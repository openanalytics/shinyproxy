/*
 * ShinyProxy
 *
 * Copyright (C) 2016-2025 Open Analytics
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
package eu.openanalytics.shinyproxy.test.helpers;

import eu.openanalytics.containerproxy.test.helpers.BasicAuthInterceptor;
import eu.openanalytics.containerproxy.test.helpers.ShinyProxyInstance;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.junit.jupiter.api.Assertions;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Set;

public class ApiTestHelper {

    private final ShinyProxyInstance inst;
    private final String baseUrl;
    private final OkHttpClient clientDemo;
    private final OkHttpClient clientDemo2;
    private final OkHttpClient clientWithoutAuth;

    private static final List<String> allowedProxyKeys = List.of(
        "id", "status", "startupTimestamp", "createdTimestamp", "userId",
        "specId", "displayName", "containers", "targetId", "runtimeValues");

    private static final List<String> allowedProxyRuntimeValues = List.of(
        "SHINYPROXY_DISPLAY_NAME", "SHINYPROXY_MAX_LIFETIME", "SHINYPROXY_CREATED_TIMESTAMP",
        "SHINYPROXY_INSTANCE", "SHINYPROXY_PUBLIC_PATH", "SHINYPROXY_HEARTBEAT_TIMEOUT",
        "SHINYPROXY_FORCE_FULL_RELOAD", "SHINYPROXY_TRACK_APP_URL", "SHINYPROXY_WEBSOCKET_RECONNECTION_MODE",
        "SHINYPROXY_APP_INSTANCE"
    );

    private static final List<String> allowedContainerKeys = List.of(
        "index", "id", "runtimeValues"
    );

    private static final List<String> allowedContainerRuntimeValues = List.of(
        "SHINYPROXY_CONTAINER_INDEX"
    );

    public ApiTestHelper(ShinyProxyInstance inst) {
        this.inst = inst;
        this.baseUrl = inst.client.getBaseUrl();
        clientDemo = new OkHttpClient.Builder()
            .followRedirects(false)
            .addInterceptor(new BasicAuthInterceptor("demo", "demo"))
            .callTimeout(Duration.ofSeconds(120))
            .readTimeout(Duration.ofSeconds(120))
            .build();
        clientDemo2 = new OkHttpClient.Builder()
            .followRedirects(false)
            .addInterceptor(new BasicAuthInterceptor("demo2", "demo2"))
            .callTimeout(Duration.ofSeconds(120))
            .readTimeout(Duration.ofSeconds(120))
            .build();
        clientWithoutAuth = new OkHttpClient.Builder()
            .followRedirects(false)
            .callTimeout(Duration.ofSeconds(120))
            .readTimeout(Duration.ofSeconds(120))
            .build();
    }

    public Request.Builder createRequest(String path) {
        return new Request.Builder()
            .url(baseUrl + path);
    }

    public Request.Builder createPostRequest(String path) {
        return new Request.Builder()
            .post(RequestBody.create("", null))
            .url(baseUrl + path);
    }

    public Request.Builder createPostRequest(String path, String body) {
        return new Request.Builder()
            .post(RequestBody.create(body.getBytes(), MediaType.parse("application/json")))
            .url(baseUrl + path);
    }

    public Request.Builder createPutRequest(String path) {
        return new Request.Builder()
            .put(RequestBody.create("", null))
            .url(baseUrl + path);
    }

    public Request.Builder createPutRequest(String path, String body) {
        return new Request.Builder()
            .put(RequestBody.create(body.getBytes(), MediaType.parse("application/json")))
            .url(baseUrl + path);
    }

    public Request.Builder createDeleteRequest(String path) {
        return new Request.Builder()
            .delete()
            .url(baseUrl + path);
    }

    public Response callWithAuth(Request.Builder request) {
        try {
            return new Response(clientDemo.newCall(request.build()).execute());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Response callWithAuthDemo2(Request.Builder request) {
        try {
            return new Response(clientDemo2.newCall(request.build()).execute());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Response callWithoutAuth(Request.Builder request) {
        try {
            return new Response(clientWithoutAuth.newCall(request.build()).execute());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String validateProxyObject(JsonObject proxy) {
        for (String key : proxy.keySet()) {
            Assertions.assertTrue(allowedProxyKeys.contains(key), "Proxy object contains disallowed key: " + key);
        }

        Assertions.assertNotNull(proxy.getJsonString("status").getString());

        // runtime values
        for (String key : proxy.getJsonObject("runtimeValues").keySet()) {
            Assertions.assertTrue(allowedProxyRuntimeValues.contains(key), "Proxy RuntimeValues contains disallowed key: " + key);
        }

        // container
        JsonArray containers = proxy.getJsonArray("containers");
        for (JsonValue container : containers) {
            for (String key : container.asJsonObject().keySet()) {
                Assertions.assertTrue(allowedContainerKeys.contains(key), "Container object contains disallowed key: " + key);
            }
            // container runtime values
            for (String key : container.asJsonObject().getJsonObject("runtimeValues").keySet()) {
                Assertions.assertTrue(allowedContainerRuntimeValues.contains(key), "Container RuntimeValues contains disallowed key: " + key);
            }
        }

        return proxy.getString("id");
    }

    public void validateStoppedProxyObject(JsonObject proxy) {
        Assertions.assertEquals(List.of("id", "status", "startupTimestamp", "createdTimestamp", "userId", "specId", "displayName",
            "containers", "targetId", "runtimeValues"), proxy.keySet().stream().toList());

        Assertions.assertEquals("Stopped", proxy.getJsonString("status").getString());

        // runtime values
        Assertions.assertEquals(Set.of(), proxy.getJsonObject("runtimeValues").keySet());

        // container
        JsonArray containers = proxy.getJsonArray("containers");
        Assertions.assertEquals(0, containers.size());
    }

}
