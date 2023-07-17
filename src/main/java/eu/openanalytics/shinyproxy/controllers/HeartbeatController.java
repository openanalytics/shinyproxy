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
package eu.openanalytics.shinyproxy.controllers;

import eu.openanalytics.containerproxy.api.dto.ApiResponse;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.service.ProxyService;
import eu.openanalytics.containerproxy.service.UserService;
import eu.openanalytics.containerproxy.service.hearbeat.ActiveProxiesService;
import eu.openanalytics.containerproxy.service.hearbeat.HeartbeatService;
import eu.openanalytics.shinyproxy.controllers.dto.ShinyProxyApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.inject.Inject;

@Controller
public class HeartbeatController {

    @Inject
    private HeartbeatService heartbeatService;

    @Inject
    private ProxyService proxyService;

    @Inject
    private UserService userService;

    @Inject
    private ActiveProxiesService activeProxiesService;

    /**
     * Endpoint used to force a heartbeat. This is used when an app cannot piggy-back heartbeats on other requests
     * or on a WebSocket connection.
     */
    @Operation(summary = "Force an heartbeat for an app.", tags = "ShinyProxy")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Heartbeat sent.",
                    content = {
                            @Content(
                                    mediaType = "application/json",
                                    examples = {
                                            @ExampleObject(value = "{\"status\":\"success\", \"data\": null}")
                                    }
                            )
                    }),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "User is not authenticated.",
                    content = {
                            @Content(
                                    mediaType = "application/json",
                                    examples = {
                                            @ExampleObject(value = "{\"message\":\"shinyproxy_authentication_required\",\"status\":\"fail\"}")
                                    }
                            )
                    }),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "410",
                    description = "App has been stopped or the app never existed or the user has no access to the app.",
                    content = {
                            @Content(
                                    mediaType = "application/json",
                                    examples = {
                                            @ExampleObject(value = "{\"message\":\"app_stopped_or_non_existent\",\"status\":\"fail\"}")
                                    }
                            )
                    }),
    })
    @RequestMapping(value = "/heartbeat/{proxyId}", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<ApiResponse<Object>> heartbeat(@PathVariable("proxyId") String proxyId) {
        Proxy proxy = proxyService.getProxy(proxyId);

        if (proxy == null || proxy.getStatus().isUnavailable() || !userService.isOwner(proxy)) {
            return ShinyProxyApiResponse.appStoppedOrNonExistent();
        }

        heartbeatService.heartbeatReceived(HeartbeatService.HeartbeatSource.FALLBACK, proxy.getId(), null);

        return ApiResponse.success();
    }


    /**
     * Provides info to about the heartbeat, max lifetime etc. of this app.
     */
    @Operation(summary = "Get heartbeat information for an app.", tags = "ShinyProxy")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Heartbeat info returned.",
                    content = {
                            @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = HeartBeatInfoDto.class),
                                    examples = {
                                            @ExampleObject(value = "{\"status\":\"success\"}")
                                    }
                            )
                    }),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "User is not authenticated.",
                    content = {
                            @Content(
                                    mediaType = "application/json",
                                    examples = {
                                            @ExampleObject(value = "{\"message\":\"shinyproxy_authentication_required\",\"status\":\"fail\"}")
                                    }
                            )
                    }),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "410",
                    description = "App has been stopped or the app never existed or the user has no access to the app.",
                    content = {
                            @Content(
                                    mediaType = "application/json",
                                    examples = {
                                            @ExampleObject(value = "{\"message\":\"app_stopped_or_non_existent\",\"status\":\"fail\"}")
                                    }
                            )
                    }),
    })
    @RequestMapping(value = "/heartbeat/{proxyId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<ApiResponse<HeartBeatInfoDto>> getHeartbeatInfo(@PathVariable("proxyId") String proxyId) {
        Proxy proxy = proxyService.getProxy(proxyId);

        if (proxy == null || proxy.getStatus().isUnavailable() || !userService.isOwner(proxy)) {
            return ShinyProxyApiResponse.appStoppedOrNonExistent();
        }

        Long lastHeartbeat = activeProxiesService.getLastHeartBeat(proxy.getId());

        HeartBeatInfoDto resp = new HeartBeatInfoDto(lastHeartbeat, heartbeatService.getHeartbeatRate());

        return ApiResponse.success(resp);
    }


    private static class HeartBeatInfoDto {

        private final Long lastHeartbeat;
        private final Long heartbeatRate;

        private HeartBeatInfoDto(Long lastHeartbeat, Long heartbeatRate) {
            this.lastHeartbeat = lastHeartbeat;
            this.heartbeatRate = heartbeatRate;
        }

        public Long getHeartbeatRate() {
            return heartbeatRate;
        }

        public Long getLastHeartbeat() {
            return lastHeartbeat;
        }
    }

}
