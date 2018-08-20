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
package eu.openanalytics.shinyproxy;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configurers.GlobalAuthenticationConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import eu.openanalytics.shinyproxy.auth.IAuthenticationBackend;
import eu.openanalytics.shinyproxy.auth.LogoutHandler;
import eu.openanalytics.shinyproxy.entity.App;
import eu.openanalytics.shinyproxy.entity.AppGroup;
import eu.openanalytics.shinyproxy.entity.Group;
import eu.openanalytics.shinyproxy.services.AppService;
import eu.openanalytics.shinyproxy.services.AppService.ShinyApp;
import eu.openanalytics.shinyproxy.services.ShinyAppService;
import eu.openanalytics.shinyproxy.services.UserService;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

	@Inject
	LogoutHandler logoutHandler;

	//@Inject
	//AppService appService;

	@Inject
	ShinyAppService appService;

	@Inject
	UserService userService;
	
	@Inject
	IAuthenticationBackend auth;
	
	@Inject
	AuthenticationEventPublisher eventPublisher;
	
	@Override
	public void configure(WebSecurity web) throws Exception {
		web
			.ignoring().antMatchers("/assets/**").and()
			.ignoring().antMatchers("/css/**").and()
			.ignoring().antMatchers("/webjars/**");
	}

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		http
			// must disable or handle in proxy
			.csrf().disable()
			// disable X-Frame-Options
			.headers().frameOptions().disable();

		if (auth.hasAuthorization()) {
			// Limit access to the app pages
			http.authorizeRequests().antMatchers("/login", "/signin/**", "/signup").permitAll();
			/*for (ShinyApp app: appService.getApps()) {
				String[] groups = app.getGroups();
				if (groups == null || groups.length == 0) continue;
				String[] appGroups = Arrays.stream(groups).map(s -> s.toUpperCase()).toArray(i -> new String[i]);
				http.authorizeRequests().antMatchers("/app/" + app.getName()).hasAnyRole(appGroups);
			}*/
			
			for (App app: appService.getApps()) {
				AppGroup g = new AppGroup(app.getId(), app.getName());
				
				List<AppGroup> groupList = app.getGroups();
				groupList.add(g); // Create a new group named as Application name. We will use this group grant access users to application without using groups
				String[] groups = groupList.stream().map(AppGroup::getGroupName).map(p-> p.toUpperCase()).toArray(String[]::new);
				if (groups == null || groups.length == 0) continue;
				http.authorizeRequests().antMatchers("/app/" + app.getName()).hasAnyRole(groups);
			}

			// Limit access to the admin pages
			http.authorizeRequests().antMatchers("/admin").hasAnyRole(userService.getAdminGroups());
			
			// All other pages are available to authenticated users
			http.authorizeRequests().anyRequest().fullyAuthenticated();

			http
				.formLogin()
					.loginPage("/login")
					.and()
				.logout()
					.logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
					.logoutSuccessHandler(logoutHandler)
					.logoutSuccessUrl("/login");
		}
		
		auth.configureHttpSecurity(http);
	}

	@Bean
	public GlobalAuthenticationConfigurerAdapter authenticationConfiguration() {
		return new GlobalAuthenticationConfigurerAdapter() {
			@Override
			public void init(AuthenticationManagerBuilder amb) throws Exception {
				amb.authenticationEventPublisher(eventPublisher);
				auth.configureAuthenticationManagerBuilder(amb);
			}
		};
	}
	
	@Bean(name="authenticationManager")
	@Override
	public AuthenticationManager authenticationManagerBean() throws Exception {
		return super.authenticationManagerBean();
	}
}