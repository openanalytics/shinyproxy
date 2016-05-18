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
	
	public List<ShinyApp> getApps() {
		return apps;
	}
	
	public ShinyApp getApp(String name) {
		for (ShinyApp app: apps) {
			if (app.getName().equals(name)) return app;
		}
		return null;
	}
	
	public static class ShinyApp {
		
		private String name;
		private String[] dockerCmd;
		private String dockerImage;
		private String ldapGroup;
		
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
		
		public String getLdapGroup() {
			return ldapGroup;
		}
		public void setLdapGroup(String ldapGroup) {
			this.ldapGroup = ldapGroup;
		}
	}
}