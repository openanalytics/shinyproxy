/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2018 Open Analytics
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

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.view.RedirectView;

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.util.Retrying;

@Controller
public class AppController extends BaseController {

	@RequestMapping(value="/app/*", method=RequestMethod.GET)
	public String app(ModelMap map, HttpServletRequest request) {
		prepareMap(map, request);
		
		Proxy proxy = findUserProxy(request);
		if (proxy != null && proxy.getStatus() == ProxyStatus.Starting) {
			// If a request comes in for a proxy that is currently starting up,
			// block the request until the proxy is ready (or errored).
			Retrying.retry(i -> proxy.getStatus() != ProxyStatus.Starting, 40, 500);
		}
		String mapping = getProxyEndpoint(proxy);

		map.put("appTitle", getAppTitle(request));
		map.put("container", buildContainerPath(mapping, request));
		
		ProxySpec[] apps = proxyService.getProxySpecs(null, false).toArray(new ProxySpec[0]);
		map.put("apps", apps);

		Set<String> displayGroups = new LinkedHashSet<>();
		for (ProxySpec app: apps) {
			displayGroups.add(app.getDisplayGroup());
		}
		map.put("displayGroups", displayGroups.toArray(new String[0]));
				
		return "app";
	}
	
	@RequestMapping(value="/app_direct/*", method=RequestMethod.GET)
	public Object appDirect(ModelMap map, HttpServletRequest request) {
		Proxy proxy = getOrStart(request);
		String mapping = getProxyEndpoint(proxy);
		String containerPath = buildContainerPath(mapping, request);
		return new RedirectView(containerPath);		
	}
	
	@RequestMapping(value="/app/*", method=RequestMethod.POST)
	@ResponseBody
	public Map<String,String> startApp(HttpServletRequest request) {
		Proxy proxy = getOrStart(request);
		String mapping = getProxyEndpoint(proxy);
		String containerPath = buildContainerPath(mapping, request);
		
		Map<String,String> response = new HashMap<>();
		response.put("containerPath", containerPath);
		response.put("proxyId", proxy.getId());
		return response;
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
	
	private String buildContainerPath(String mapping, HttpServletRequest request) {
		if (mapping == null) return "";
		
		String queryString = request.getQueryString();
		queryString = (queryString == null) ? "" : "?" + queryString;
		
		String containerPath = getContextPath() + mapping + environment.getProperty("proxy.landing-page", "/") + queryString;
		return containerPath;
	}
}
