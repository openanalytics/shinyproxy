/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2021 Open Analytics
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
package eu.openanalytics.shinyproxy.controllers;

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.service.HeartbeatService;
import eu.openanalytics.containerproxy.service.ProxyService;
import eu.openanalytics.containerproxy.service.UserService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.inject.Inject;
import java.util.HashMap;

@Controller
public class HeartbeatController {

    @Inject
    private HeartbeatService heartbeatService;

    @Inject
    private ProxyService proxyService;

    @Inject
    private UserService userService;

    /**
     * Endpoint used to force a heartbeat. This is used when an app cannot piggy-back heartbeats on other requests
     * or on a WebSocket connection.
     * @return
     */
    @RequestMapping(value = "/heartbeat/{proxyId}", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<HashMap<String, String>> heartbeat(@PathVariable("proxyId") String proxyId) {
        Proxy proxy = proxyService.getProxy(proxyId);

        if (proxy == null) {
            return ResponseEntity.status(410).body(new HashMap<String, String>() {{
                put("status", "error");
                put("message", "app_stopped_or_non_existent");
            }});
        }

        if (!userService.isOwner(proxy)) {
            throw new AccessDeniedException(String.format("Cannot register heartbeat for proxy %s: access denied", proxyId));
        }

        heartbeatService.heartbeatReceived(proxy);

        return ResponseEntity.ok(new HashMap<String, String>() {{
            put("status", "success");
        }});
    }
}
