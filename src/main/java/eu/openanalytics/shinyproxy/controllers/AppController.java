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
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValueKey;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.util.BadRequestException;
import eu.openanalytics.containerproxy.util.ProxyMappingManager;
import eu.openanalytics.containerproxy.util.Retrying;
import eu.openanalytics.shinyproxy.AppRequestInfo;
import eu.openanalytics.shinyproxy.ShinyProxySpecProvider;
import eu.openanalytics.shinyproxy.runtimevalues.AppInstanceKey;
import eu.openanalytics.shinyproxy.runtimevalues.MaxInstancesKey;
import eu.openanalytics.shinyproxy.runtimevalues.PublicPathKey;
import eu.openanalytics.shinyproxy.runtimevalues.WebSocketReconnectionModeKey;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class AppController extends BaseController {

	@Inject
	private ProxyMappingManager mappingManager;

	@Inject
	private ShinyProxySpecProvider shinyProxySpecProvider;

	@RequestMapping(value="/app/*/*", method=RequestMethod.GET)
	public String app(ModelMap map, HttpServletRequest request) {
		AppRequestInfo appRequestInfo = AppRequestInfo.fromRequest(request);

		prepareMap(map, request);

		Proxy proxy = findUserProxy(appRequestInfo);
		awaitReady(proxy);

		map.put("appTitle", getAppTitle(appRequestInfo));
		map.put("appName", appRequestInfo.getAppName());
		map.put("appInstance", appRequestInfo.getAppInstance());
		map.put("appInstanceDisplayName", appRequestInfo.getAppInstanceDisplayName());
		map.put("containerPath", (proxy == null) ? "" : buildContainerPath(request, appRequestInfo));
		map.put("proxyId", (proxy == null) ? "" : proxy.getId());
		map.put("webSocketReconnectionMode", (proxy == null) ? "" : proxy.getRuntimeValue(WebSocketReconnectionModeKey.inst));
		map.put("contextPath", getContextPath());
		map.put("heartbeatRate", getHeartbeatRate());
		map.put("isAppPage", true);
		map.put("maxInstances", (proxy == null) ? null: proxy.getRuntimeValue(MaxInstancesKey.inst));

		return "app";
	}
	
	@RequestMapping(value="/app/*/*", method=RequestMethod.POST)
	@ResponseBody
	public Map<String,String> startApp(HttpServletRequest request) {
		AppRequestInfo appRequestInfo = AppRequestInfo.fromRequest(request);

		Proxy proxy = getOrStart(appRequestInfo);
		String containerPath = buildContainerPath(request, appRequestInfo);
		
		Map<String,String> response = new HashMap<>();
		response.put("containerPath", containerPath);
		response.put("proxyId", proxy.getId());
		response.put("webSocketReconnectionMode", proxy.getRuntimeValue(WebSocketReconnectionModeKey.inst));
		response.put("maxInstances", proxy.getRuntimeValue(MaxInstancesKey.inst));
		return response;
	}
	
	@RequestMapping(value="/app_direct/**")
	public void appDirect(HttpServletRequest request, HttpServletResponse response) throws IOException {
		AppRequestInfo appRequestInfo = AppRequestInfo.fromRequest(request);

		Proxy proxy = findUserProxy(appRequestInfo);

		if (proxy == null && appRequestInfo.getSubPath() != null && !appRequestInfo.getSubPath().equals("/")) {
		    response.setStatus(410);
		    response.getWriter().write("{\"status\":\"error\", \"message\":\"app_stopped_or_non_existent\"}");
		    return;
		} else {
			proxy = getOrStart(appRequestInfo);
			awaitReady(proxy);
		}
		
		String mapping = getProxyEndpoint(proxy);
		
		if (appRequestInfo.getSubPath() == null) {
			try {
				response.sendRedirect(request.getRequestURI() + "/");
			} catch (Exception e) {
				throw new RuntimeException("Error redirecting proxy request", e);
			}
			return;
		}
		
		try {
			mappingManager.dispatchAsync(mapping + appRequestInfo.getSubPath(), request, response);
		} catch (Exception e) {
			throw new RuntimeException("Error routing proxy request", e);
		}
	}

	private Proxy getOrStart(AppRequestInfo appRequestInfo) {
		Proxy proxy = findUserProxy(appRequestInfo);
		if (proxy == null) {
			ProxySpec spec = proxyService.getProxySpec(appRequestInfo.getAppName());

			if (spec == null) throw new BadRequestException("Unknown proxy spec: " + appRequestInfo.getAppName());
			ProxySpec resolvedSpec = proxyService.resolveProxySpec(spec, null, null);

			List<RuntimeValue> runtimeValues = shinyProxySpecProvider.getRuntimeValues(spec);
			runtimeValues.add(new RuntimeValue(PublicPathKey.inst, getPublicPath(appRequestInfo)));
			runtimeValues.add(new RuntimeValue(AppInstanceKey.inst, appRequestInfo.getAppInstance()));

			if (!validateProxyStart(spec)) {
				throw new BadRequestException("Cannot start new proxy because the maximum amount of instances of this proxy has been reached");
			}

			proxy = proxyService.startProxy(resolvedSpec, false, runtimeValues);
		}
		return proxy;
	}


	private boolean awaitReady(Proxy proxy) {
		if (proxy == null) return false;
		if (proxy.getStatus() == ProxyStatus.Up) return true;
		if (proxy.getStatus() == ProxyStatus.Stopping || proxy.getStatus() == ProxyStatus.Stopped) return false;
		
		int totalWaitMs = Integer.parseInt(environment.getProperty("proxy.container-wait-time", "20000"));
		int waitMs = Math.min(500, totalWaitMs);
		int maxTries = totalWaitMs / waitMs;
		Retrying.retry(i -> proxy.getStatus() != ProxyStatus.Starting, maxTries, waitMs);
		
		return (proxy.getStatus() == ProxyStatus.Up);
	}
	
	private String buildContainerPath(HttpServletRequest request, AppRequestInfo appRequestInfo) {
		String queryString = ServletUriComponentsBuilder.fromRequest(request).replaceQueryParam("sp_hide_navbar").build().getQuery();

		queryString = (queryString == null) ? "" : "?" + queryString;
		
		return getPublicPath(appRequestInfo) + queryString;
	}

	private String getPublicPath(AppRequestInfo appRequestInfo) {
		return getContextPath() + "app_direct/" + appRequestInfo.getAppName() + "/" + appRequestInfo.getAppInstance() + '/';
	}

	/**
	 * Validates whether a proxy should be allowed to start.
	 */
	private boolean validateProxyStart(ProxySpec spec) {
		Integer maxInstances = shinyProxySpecProvider.getMaxInstancesForSpec(spec);

		// note: there is a very small change that the user is able to start more instances than allowed, if the user
		// starts many proxies at once. E.g. in the following scenario:
		// - max proxies = 2
		// - user starts a proxy
		// - user sends a start proxy request -> this function is called and returns true
		// - just before this new proxy is added to the list of active proxies, the user sends a new start proxy request
		// - again this new proxy is allowed, because there is still only one proxy in the list of active proxies
		// -> the user has three proxies running.
		// Because of chance that this happens is small and that the consequences are low, we accept this risk.
		int currentAmountOfInstances = proxyService.getProxies(
				p -> p.getSpec().getId().equals(spec.getId())
						&& userService.isOwner(p),
				false).size();


		return currentAmountOfInstances < maxInstances;
	}

}
