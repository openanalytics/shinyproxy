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
import java.util.List;

import javax.inject.Inject;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
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
	
	public static class ShinyApp {
		
		private String name;
		private String displayName;
		private String description;
		private String logoUrl;
		private String[] dockerCmd;
		private String dockerImage;
		private String[] dockerDns;
		private String[] groups;
		
		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}
		
		public String getDisplayName() {
			return displayName;
		}
		public void setDisplayName(String displayName) {
			this.displayName = displayName;
		}
		
		public String getDescription() {
			return description;
		}
		public void setDescription(String description) {
			this.description = description;
		}
		
		public String getLogoUrl() {
			return logoUrl;
		}
		public void setLogoUrl(String logoUrl) {
			this.logoUrl = logoUrl;
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
		
		public String[] getDockerDns() {
			return dockerDns;
		}
		public void setDockerDns(String[] dockerDns) {
			this.dockerDns = dockerDns;
		}
		
		public String[] getGroups() {
			return groups;
		}
		public void setGroups(String[] groups) {
			this.groups = groups;
		}
	}
}