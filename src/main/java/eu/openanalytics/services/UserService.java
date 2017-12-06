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
package eu.openanalytics.services;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.event.AbstractAuthenticationEvent;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import eu.openanalytics.services.AppService.ShinyApp;
import eu.openanalytics.services.EventService.EventType;

@Service
public class UserService implements ApplicationListener<AbstractAuthenticationEvent> {

	private Logger log = Logger.getLogger(UserService.class);

	@Inject
	Environment environment;

	@Inject
	DockerService dockerService;

	@Inject
	EventService eventService;
	
	@Inject
	AppService appService;
	
	public Authentication getCurrentAuth() {
		return SecurityContextHolder.getContext().getAuthentication();
	}
	
	public String[] getAdminGroups() {
		String[] adminGroups = environment.getProperty("shiny.proxy.admin-groups", String[].class);
		if (adminGroups == null) adminGroups = new String[0];
		for (int i = 0; i < adminGroups.length; i++) {
			adminGroups[i] = adminGroups[i].toUpperCase();
		}
		return adminGroups;
	}
	
	public List<ShinyApp> getAccessibleApps(Authentication principalAuth) {
		List<ShinyApp> accessibleApps = new ArrayList<>();
		for (ShinyApp app: appService.getApps()) {
			if (canAccess(principalAuth, app.getName())) accessibleApps.add(app);
		}
		return accessibleApps;
	}
	
	public String[] getGroups(Authentication principalAuth) {
		List<String> groups = new ArrayList<>();
		if (principalAuth != null) {
			for (GrantedAuthority auth: principalAuth.getAuthorities()) {
				String authName = auth.getAuthority().toUpperCase();
				if (authName.startsWith("ROLE_")) authName = authName.substring(5);
				groups.add(authName);
			}
		}
		return groups.toArray(new String[groups.size()]);
	}
	
	public boolean isAdmin(Authentication principalAuth) {
		for (String adminGroups: getAdminGroups()) {
			if (isMember(principalAuth, adminGroups)) return true;
		}
		return false;
	}
	
	private boolean isMember(Authentication principalAuth, String groupName) {
		if (principalAuth == null || groupName == null) return false;
		for (String group: getGroups(principalAuth)) {
			if (group.equalsIgnoreCase(groupName)) return true;
		}
		return false;
	}
	
	private boolean canAccess(Authentication principalAuth, String appName) {
		ShinyApp app = appService.getApp(appName);
		if (app == null) return false;
		if (app.getGroups() == null || app.getGroups().length == 0) return true;
		if (principalAuth == null || principalAuth instanceof AnonymousAuthenticationToken) return true;
		for (String group: app.getGroups()) {
			if (isMember(principalAuth, group)) return true;
		}
		return false;
	}

	@Override
	public void onApplicationEvent(AbstractAuthenticationEvent event) {
		Authentication source = event.getAuthentication();
		if (event instanceof AbstractAuthenticationFailureEvent) {
			Exception e = ((AbstractAuthenticationFailureEvent) event).getException();
			log.info(String.format("Authentication failure [user: %s] [error: %s]", source.getName(), e.getMessage()));
		} else if (event instanceof AuthenticationSuccessEvent) {
			String userName = source.getName();
			log.info(String.format("User logged in [user: %s]", userName));
			eventService.post(EventType.Login.toString(), userName, null);
		}
	}

	public void logout(String userName) {
		log.info(String.format("User logged out [user: %s]", userName));
		eventService.post(EventType.Logout.toString(), userName, null);
		dockerService.releaseProxies(userName);
	}

}
