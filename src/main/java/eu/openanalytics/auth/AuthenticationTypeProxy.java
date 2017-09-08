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

import javax.inject.Inject;

import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationTypeProxy {

	@Inject
	Environment environment;

	@Inject
	NoopAuthenticationType noop;

	@Inject
	LDAPAuthenticationType ldap;

	@Inject
	SimpleAuthenticationType simple;

	@Inject
	SocialAuthenticationType social;

	@Inject
	KeycloakAuthenticationType keycloak;
	
	public IAuthenticationType get() {
		switch (name()) {
		case "ldap":
			return ldap;
		case "simple":
			return simple;
		case "social":
			return social;
		case "keycloak":
			return keycloak;
		default:
			return noop;
		}
	}

	public String name() {
		return environment.getProperty("shiny.proxy.authentication");
	}
	
	public static interface IAuthenticationType {

		public boolean hasAuthorization();

		public void configureHttpSecurity(HttpSecurity http) throws Exception;

		public void configureAuthenticationManagerBuilder(AuthenticationManagerBuilder auth) throws Exception;

	}

}
