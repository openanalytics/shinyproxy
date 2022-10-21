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

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import eu.openanalytics.containerproxy.backend.IContainerBackend;
import eu.openanalytics.containerproxy.model.runtime.ParameterNames;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.BackendContainerNameKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ContainerImageKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.HeartbeatTimeoutKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.InstanceIdKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.MaxLifetimeKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ParameterNamesKey;
import eu.openanalytics.containerproxy.service.hearbeat.ActiveProxiesService;
import eu.openanalytics.shinyproxy.runtimevalues.AppInstanceKey;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class AdminController extends BaseController {

	@Inject
	private ActiveProxiesService activeProxiesService;

	@RequestMapping("/admin")
	private String admin(ModelMap map, HttpServletRequest request) {
		prepareMap(map, request);

		return "admin";
	}

    @RequestMapping(value = "/admin/data", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    private Map<String, List<ProxyInfo>> adminData() {
        List<Proxy> proxies = proxyService.getProxies(null, false);
        return Collections.singletonMap("apps", proxies.stream().map(ProxyInfo::new).collect(Collectors.toList()));
    }

	public class ProxyInfo {
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
				String[] parts = proxy.getContainers().get(0).getRuntimeValue(ContainerImageKey.inst).split(":");
				imageName = parts[0];
				if (parts.length > 1) {
					imageTag = parts[1];
				} else {
					imageTag = "N/A";
				}
				endpoint = proxy.getContainers().get(0).getTargets().values().stream().map(URI::toString).findFirst().orElse("N/A"); // TODO Shiny apps have only one endpoint
			} else {
				imageName = "N/A";
				imageTag = "N/A";
				endpoint = "N/A";
			}

			Long heartbeatTimeout = proxy.getRuntimeObject(HeartbeatTimeoutKey.inst);
			if (heartbeatTimeout != -1) {
				this.heartbeatTimeout = formatSeconds(heartbeatTimeout / 1000);
			} else {
				this.heartbeatTimeout = null;
			}

			Long maxLifetime = proxy.getRuntimeObject(MaxLifetimeKey.inst);
			if (maxLifetime != -1) {
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
			spInstance = proxy.getRuntimeValue(InstanceIdKey.inst);
			backendContainerName = proxy.getContainers().get(0).getRuntimeValue(BackendContainerNameKey.inst);
		}

		private String getTimeDelta(Long timestamp) {
			long seconds = (System.currentTimeMillis() - timestamp)/1000;
			return formatSeconds(seconds);
		}

		private String formatSeconds(Long seconds) {
			return String.format("%d:%02d:%02d", seconds/3600, (seconds%3600)/60, seconds%60);
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
