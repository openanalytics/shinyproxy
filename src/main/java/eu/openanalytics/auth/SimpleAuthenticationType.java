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
