/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2019 Open Analytics
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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.util.ProxyMappingManager;
import eu.openanalytics.containerproxy.util.Retrying;

@Controller
public class AppController extends BaseController {

	@Inject
	private ProxyMappingManager mappingManager;
	
	@RequestMapping(value="/app/*", method=RequestMethod.GET)
	public String app(ModelMap map, HttpServletRequest request) {
		prepareMap(map, request);
		
		Proxy proxy = findUserProxy(request);
		if (proxy != null && proxy.getStatus() == ProxyStatus.Starting) {
			// If a request comes in for a proxy that is currently starting up,
			// block the request until the proxy is ready (or errored).
			Retrying.retry(i -> proxy.getStatus() != ProxyStatus.Starting, 40, 500);
		}

		map.put("appTitle", getAppTitle(request));
		map.put("container", (proxy == null) ? "" : buildContainerPath(request));
		
		return "app";
	}
	
	@RequestMapping(value="/app/*", method=RequestMethod.POST)
	@ResponseBody
	public Map<String,String> startApp(HttpServletRequest request) {
		Proxy proxy = getOrStart(request);
		String containerPath = buildContainerPath(request);
		
		Map<String,String> response = new HashMap<>();
		response.put("containerPath", containerPath);
		response.put("proxyId", proxy.getId());
		return response;
	}
	
	@RequestMapping(value="/app_direct/**")
	public void appDirect(HttpServletRequest request, HttpServletResponse response) {
		Proxy proxy = getOrStart(request);
		String mapping = getProxyEndpoint(proxy);
		
		String subPath = request.getRequestURI();
		subPath = subPath.substring(subPath.indexOf("/app_direct/") + 12);
		subPath = subPath.substring(getAppName(request).length());
		
		if (subPath.trim().isEmpty()) {
			try {
				response.sendRedirect(request.getRequestURI() + "/");
			} catch (Exception e) {
				throw new RuntimeException("Error redirecting proxy request", e);
			}
			return;
		}
		
		try {
			mappingManager.dispatchAsync(mapping + subPath, request, response);
		} catch (Exception e) {
			throw new RuntimeException("Error routing proxy request", e);
		}
	}
	
	private Proxy getOrStart(HttpServletRequest request) {
		Proxy proxy = findUserProxy(request);
		if (proxy == null) {
			String specId = getAppName(request);
			ProxySpec spec = proxyService.getProxySpec(specId);
			if (spec == null) throw new IllegalArgumentException("Unknown proxy spec: " + specId);
			ProxySpec resolvedSpec = proxyService.resolveProxySpec(spec, null, null);
			proxy = proxyService.startProxy(resolvedSpec, false);
		}
		return proxy;
	}
	
	private String buildContainerPath(HttpServletRequest request) {
		String appName = getAppName(request);
		if (appName == null) return "";
		
		String queryString = request.getQueryString();
		queryString = (queryString == null) ? "" : "?" + queryString;
		
		String containerPath = getContextPath() + "app_direct/" + appName + "/" + queryString;
		return containerPath;
	}
}
