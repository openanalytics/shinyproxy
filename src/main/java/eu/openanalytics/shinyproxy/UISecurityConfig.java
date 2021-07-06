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

import java.util.Arrays;

import javax.inject.Inject;

import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.stereotype.Component;

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.security.ICustomSecurityConfig;
import eu.openanalytics.containerproxy.service.ProxyService;
import eu.openanalytics.containerproxy.service.UserService;

@Component
public class UISecurityConfig implements ICustomSecurityConfig {

	@Inject
	private IAuthenticationBackend auth;
	
	@Inject
	private UserService userService;
	
	@Inject
	private ProxyService proxyService;

	@Inject
	private OperatorService operatorService;

	@Override
	public void apply(HttpSecurity http) throws Exception {
		if (auth.hasAuthorization()) {
			
			// Limit access to the app pages according to spec permissions
			for (ProxySpec spec: proxyService.getProxySpecs(null, true)) {
				if (spec.getAccessControl() == null) continue;
				
				String[] groups = spec.getAccessControl().getGroups();
				if (groups == null || groups.length == 0) continue;
				
				String[] appGroups = Arrays.stream(groups).map(s -> s.toUpperCase()).toArray(i -> new String[i]);
				http.authorizeRequests().antMatchers("/app/" + spec.getId()).hasAnyRole(appGroups);
			}

			// Limit access to the admin pages
			http.authorizeRequests().antMatchers("/admin").hasAnyRole(userService.getAdminGroups());
		}

		if (operatorService.isEnabled()) {
		    // running using operator
            http.addFilterAfter(new OperatorCookieFilter(), AnonymousAuthenticationFilter.class);
            http.authorizeRequests().antMatchers("/server-transfer").permitAll();
        }
	}
}
