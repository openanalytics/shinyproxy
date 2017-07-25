/**
 * RDepot
 *
 * Copyright (C) 2012-${year} ${company}
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

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ModelMap;

import eu.openanalytics.services.UserService;

public abstract class BaseController {

	@Inject
	UserService userService;
	
	@Inject
	Environment environment;
	
	private static Pattern appPattern = Pattern.compile(".*/app/(.*)");
	
	protected String getUserName(HttpServletRequest request) {
		Principal principal = request.getUserPrincipal();
		String username = (principal == null) ? request.getSession().getId() : principal.getName();
		return username;
	}
	
	protected String getAppName(HttpServletRequest request) {
		return getAppName(request.getRequestURI());
	}
	
	protected String getAppName(String uri) {
		Matcher matcher = appPattern.matcher(uri);
		String appName = matcher.matches() ? matcher.group(1) : null;
		return appName;
	}
	
	protected void prepareMap(ModelMap map, HttpServletRequest request) {
		map.put("title", environment.getProperty("shiny.proxy.title"));
		map.put("logo", environment.getProperty("shiny.proxy.logo-url"));

		map.put("showNavbar", !Boolean.valueOf(environment.getProperty("shiny.proxy.hide-navbar")));
		
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		map.put("isLoggedIn", authentication != null && !(authentication instanceof AnonymousAuthenticationToken) && authentication.isAuthenticated());
		map.put("isAdmin", userService.isAdmin(authentication));
	}
}
