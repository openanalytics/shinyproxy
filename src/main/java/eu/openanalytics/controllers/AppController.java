/**
 * Copyright 2016 Open Analytics, Belgium
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.openanalytics.controllers;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

import eu.openanalytics.services.AppService;
import eu.openanalytics.services.DockerService;
import eu.openanalytics.services.UserService;

@Controller
public class AppController {

	@Inject
	DockerService dockerService;
	
	@Inject
	AppService appService;

	@Inject
	UserService userService;
	
	@Inject
	Environment environment;

	@RequestMapping("/app/*")
	String app(ModelMap map, Principal principal, HttpServletRequest request) {
		String userName = (principal == null) ? request.getSession().getId() : principal.getName();
		Matcher matcher = Pattern.compile(".*/app/(.*)").matcher(request.getRequestURI());
		String appName = matcher.matches() ? matcher.group(1) : null;
		String mapping = dockerService.getMapping(userName, appName);
		
		String queryString = request.getQueryString();
		if (queryString == null) queryString = "";
		else queryString = "?" + queryString;
		
		map.put("title", environment.getProperty("shiny.proxy.title"));
		map.put("logo", environment.getProperty("shiny.proxy.logo-url"));
		map.put("container", "/" + mapping + environment.getProperty("shiny.proxy.landing-page") + queryString);
		map.put("heartbeatRate", environment.getProperty("shiny.proxy.heartbeat-rate", "10000"));
		map.put("adminGroups", userService.getAdminRoles());
		
		return "app";
	}
}
