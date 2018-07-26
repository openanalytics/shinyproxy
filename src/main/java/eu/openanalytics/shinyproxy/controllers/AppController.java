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

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import eu.openanalytics.shinyproxy.ShinyProxyApplication;
import eu.openanalytics.shinyproxy.services.AppService;
import eu.openanalytics.shinyproxy.services.ProxyService;

@Controller
public class AppController extends BaseController {

	@Inject
	ProxyService proxyService;
	
	@Inject
	AppService appService;

	@RequestMapping(value="/app/*", method=RequestMethod.GET)
	String app(ModelMap map, HttpServletRequest request) {
		logger.info("app started: " + request.getRequestURI());
		prepareMap(map, request);
		
		String mapping = proxyService.getMapping(request, getUserName(request), getAppName(request), false);
		String contextPath = ShinyProxyApplication.getContextPath(environment);
		logger.info("app contextPath=" + contextPath);		

		map.put("appTitle", getAppTitle(request));
		map.put("container", buildContainerPath(mapping, request));
		map.put("heartbeatRate", environment.getProperty("shiny.proxy.heartbeat-rate", "10000"));
		map.put("heartbeatPath", contextPath + "/heartbeat");
		
		logger.info("app finished: ");
		
		return "app";
	}
	
	@RequestMapping(value="/app/*", method=RequestMethod.POST)
	@ResponseBody
	String startApp(HttpServletRequest request) {		
		logger.info("startApp started: " + request.getRequestURI());
		
		String mapping = proxyService.getMapping(request, getUserName(request), getAppName(request), true);
		
		logger.info("startApp finished: mapping="+ mapping);
		return buildContainerPath(mapping, request);
	}
	
	private String buildContainerPath(String mapping, HttpServletRequest request) {
		logger.info("buildContainerPath started: request=" + request.getRequestURI() + ", mapping="+ mapping);
		if (mapping == null) return "";
		
		String queryString = request.getQueryString();
		queryString = (queryString == null) ? "" : "?" + queryString;
		
		String contextPath = ShinyProxyApplication.getContextPath(environment);
		String containerPath = contextPath + "/" + mapping + environment.getProperty("shiny.proxy.landing-page", "/") + queryString;
		
		logger.info("buildContainerPath finished: containerPath="+ containerPath);
		return containerPath;
	}
}
