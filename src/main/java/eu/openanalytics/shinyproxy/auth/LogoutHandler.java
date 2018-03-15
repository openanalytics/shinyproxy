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
package eu.openanalytics.shinyproxy.auth;

import java.io.IOException;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import eu.openanalytics.shinyproxy.ShinyProxyApplication;
import eu.openanalytics.shinyproxy.services.UserService;

@Component
public class LogoutHandler implements LogoutSuccessHandler {

	@Inject
	UserService userService;
	
	@Inject
	Environment environment;
	
	@Override
	public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
		if (authentication != null) {
			String userName = authentication.getName();
			if (authentication.getPrincipal() instanceof UserDetails) {
				userName = ((UserDetails) authentication.getPrincipal()).getUsername();
			}
			if (userName != null) userService.logout(userName);
		}
		response.sendRedirect(ShinyProxyApplication.getContextPath(environment) + "/");
	}
	
}
