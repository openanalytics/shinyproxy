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
package eu.openanalytics.shinyproxy.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
	
	public static class ShinyApp extends HashMap<String, String> {
		
		private static final long serialVersionUID = 8943311295945978160L;

		public String getName() {
			return get("name");
		}
		
		public String getDisplayName() {
			return get("display-name");
		}
		
		public String getDescription() {
			return get("description");
		}
		
		public String getLogoUrl() {
			return get("logo-url");
		}
		
		public String[] getGroups() {
			return getArray("groups");
		}
		
		//TODO Move docker-related settings out of here
		
		public String[] getDockerCmd() {
			return getArray("docker-cmd");
		}
		
		public String getDockerImage() {
			return get("docker-image");
		}
		
		public String[] getDockerDns() {
			return getArray("docker-dns");
		}
		
		public String getDockerNetwork() {
			return get("docker-network");
		}
		
		public String[] getDockerNetworkConnections() {
			return getArray("docker-network-connections");
		}
		
		public String getDockerMemory() {
			return get("docker-memory");
		}
		
		public String getDockerEnvFile() {
			return get("docker-env-file");
		}

		public Map<String, String> getDockerEnv() {
			return getMap("docker-env");
		}

		public String[] getDockerVolumes() {
			return getArray("docker-volumes");
		}
		
		public String[] getArray(String key) {
			List<String> values = new ArrayList<>();
			keySet().stream()
				.filter(k -> k.startsWith(key))
				.map(k -> get(k))
				.forEach(v -> {
					String[] fields = StringUtils.commaDelimitedListToStringArray(v);
					for (String f: fields) values.add(f.trim());
				});
			return values.toArray(new String[values.size()]);
		}
		
		public Map<String,String> getMap(String key) {
			return keySet().stream()
					.filter(k -> k.startsWith(key))
					.collect(Collectors.toMap(k -> k.substring(1 + key.length()), k -> get(k)));
		}
	}
}