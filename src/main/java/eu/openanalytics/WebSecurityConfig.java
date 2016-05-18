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
package eu.openanalytics;

import javax.inject.Inject;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configurers.GlobalAuthenticationConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import eu.openanalytics.components.LogoutHandler;

/**
 * @author Torkild U. Resheim, Itema AS
 */
@Configuration
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {
	
	@Inject
	LogoutHandler logoutHandler;
	
	public void configure(WebSecurity web) throws Exception {
        web
            .ignoring().antMatchers("/css/**").and()
        	.ignoring().antMatchers("/webjars/**");
    }
	@Override
	protected void configure(HttpSecurity http) throws Exception {
		http
			// must disable or handle in proxy
			.csrf()
				.disable()
			// force authentication
			.authorizeRequests()
				.antMatchers("/login").permitAll()
				.anyRequest().fullyAuthenticated()
				.and()
			.formLogin()
				.loginPage("/login")
				.and()
			.logout()
				.logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
				.logoutSuccessHandler(logoutHandler)
				.logoutSuccessUrl("/login")
		        .and()
			// disable X-Frame-Options
			.headers()
				.frameOptions()
					.sameOrigin();
	}

	@Configuration
	protected static class AuthenticationConfiguration extends
			GlobalAuthenticationConfigurerAdapter {

		@Inject
		private Environment environment;		

		@Override
		public void init(AuthenticationManagerBuilder auth) throws Exception {
			boolean secured = null != environment.getProperty("shiny.proxy.ldap.manager-dn");
			if (secured) {
				auth
					.ldapAuthentication()
						.userDnPatterns("uid={0}")
						.contextSource().url(environment.getProperty("shiny.proxy.ldap.url"))
						.managerPassword(environment.getProperty("shiny.proxy.ldap.manager-password"))
						.managerDn(environment.getProperty("shiny.proxy.ldap.manager-dn"));
			} else {
				auth
				.ldapAuthentication()
					.userDnPatterns("uid={0}")
					.contextSource().url(environment.getProperty("shiny.proxy.ldap.url"));
				}
			}
		}
}