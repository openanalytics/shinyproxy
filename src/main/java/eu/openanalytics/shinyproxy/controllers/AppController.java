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

import javax.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;

@Controller
public class AppController extends BaseController {

	@RequestMapping(value="/app/*", method=RequestMethod.GET)
	String app(ModelMap map, HttpServletRequest request) {
		prepareMap(map, request);
		
		Proxy proxy = findUserProxy(request);
		String mapping = getProxyEndpoint(proxy);

		map.put("appTitle", getAppTitle(request));
		map.put("container", buildContainerPath(mapping, request));
		map.put("heartbeatRate", environment.getProperty("shiny.proxy.heartbeat-rate", "10000"));
		map.put("heartbeatPath", getContextPath() + "heartbeat");
		
		return "app";
	}
	
	@RequestMapping(value="/app/*", method=RequestMethod.POST)
	@ResponseBody
	String startApp(HttpServletRequest request) {
		Proxy proxy = findUserProxy(request);
		if (proxy == null) {
			String specId = getAppName(request);
			ProxySpec spec = proxySpecService.getSpec(specId);
			proxy = proxyService.startProxy(spec);
		}
		String mapping = getProxyEndpoint(proxy);
		return buildContainerPath(mapping, request);
	}
	
	private String buildContainerPath(String mapping, HttpServletRequest request) {
		if (mapping == null) return "";
		
		String queryString = request.getQueryString();
		queryString = (queryString == null) ? "" : "?" + queryString;
		
		String containerPath = getContextPath() + mapping + environment.getProperty("shiny.proxy.landing-page", "/") + queryString;
		return containerPath;
	}
}
