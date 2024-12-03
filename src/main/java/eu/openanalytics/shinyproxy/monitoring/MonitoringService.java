/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2024 Open Analytics
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
package eu.openanalytics.shinyproxy.monitoring;

import eu.openanalytics.containerproxy.util.ProxyMappingManager;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.net.URI;

@Service
public class MonitoringService {

    private final ProxyMappingManager proxyMappingManager;

    private final URI grafanaUrl;

    @SneakyThrows
    public MonitoringService(ProxyMappingManager proxyMappingManager, Environment environment) {
        this.proxyMappingManager = proxyMappingManager;
        String url = environment.getProperty("proxy.monitoring.grafana-url");
        if (url != null) {
            grafanaUrl = new URI(StringUtils.removeEnd(url, "/"));
        } else {
            grafanaUrl = null;
        }
    }

    public boolean isEnabled() {
        return grafanaUrl != null;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        if (!isEnabled()) {
            return;
        }
        LoadBalancingProxyClient proxyClient = new LoadBalancingProxyClient();
        proxyClient.setMaxQueueSize(100);
        proxyClient.addHost(grafanaUrl);
        proxyMappingManager.getHttpHandler().addPrefixPath("/grafana_internal",  new ProxyHandler(proxyClient, ResponseCodeHandler.HANDLE_404));
    }

}
