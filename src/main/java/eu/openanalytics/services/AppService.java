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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
		private String dockerNetwork;
		private String[] dockerNetworkConnections;
		private String dockerMemory;
		private String dockerEnvFile;
		private Map<String, String> dockerEnv = new HashMap<String, String>();
		private String[] dockerVolumes;
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
		
		public String getDockerNetwork() {
			return dockerNetwork;
		}
		public void setDockerNetwork(String dockerNetwork) {
			this.dockerNetwork = dockerNetwork;
		}
		
		public String[] getDockerNetworkConnections() {
			return dockerNetworkConnections;
		}
		public void setDockerNetworkConnections(String[] dockerNetworkConnections) {
			this.dockerNetworkConnections = dockerNetworkConnections;
		}
		
		public String getDockerMemory() {
			return dockerMemory;
		}
		public void setDockerMemory(String dockerMemory) {
			this.dockerMemory = dockerMemory;
		}
		
		public String getDockerEnvFile() {
			return dockerEnvFile;
		}
		public void setDockerEnvFile(String dockerEnvFile) {
			this.dockerEnvFile = dockerEnvFile;
		}

		public Map<String, String> getDockerEnv() {
			return dockerEnv;
		}
		public void setDockerEnv(Map<String, String> dockerEnv) {
			this.dockerEnv = dockerEnv;
		}

		public String[] getDockerVolumes() {
			return dockerVolumes;
		}
		public void setDockerVolumes(String[] dockerVolumes) {
			this.dockerVolumes = dockerVolumes;
		}
		
		public String[] getGroups() {
			return groups;
		}
		public void setGroups(String[] groups) {
			this.groups = groups;
		}
	}
}