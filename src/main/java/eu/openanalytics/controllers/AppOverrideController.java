/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2017 Open Analytics
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

import eu.openanalytics.ShinyProxyApplication;
import eu.openanalytics.services.AppService;
import eu.openanalytics.services.DockerService;
import java.security.Principal;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.log4j.Logger;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class AppOverrideController extends BaseController {

    private Logger log = Logger.getLogger(AppOverrideController.class);

	@Inject
	DockerService dockerService;
	
	@Inject
	AppService appService;

	@RequestMapping(value="/appOverride/**", params={"sig", "expires"}, method=RequestMethod.GET)
	String appOverride(ModelMap map, Principal principal, HttpServletRequest request) {
        // TODO: check sig and expires
		prepareMap(map, request);
		
		String mapping = dockerService.getMapping(getUserName(request), getAppName(request), getTagOverride(request), false);
		String contextPath = ShinyProxyApplication.getContextPath(environment);

		map.put("appTitle", getAppTitle(request));
		map.put("container", appService.buildContainerPath(mapping, request));
		map.put("heartbeatRate", environment.getProperty("shiny.proxy.heartbeat-rate", "10000"));
		map.put("heartbeatPath", contextPath + "/heartbeat");
		
        return "app";
    }

	@RequestMapping(value="/appOverride/**", params={"sig", "expires"}, method=RequestMethod.POST)
	@ResponseBody
	String startAppOverride(HttpServletRequest request) {
        // TODO: check sig and expires
		String mapping = dockerService.getMapping(getUserName(request), getAppName(request), getTagOverride(request), true);
		return appService.buildContainerPath(mapping, request);
    }

    @RequestMapping(value="/appOverride/**", method={RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    String createAppOverride(
        HttpServletRequest request, HttpServletResponse response,
        // number of seconds before signature expires
        // clamped by config
        @RequestParam("expiry") Integer expiry
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            // User is not logged in
            response.setStatus(401);
            response.setContentType("text/plain");
            return "Unauthorized: you must sign in to create app overrides";
        }
        if (!userService.isAdmin(authentication)) {
            // User is logged in, but is not an admin
            response.setStatus(403);
            response.setContentType("text/plain");
            return "Forbidden: only admins may create app overrides";
        }
        // User is logged in and is an admin
        String overrideLocation = "?sig=TODO&expires=TODO";
        String requestQS = request.getQueryString();
        if (requestQS != null) {
            overrideLocation += "&" + requestQS;
        }
        if (request.getMethod() == "GET") {
            response.setStatus(302);
            response.setHeader("Location", overrideLocation);
            return null;
        } else {
            return overrideLocation;
        }
    }
}
