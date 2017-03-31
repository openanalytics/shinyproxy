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

	public IAuthenticationType get() {
		switch (name()) {
		case "ldap":
			return ldap;
		case "simple":
			return simple;
		case "social":
			return social;
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
