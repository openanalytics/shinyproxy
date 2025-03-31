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
package eu.openanalytics.shinyproxy;

import eu.openanalytics.containerproxy.backend.dispatcher.ProxyDispatcherService;
import eu.openanalytics.containerproxy.backend.strategy.IProxyTestStrategy;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.service.StructuredLogger;
import eu.openanalytics.containerproxy.util.Retrying;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.Objects;

/**
 * This component tests the responsiveness of Shiny containers by making an HTTP GET request to the container's published port (default 3838).
 * If this request does not receive a 200 (OK) response within a configured time limit, the container is considered to be unresponsive.
 */
@Component
@Primary
public class ShinyProxyTestStrategy implements IProxyTestStrategy {

    private final StructuredLogger slog = StructuredLogger.create(getClass());

    @Inject
    private Environment environment;

    @Inject
    private ProxyDispatcherService proxyDispatcherService;

    private int totalWaitMs;
    private int requestTimeout;

    @PostConstruct
    public void init() {
        totalWaitMs = Integer.parseInt(environment.getProperty("proxy.container-wait-time", "20000"));
        requestTimeout = Integer.parseInt(environment.getProperty("proxy.container-wait-timeout", "5000"));
    }

    @Override
    public boolean testProxy(Proxy proxy) {
        if (!Objects.equals(proxy.getTargetId(), proxy.getId())) {
            // Proxy points to a different target, should not test it
            return true;
        }

        if (proxy.getTargets().isEmpty()) return false;
        URI targetURI = proxy.getTargets().get("");

        return Retrying.retry((currentAttempt, maxAttempts) -> {
            if (proxy.getStatus().isUnavailable()) {
                // proxy got stopped while loading -> no need to try to connect it since the container will already be deleted
                return new Retrying.Result(false, false);
            }
            if (!proxyDispatcherService.getDispatcher(proxy.getSpecId()).isProxyHealthy(proxy)) {
                return new Retrying.Result(false, false);
            }
            URL testURL = new URL(targetURI.toString() + "/");
            HttpURLConnection connection = ((HttpURLConnection) testURL.openConnection());
            if (currentAttempt <= 5) {
                // When the container has only just started (or when the k8s service has only just been created),
                // it could be that our traffic ends in a black hole, and we need to wait the full 5s seconds of
                // the timeout. Therefore, we first try a few attempts with a lower timeout. If the container is
                // fast, this will result in a faster startup. If the container is slow to startup, not time is wasted.
                connection.setConnectTimeout(200);
                connection.setReadTimeout(200);
            } else {
                connection.setConnectTimeout(requestTimeout);
                connection.setReadTimeout(requestTimeout);
            }
            connection.setInstanceFollowRedirects(false);
            int responseCode = connection.getResponseCode();
            if (Arrays.asList(200, 301, 302, 303, 307, 308).contains(responseCode)) {
                return Retrying.SUCCESS;
            }
            return Retrying.FAILURE;
        }, totalWaitMs, "Checking application reachable at " + targetURI, 10, proxy, slog);
    }

}
