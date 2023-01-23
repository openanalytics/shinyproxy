/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2021 Open Analytics
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
package eu.openanalytics.shinyproxy;

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.security.ICustomSecurityConfig;
import eu.openanalytics.containerproxy.service.UserService;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;

import javax.inject.Inject;

@Component
public class UISecurityConfig implements ICustomSecurityConfig {

	@Inject
	private IAuthenticationBackend auth;
	
	@Inject
	private UserService userService;

	@Inject
	private OperatorService operatorService;

	@Override
	public void apply(HttpSecurity http) throws Exception {
		if (auth.hasAuthorization()) {
			
			// Limit access to the app pages according to spec permissions
			http.authorizeRequests().antMatchers("/app/{specId}/**").access("@proxyAccessControlService.canAccessOrHasExistingProxy(authentication, #specId)");
			http.authorizeRequests().antMatchers("/app_i/{specId}/**").access("@proxyAccessControlService.canAccessOrHasExistingProxy(authentication, #specId)");
			http.authorizeRequests().antMatchers("/app_direct/{specId}/**").access("@proxyAccessControlService.canAccessOrHasExistingProxy(authentication, #specId)");
			http.authorizeRequests().antMatchers("/app_direct_i/{specId}/**").access("@proxyAccessControlService.canAccessOrHasExistingProxy(authentication, #specId)");

			// Limit access to the admin pages
			http.authorizeRequests().antMatchers("/admin").hasAnyRole(userService.getAdminGroups());
			http.authorizeRequests().antMatchers("/admin/data").hasAnyRole(userService.getAdminGroups());

			http.addFilterAfter(new AuthenticationRequiredFilter(), ExceptionTranslationFilter.class);
		}

	}
}
