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
package eu.openanalytics.auth;

import java.util.Arrays;

import javax.inject.Inject;

import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configurers.provisioning.InMemoryUserDetailsManagerConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.stereotype.Component;

import eu.openanalytics.auth.AuthenticationTypeProxy.IAuthenticationType;

/**
 * Simple authentication method where user/password combinations are
 * provided by the application.yml file.
 */
@Component
public class SimpleAuthenticationType implements IAuthenticationType {

	@Inject
	Environment environment;
	
	@Override
	public boolean hasAuthorization() {
		return true;
	}
	
	@Override
	public void configureHttpSecurity(HttpSecurity http) throws Exception {
		// Nothing to do.
	}

	@Override
	public void configureAuthenticationManagerBuilder(AuthenticationManagerBuilder auth) throws Exception {
		InMemoryUserDetailsManagerConfigurer<AuthenticationManagerBuilder> userDetails = auth.inMemoryAuthentication();
		int i=0;
		SimpleUser user = loadUser(i++);
		while (user != null) {
			userDetails.withUser(user.name).password(user.password).roles(user.roles);
			user = loadUser(i++);
		}
	}
	
	private SimpleUser loadUser(int index) {
		String userName = environment.getProperty(String.format("shiny.proxy.users[%d].name", index));
		if (userName == null) return null;
		String password = environment.getProperty(String.format("shiny.proxy.users[%d].password", index));
		String[] roles = environment.getProperty(String.format("shiny.proxy.users[%d].groups", index), String[].class);
		roles = Arrays.stream(roles).map(s -> s.toUpperCase()).toArray(i -> new String[i]);
		return new SimpleUser(userName, password, roles);
	}
	
	private static class SimpleUser {
		
		public String name;
		public String password;
		public String[] roles;
		
		public SimpleUser(String name, String password, String[] roles) {
			this.name = name;
			this.password = password;
			this.roles = roles;
		}
		
	}
}
