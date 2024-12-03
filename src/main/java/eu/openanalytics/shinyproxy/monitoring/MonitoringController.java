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

import eu.openanalytics.containerproxy.service.UserService;
import eu.openanalytics.containerproxy.util.ProxyMappingManager;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.util.HttpString;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;

import static eu.openanalytics.containerproxy.util.ProxyMappingManager.ATTACHMENT_KEY_DISPATCHER;

@Controller
public class MonitoringController {

    private final ProxyMappingManager proxyMappingManager;
    private final UserService userService;
    private final MonitoringService monitoringService;
    private final UrlPathHelper urlPathHelper = new UrlPathHelper();

    public MonitoringController(ProxyMappingManager proxyMappingManager, UserService userService, MonitoringService monitoringService) {
        this.proxyMappingManager = proxyMappingManager;
        this.userService = userService;
        this.monitoringService = monitoringService;
    }

    @RequestMapping(value = {"/grafana/**"})
    public void grafana(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!monitoringService.isEnabled()) {
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.FORBIDDEN.value());
            request.getRequestDispatcher("/error").forward(request, response);
            return;
        }
        String uri = urlPathHelper.getPathWithinApplication(request);
        if (uri.equals("/grafana")) {
            response.sendRedirect("/grafana/");
            return;
        }
        if (!uri.startsWith("/grafana/")) {
            throw new IllegalStateException("TODO");
        }

        String target = uri.replace("/grafana/", "/grafana_internal/");
        HttpServerExchange exchange = ServletRequestContext.current().getExchange();
        exchange.putAttachment(ATTACHMENT_KEY_DISPATCHER, proxyMappingManager);
        exchange.getRequestHeaders().put(new HttpString("X-SP-UserId"), userService.getCurrentUserId());

        request.startAsync();
        request.getRequestDispatcher(target).forward(request, response);
    }

}
