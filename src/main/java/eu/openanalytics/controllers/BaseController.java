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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.Principal;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ModelMap;
import org.springframework.util.StreamUtils;

import eu.openanalytics.services.AppService;
import eu.openanalytics.services.AppService.ShinyApp;
import eu.openanalytics.services.UserService;

public abstract class BaseController {

	@Inject
	AppService appService;
	
	@Inject
	UserService userService;
	
	@Inject
	Environment environment;
	
	private static Logger logger = Logger.getLogger(BaseController.class);
	private static Pattern appPattern = Pattern.compile(".*/app/(.*)");
	private static Map<String, String> imageCache = new HashMap<>();
	
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
	
	protected String getAppTitle(HttpServletRequest request) {
		String appName = getAppName(request);
		if (appName == null || appName.isEmpty()) return "";
		ShinyApp app = appService.getApp(appName);
		if (app == null || app.getDisplayName() == null || app.getDisplayName().isEmpty()) return appName;
		else return app.getDisplayName();
	}
	
	protected void prepareMap(ModelMap map, HttpServletRequest request) {
		map.put("title", environment.getProperty("shiny.proxy.title"));
		map.put("logo", resolveImageURI(environment.getProperty("shiny.proxy.logo-url")));
		map.put("showNavbar", !Boolean.valueOf(environment.getProperty("shiny.proxy.hide-navbar")));
		
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		map.put("isLoggedIn", authentication != null && !(authentication instanceof AnonymousAuthenticationToken) && authentication.isAuthenticated());
		map.put("isAdmin", userService.isAdmin(authentication));
	}
	
	protected String resolveImageURI(String resourceURI) {
		if (resourceURI == null || resourceURI.isEmpty()) return resourceURI;
		if (imageCache.containsKey(resourceURI)) return imageCache.get(resourceURI);
		
		String resolvedValue = resourceURI;
		if (resourceURI.toLowerCase().startsWith("file://")) {
			String mimetype = URLConnection.guessContentTypeFromName(resourceURI);
			if (mimetype == null) {
				logger.warn("Cannot determine mimetype for resource: " + resourceURI);
			} else {
				try (InputStream input = new URL(resourceURI).openConnection().getInputStream()) {
					byte[] data = StreamUtils.copyToByteArray(input);
					String encoded = Base64.getEncoder().encodeToString(data);
					resolvedValue = String.format("data:%s;base64,%s", mimetype, encoded);
				} catch (IOException e) {
					logger.warn("Failed to convert file URI to data URI: " + resourceURI, e);
				}
			}
		}
		imageCache.put(resourceURI, resolvedValue);
		return resolvedValue;
	}
}
