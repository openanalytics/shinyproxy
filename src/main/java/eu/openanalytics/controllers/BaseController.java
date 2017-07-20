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
