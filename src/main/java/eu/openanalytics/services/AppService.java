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
package eu.openanalytics.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@ConfigurationProperties(prefix = "shiny")
@Service
public class AppService {

	private List<ShinyApp> apps = new ArrayList<>();
	
	@Inject
	Environment environment;
	
	public ShinyApp getApp(String name) {
		for (ShinyApp app: apps) {
			if (app.getName().equals(name)) return app;
		}
		return null;
	}

	public List<ShinyApp> getApps() {
		return apps;
	}
	
	public List<ShinyApp> getApps(Authentication principalAuth) {
		List<ShinyApp> accessibleApps = new ArrayList<>();
		for (ShinyApp app: apps) {
			if (canAccess(principalAuth, app.getName())) accessibleApps.add(app);
		}
		return accessibleApps;
	}
	
	public boolean canAccess(Authentication principalAuth, String appName) {
		String[] appRoles = getAppRoles(appName);
		if (appRoles.length == 0 || principalAuth == null) return true;
		Arrays.sort(appRoles);
		for (GrantedAuthority auth: principalAuth.getAuthorities()) {
			String role = auth.getAuthority().toUpperCase();
			if (role.startsWith("ROLE_")) role = role.substring(5);
			if (Arrays.binarySearch(appRoles, role) >= 0) return true;
		}
		return false;
	}
	
	public String[] getAppRoles(String appName) {
		ShinyApp app = getApp(appName);
		if (app == null || app.getLdapGroups() == null) return new String[0];
		String[] roles = new String[app.getLdapGroups().length];
		for (int i = 0; i < roles.length; i++) roles[i] = app.getLdapGroups()[i].toUpperCase();
		return roles;
	}
	
	public static class ShinyApp {
		
		private String name;
		private String[] dockerCmd;
		private String dockerImage;
		private String[] ldapGroups;
		
		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}
		
		public String[] getDockerCmd() {
			return dockerCmd;
		}
		public void setDockerCmd(String[] dockerCmd) {
			this.dockerCmd = dockerCmd;
		}

		public String getDockerImage() {
			return dockerImage;
		}
		public void setDockerImage(String dockerImage) {
			this.dockerImage = dockerImage;
		}
		
		public String[] getLdapGroups() {
			return ldapGroups;
		}
		public void setLdapGroups(String[] ldapGroups) {
			this.ldapGroups = ldapGroups;
		}
	}
}