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
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import eu.openanalytics.containerproxy.service.hearbeat.ActiveProxiesService;
import eu.openanalytics.containerproxy.service.hearbeat.HeartbeatService;
import eu.openanalytics.shinyproxy.runtimevalues.AppInstanceKey;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

import eu.openanalytics.containerproxy.model.runtime.Proxy;

@Controller
public class AdminController extends BaseController {

	@Inject
	private ActiveProxiesService activeProxiesService;

	@RequestMapping("/admin")
	private String admin(ModelMap map, HttpServletRequest request) {
		prepareMap(map, request);
		
		List<Proxy> proxies = proxyService.getProxies(null, false);
		map.put("proxies", proxies.stream().map(ProxyInfo::new).collect(Collectors.toList()));

		return "admin";
	}

	public class ProxyInfo {
	    public final String status;
		public final String id;
		public final String userId;
		public final String appName;
		public final String appInstanceName;
		public final String endpoint;
		public final String uptime;
		public final String lastHeartBeat;
		public final String imageName;
		public final String imageTag;

		public ProxyInfo(Proxy proxy) {
			status = proxy.getStatus().toString();
			id = proxy.getId();
			userId = proxy.getUserId();
			appName = proxy.getSpec().getId();
			appInstanceName = getInstanceName(proxy);
			endpoint = proxy.getTargets().values().stream().map(URI::toString).findFirst().orElse("N/A"); // Shiny apps have only one endpoint

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

			String[] parts = proxy.getSpec().getContainerSpecs().get(0).getImage().split(":");
			imageName = parts[0];
			if (parts.length > 1) {
				imageTag = parts[1];
			} else {
				imageTag = "latest";
			}
		}

		private String getTimeDelta(Long timestamp) {
			long uptimeSec = (System.currentTimeMillis() - timestamp)/1000;
			return String.format("%d:%02d:%02d", uptimeSec/3600, (uptimeSec%3600)/60, uptimeSec%60);
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
