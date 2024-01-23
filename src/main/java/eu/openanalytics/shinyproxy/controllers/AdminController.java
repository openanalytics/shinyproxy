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
import eu.openanalytics.containerproxy.model.runtime.Container;
import eu.openanalytics.containerproxy.model.runtime.ParameterNames;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.BackendContainerNameKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ContainerImageKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.HeartbeatTimeoutKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.InstanceIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.MaxLifetimeKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ParameterNamesKey;
import eu.openanalytics.containerproxy.service.hearbeat.ActiveProxiesService;
import eu.openanalytics.shinyproxy.runtimevalues.AppInstanceKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.inject.Inject;
import java.util.List;

@Controller
public class AdminController extends BaseController {

    @Inject
    private ActiveProxiesService activeProxiesService;

    @RequestMapping("/admin")
    private String admin(ModelMap map, HttpServletRequest request) {
        prepareMap(map, request);

        return "admin";
    }

    @Operation(summary = "Get active proxies of all users.", tags = "ShinyProxy")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Active proxies are returned.",
            content = {
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ProxyInfoResponse.class),
                    examples = {
                        @ExampleObject(value = "{\"status\": \"success\", \"data\": [{\"status\": \"Up\", \"proxyId\": \"9cd90bbb-ae9c-4016-9b9c-d2852b3a0bf6\", \"userId\": \"jack\", \"appName\": \"01_hello\", " +
                            "\"instanceName\": \"Default\", \"endpoint\": \"N/A\", \"uptime\": \"0:00:39\", \"lastHeartBeat\": \"0:00:05\", \"imageName\": \"openanalytics/shinyproxy-demo\", \"imageTag\": \"N/A\", " +
                            "\"heartbeatTimeout\": null, \"maxLifetime\": \"0:02:00\", \"spInstance\": \"9bec0d32754eab6a036bf1ee032bca82f98df0c5\", \"backendContainerName\": " +
                            "\"900b4f35b283401946db1d7cb8fe31ad5e6209d921b3cb9fd668ed6b9cbf7aa5\", \"parameters\": null}, {\"status\": \"Up\", \"proxyId\": \"b34d416e-ce6e-4351-a126-8836c88f2200\", \"userId\": " +
                            "\"jack\", \"appName\": \"06_tabsets\", \"instanceName\": \"Default\", \"endpoint\": \"N/A\", \"uptime\": \"0:00:18\", \"lastHeartBeat\": \"0:00:02\", \"imageName\": " +
                            "\"openanalytics/shinyproxy-demo\", \"imageTag\": \"N/A\", \"heartbeatTimeout\": null, \"maxLifetime\": \"0:02:00\", \"spInstance\": \"9bec0d32754eab6a036bf1ee032bca82f98df0c5\", " +
                            "\"backendContainerName\": \"2158b5b49c4138a9d0d6313fc4b62eba074b359473143be1d98102ab06c74bf8\", \"parameters\": null}]}")
                    }
                )
            }),
    })
    @RequestMapping(value = "/admin/data", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    @ResponseBody
    private ResponseEntity<ApiResponse<List<ProxyInfo>>> adminData() {
        List<Proxy> proxies = proxyService.getAllProxies();
        List<ProxyInfo> proxyInfos = proxies.stream().map(ProxyInfo::new).toList();
        return ApiResponse.success(proxyInfos);
    }

    public static class ProxyInfoResponse {
        public String status = "success";
        public List<ProxyInfo> data;
    }

    public class ProxyInfo {

        @Schema(allowableValues = {"New", "Up", "Stopping", "Pausing", "Paused", "Resuming", "Stopped"})
        public final String status;

        public final String proxyId;
        public final String userId;
        public final String appName;
        public final String instanceName;
        public final String endpoint;
        public final String uptime;
        public final String lastHeartBeat;
        public final String imageName;
        public final String imageTag;
        public final String heartbeatTimeout;
        public final String maxLifetime;
        public final String spInstance;
        public final String backendContainerName;
        public final List<ParameterNames.ParameterName> parameters;

        public ProxyInfo(Proxy proxy) {
            status = proxy.getStatus().toString();
            proxyId = proxy.getId();
            userId = proxy.getUserId();
            appName = proxy.getSpecId();
            instanceName = getInstanceName(proxy);

            if (proxy.getStartupTimestamp() > 0) {
                uptime = getTimeDelta(proxy.getStartupTimestamp());
            } else {
                uptime = "N/A";
            }

            Long heartBeat = activeProxiesService.getLastHeartBeat(proxy.getId());
            if (heartBeat == null) {
                lastHeartBeat = "N/A";
            } else {
                lastHeartBeat = getTimeDelta(heartBeat);
            }

            if (!proxy.getContainers().isEmpty()) {
                Container container = proxy.getContainers().get(0);
                String[] parts = container.getRuntimeValue(ContainerImageKey.inst).split(":");
                imageName = parts[0];
                if (parts.length > 1) {
                    imageTag = parts[1];
                } else {
                    imageTag = "N/A";
                }
                backendContainerName = container.getRuntimeObjectOrDefault(BackendContainerNameKey.inst, "N/A");
            } else {
                imageName = "N/A";
                imageTag = "N/A";
                backendContainerName = "N/A";
            }

            if (proxy.getTargets().containsKey(proxy.getId())) {
                endpoint = proxy.getTargets().get("").toString();
            } else {
                endpoint = "N/A";
            }

            Long heartbeatTimeout = proxy.getRuntimeObjectOrNull(HeartbeatTimeoutKey.inst);
            if (heartbeatTimeout != null && heartbeatTimeout != -1) {
                this.heartbeatTimeout = formatSeconds(heartbeatTimeout / 1000);
            } else {
                this.heartbeatTimeout = null;
            }

            Long maxLifetime = proxy.getRuntimeObjectOrNull(MaxLifetimeKey.inst);
            if (maxLifetime != null && maxLifetime != -1) {
                this.maxLifetime = formatSeconds(maxLifetime * 60);
            } else {
                this.maxLifetime = null;
            }

            ParameterNames providedParameters = proxy.getRuntimeObjectOrNull(ParameterNamesKey.inst);
            if (providedParameters != null) {
                parameters = providedParameters.getParametersNames();
            } else {
                parameters = null;
            }
            spInstance = proxy.getRuntimeObjectOrDefault(InstanceIdKey.inst, "N/A");
        }

        private String getTimeDelta(Long timestamp) {
            long seconds = (System.currentTimeMillis() - timestamp) / 1000;
            return formatSeconds(seconds);
        }

        private String formatSeconds(Long seconds) {
            return String.format("%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
        }

        private String getInstanceName(Proxy proxy) {
            String appInstanceName = proxy.getRuntimeValue(AppInstanceKey.inst);
            if (appInstanceName.equals("_")) {
                return "Default";
            }
            return appInstanceName;
        }

    }

}
