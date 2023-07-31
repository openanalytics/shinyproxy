/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2023 Open Analytics
 *
 * ===========================================================================
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
package eu.openanalytics.shinyproxy;

import eu.openanalytics.containerproxy.util.ContextPathHelper;
import io.undertow.UndertowMessages;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.protocol.http.ServerFixedLengthStreamSinkConduit;
import io.undertow.util.Headers;
import org.springframework.http.HttpStatus;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.conduits.AbstractStreamSinkConduit;
import org.xnio.conduits.ConduitWritableByteChannel;
import org.xnio.conduits.Conduits;
import org.xnio.conduits.StreamSinkConduit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

/**
 * The goal of this class is to inject a `<script>` tag into the HTML pages of the app.
 * In order to do this, we have to store the complete response in memory, inject the script and update the content-length.
 * This is only applied to HTML requests.
 *
 * See https://lists.jboss.org/pipermail/undertow-dev/2019-February/002312.html
 * See https://github.com/SoftInstigate/restheart-security/blob/c88820799f89c63f56e560155861df246f908490/src/main/java/org/restheart/security/handlers/ModifiableContentSinkConduit.java
 */
public class ShinyProxyIframeScriptInjector extends AbstractStreamSinkConduit<StreamSinkConduit> {

    private final ByteArrayOutputStream outputStream;
    private final String contextPath;
    private final HttpServerExchange exchange;

    /**
     * Construct a new instance.
     *
     * @param next     the delegate conduit to set
     * @param exchange the exchange
     */
    public ShinyProxyIframeScriptInjector(String contextPath, StreamSinkConduit next, HttpServerExchange exchange) {
        super(next);
        this.contextPath = contextPath;
        this.exchange = exchange;
        long length = exchange.getResponseContentLength();
        if (length <= 0L) {
            outputStream = new ByteArrayOutputStream();
        } else {
            if (length > Integer.MAX_VALUE) {
                throw UndertowMessages.MESSAGES.responseTooLargeToBuffer(length);
            }
            outputStream = new ByteArrayOutputStream((int) length);
        }
    }

    @Override
    public long transferFrom(StreamSourceChannel source, long count, ByteBuffer throughBuffer) throws IOException {
        return IoUtils.transfer(source, count, throughBuffer, new ConduitWritableByteChannel(this));
    }

    @Override
    public long transferFrom(FileChannel src, long position, long count) throws IOException {
        return src.transferTo(position, count, new ConduitWritableByteChannel(this));
    }

    @Override
    public int write(java.nio.ByteBuffer src) throws IOException {
        byte[] bytes = new byte[src.remaining()];
        src.get(bytes, 0, bytes.length);
        outputStream.write(bytes);
        return bytes.length;
    }

    @Override
    public long write(java.nio.ByteBuffer[] srcs, int offs, int len) throws IOException {
        for (int i = offs; i < len; ++i) {
            if (srcs[i].hasRemaining()) {
                return write(srcs[i]);
            }
        }
        return 0;
    }

    @Override
    public int writeFinal(java.nio.ByteBuffer src) throws IOException {
        return Conduits.writeFinalBasic(this, src);
    }

    @Override
    public long writeFinal(java.nio.ByteBuffer[] srcs, int offs, int len) throws IOException {
        return Conduits.writeFinalBasic(this, srcs, offs, len);
    }

    @Override
    public void terminateWrites() throws IOException {
        ByteBuffer out;
        // 1. check whether it's a html response and success
        if (exchange.getStatusCode() == HttpStatus.OK.value()
                && exchange.getResponseHeaders().get("Content-Type") != null
                && exchange.getResponseHeaders().get("Content-Type").stream().anyMatch(headerValue -> headerValue.contains("text/html"))) {
            // 2. inject script
            String r = outputStream.toString(StandardCharsets.UTF_8);
            r += "<script src='" + contextPath + "js/shiny.iframe.js'></script>";
            out = ByteBuffer.wrap(r.getBytes(StandardCharsets.UTF_8));
        } else {
            // 2. read bytes
            out = ByteBuffer.wrap(outputStream.toByteArray());
        }
        // 3. set Content-Length header
        updateContentLength(exchange, out);
        // 4. write new response (to the next stream)
        do {
            next.write(out);
        } while (out.hasRemaining());

        // 5. call parent method
        super.terminateWrites();
    }

    private void updateContentLength(HttpServerExchange exchange, ByteBuffer output) {
        long length = output.limit();

        // check works case-insensitive
        if (!exchange.getResponseHeaders().contains("Transfer-Encoding")) {
            exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, length);
        }

        // also update length of ServerFixedLengthStreamSinkConduit
        if (next instanceof ServerFixedLengthStreamSinkConduit) {
            Method m;

            try {
                m = ServerFixedLengthStreamSinkConduit.class.getDeclaredMethod(
                        "reset",
                        long.class,
                        HttpServerExchange.class);
                m.setAccessible(true);
            } catch (NoSuchMethodException | SecurityException ex) {
                throw new RuntimeException("could not find ServerFixedLengthStreamSinkConduit.reset method", ex);
            }

            try {
                m.invoke(next, length, exchange);
            } catch (Throwable ex) {
                throw new RuntimeException("could not access BUFFERED_REQUEST_DATA field", ex);
            }
        }
    }

}
