/**
 * ShinyProxy
 *
 * Copyright (C) 2012-2017 Open Analytics
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
package eu.openanalytics.controllers;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

import eu.openanalytics.ShinyProxyApplication;
import eu.openanalytics.services.AppService;
import eu.openanalytics.services.DockerService;

@Controller
public class AppController extends BaseController {

	@Inject
	DockerService dockerService;
	
	@Inject
	AppService appService;

	@RequestMapping("/app/*")
	String app(ModelMap map, HttpServletRequest request) {
		prepareMap(map, request);
		
		String mapping = dockerService.getMapping(getUserName(request), getAppName(request));
		
		String queryString = request.getQueryString();
		if (queryString == null) queryString = "";
		else queryString = "?" + queryString;
		
		String contextPath = ShinyProxyApplication.getContextPath(environment);
		String containerPath = contextPath + "/" + mapping + environment.getProperty("shiny.proxy.landing-page") + queryString;

		map.put("container", containerPath);
		map.put("heartbeatRate", environment.getProperty("shiny.proxy.heartbeat-rate", "10000"));
		map.put("heartbeatPath", contextPath + "/heartbeat");
		
		return "app";
	}
}
