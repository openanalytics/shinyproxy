/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2021 Open Analytics
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.spec.DockerSwarmSecret;
import eu.openanalytics.shinyproxy.runtimevalues.MaxInstancesKey;
import eu.openanalytics.shinyproxy.runtimevalues.ShinyForceFullReloadKey;
import eu.openanalytics.shinyproxy.runtimevalues.WebSocketReconnectionModeKey;
import org.springframework.beans.factory.annotation.Autowired;
import eu.openanalytics.shinyproxy.runtimevalues.WebsocketReconnectionMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;


import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.model.spec.ProxyAccessControl;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;

/**
 * This component converts proxy specs from the 'ShinyProxy notation' into the 'ContainerProxy' notation.
 * ShinyProxy notation is slightly more compact, and omits several things that Shiny apps do not need,
 * such as definition of multiple containers.
 *
 * Also, if no port is specified, a port mapping is automatically created for Shiny port 3838.
 */
@Component
@Primary
@ConfigurationProperties(prefix = "proxy")
public class ShinyProxySpecProvider implements IProxySpecProvider {

	private static final String PROP_DEFAULT_MAX_INSTANCES = "proxy.default-max-instances";

	private List<ProxySpec> specs = new ArrayList<>();
	private Map<String, ShinyProxySpec> shinyProxySpecs = new HashMap<>();
	private List<TemplateGroup> templateGroups = new ArrayList<>();

	private static Environment environment;

	@Autowired
	public void setEnvironment(Environment env){
		ShinyProxySpecProvider.environment = env;
	}

	@PostConstruct
	public void afterPropertiesSet() {
		this.specs.stream().collect(Collectors.groupingBy(ProxySpec::getId)).forEach((id, duplicateSpecs) -> {
			if (duplicateSpecs.size() > 1) throw new IllegalArgumentException(String.format("Configuration error: spec with id '%s' is defined multiple times", id));
		});
	}

	public List<ProxySpec> getSpecs() {
		return new ArrayList<>(specs);
	}

	public ProxySpec getSpec(String id) {
		if (id == null || id.isEmpty()) return null;
		return specs.stream().filter(s -> id.equals(s.getId())).findAny().orElse(null);
	}

	public ShinyProxySpec getShinyProxySpec(String specId) {
		return shinyProxySpecs.get(specId);
	}

	public void setSpecs(List<ShinyProxySpec> specs) {
		this.specs = specs.stream().map(s -> {
			shinyProxySpecs.put(s.getId(), s);
			return ShinyProxySpecProvider.convert(s);
		}).collect(Collectors.toList());
	}

	public void setTemplateGroups(List<TemplateGroup> templateGroups) {
		this.templateGroups = templateGroups;
	}

	public List<TemplateGroup> getTemplateGroups() {
		return templateGroups;
	}

	private static ProxySpec convert(ShinyProxySpec from) {
		ProxySpec to = new ProxySpec();
		to.setId(from.getId());
		to.setDisplayName(from.getDisplayName());
		to.setDescription(from.getDescription());
		to.setLogoURL(from.getLogoURL());
		to.setMaxLifeTime(from.getMaxLifetime());
		to.setStopOnLogout(from.getStopOnLogout());
		to.setHeartbeatTimeout(from.getHeartbeatTimeout());

		if (from.getKubernetesPodPatches() != null) {
			try {
				to.setKubernetesPodPatches(from.getKubernetesPodPatches());
			} catch (Exception e) {
				throw new IllegalArgumentException(String.format("Configuration error: spec with id '%s' has invalid kubernetes-pod-patches", from.getId()));
			}
		}
		to.setKubernetesAdditionalManifests(from.getKubernetesAdditionalManifests());
		to.setKubernetesAdditionalPersistentManifests(from.getKubernetesAdditionalPersistentManifests());

		ProxyAccessControl acl = new ProxyAccessControl();
		to.setAccessControl(acl);

		if (from.getAccessGroups() != null && from.getAccessGroups().length > 0) {
			acl.setGroups(from.getAccessGroups());
		}

		if (from.getAccessUsers() != null && from.getAccessUsers().length > 0) {
			acl.setUsers(from.getAccessUsers());
		}

		if (from.getAccessExpression() != null && from.getAccessExpression().length() > 0) {
			acl.setExpression(from.getAccessExpression());
		}

		ContainerSpec cSpec = new ContainerSpec();
		cSpec.setImage(from.getContainerImage());
		cSpec.setCmd(from.getContainerCmd());
		cSpec.setEnv(from.getContainerEnv());
		cSpec.setEnvFile(from.getContainerEnvFile());
		cSpec.setNetwork(from.getContainerNetwork());
		cSpec.setNetworkConnections(from.getContainerNetworkConnections());
		cSpec.setDns(from.getContainerDns());
		cSpec.setVolumes(from.getContainerVolumes());
		cSpec.setMemoryRequest(from.getContainerMemoryRequest());
		cSpec.setMemoryLimit(from.getContainerMemoryLimit());
		cSpec.setCpuRequest(from.getContainerCpuRequest());
		cSpec.setCpuLimit(from.getContainerCpuLimit());
		cSpec.setPrivileged(from.isContainerPrivileged());
		cSpec.setLabels(from.getLabels());
		cSpec.setTargetPath(from.getTargetPath());
		cSpec.setDockerSwarmSecrets(from.getDockerSwarmSecrets());
		cSpec.setDockerRegistryDomain(from.getDockerRegistryDomain());
		cSpec.setDockerRegistryUsername(from.getDockerRegistryUsername());
		cSpec.setDockerRegistryPassword(from.getDockerRegistryPassword());

		Map<String, Integer> portMapping = new HashMap<>();
		if (from.getPort() > 0) {
			portMapping.put("default", from.getPort());
		} else {
			portMapping.put("default", 3838);
		}
		cSpec.setPortMapping(portMapping);

		to.setContainerSpecs(Collections.singletonList(cSpec));

		return to;
	}


	public List<RuntimeValue> getRuntimeValues(ProxySpec proxy) {
		List<RuntimeValue> runtimeValues = new ArrayList<>();
		ShinyProxySpec shinyProxySpec = shinyProxySpecs.get(proxy.getId());

		WebsocketReconnectionMode webSocketReconnectionMode = shinyProxySpec.getWebsocketReconnectionMode();
		if (webSocketReconnectionMode == null) {
			runtimeValues.add(new RuntimeValue(WebSocketReconnectionModeKey.inst, environment.getProperty("proxy.default-websocket-reconnection-mode", WebsocketReconnectionMode.class, WebsocketReconnectionMode.None)));
		} else {
			runtimeValues.add(new RuntimeValue(WebSocketReconnectionModeKey.inst, webSocketReconnectionMode));
		}

		runtimeValues.add(new RuntimeValue(MaxInstancesKey.inst, getMaxInstancesForSpec(proxy.getId())));
		runtimeValues.add(new RuntimeValue(ShinyForceFullReloadKey.inst, getShinyForceFullReload(proxy.getId())));

		return runtimeValues;
	}

	public Integer getMaxInstancesForSpec(String specId) {
		ShinyProxySpec shinyProxySpec = shinyProxySpecs.get(specId);
		if (shinyProxySpec == null) {
			return null;
		}
		Integer defaultMaxInstances = environment.getProperty(PROP_DEFAULT_MAX_INSTANCES, Integer.class, 1);
		Integer maxInstances = shinyProxySpec.getMaxInstances();
		if (maxInstances != null) {
            return shinyProxySpec.getMaxInstances();
		}
		return defaultMaxInstances;
	}

	public Boolean getShinyForceFullReload(String specId) {
		ShinyProxySpec shinyProxySpec = shinyProxySpecs.get(specId);
		if (shinyProxySpec == null) {
			return null;
		}
		if (shinyProxySpec.getShinyForceFullReload() != null) {
			return shinyProxySpec.getShinyForceFullReload();
		}
		return false;
	}

	public Boolean getHideNavbarOnMainPageLink(String specId) {
		ShinyProxySpec shinyProxySpec = shinyProxySpecs.get(specId);
		if (shinyProxySpec == null) {
			return null;
		}
		if (shinyProxySpec.getHideNavbarOnMainPageLink() != null) {
			return shinyProxySpec.getHideNavbarOnMainPageLink();
		}
		return false;
	}

	public String getTemplateGroupOfApp(String specId) {
		ShinyProxySpec shinyProxySpec = shinyProxySpecs.get(specId);
		if (shinyProxySpec == null) {
			return null;
		}
		return shinyProxySpec.getTemplateGroup();
	}

	public void postProcessRecoveredProxy(Proxy proxy) {
		proxy.addRuntimeValues(getRuntimeValues(proxy.getSpec()));
	}

	public static class ShinyProxySpec {

		private String id;
		private String displayName;
		private String description;
		private String logoURL;

		private String containerImage;
		private String[] containerCmd;
		private Map<String,String> containerEnv;
		private String containerEnvFile;
		private String containerNetwork;
		private String[] containerNetworkConnections;
		private String[] containerDns;
		private String[] containerVolumes;
		private String containerMemoryRequest;
		private String containerMemoryLimit;
		private String containerCpuRequest;
		private String containerCpuLimit;
		private boolean containerPrivileged;
		private String kubernetesPodPatches;
		private List<String> kubernetesAdditionalManifests = new ArrayList<>();
		private List<String> kubernetesAdditionalPersistentManifests = new ArrayList<>();
		private List<DockerSwarmSecret> dockerSwarmSecrets = new ArrayList<>();
		private String dockerRegistryDomain;
		private String dockerRegistryUsername;
		private String dockerRegistryPassword;

		private String targetPath;
		private WebsocketReconnectionMode websocketReconnectionMode;
		private Boolean shinyForceFullReload;
		private Integer maxInstances;
		private Boolean hideNavbarOnMainPageLink;
		private Long maxLifetime;
		private Boolean stopOnLogout;
		private Long heartbeatTimeout;

		private Map<String,String> labels;

		private int port;
		private String[] accessGroups;
		private String[] accessUsers;
		private String accessExpression;
		private String templateGroup;
		private Map<String, String> templateProperties = new HashMap<>();

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
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

		public String getLogoURL() {
			return logoURL;
		}

		public void setLogoURL(String logoURL) {
			this.logoURL = logoURL;
		}

		public String getContainerImage() {
			return containerImage;
		}

		public void setContainerImage(String containerImage) {
			this.containerImage = containerImage;
		}

		public String[] getContainerCmd() {
			return containerCmd;
		}

		public void setContainerCmd(String[] containerCmd) {
			this.containerCmd = containerCmd;
		}

		public Map<String, String> getContainerEnv() {
			return containerEnv;
		}

		public void setContainerEnv(Map<String, String> containerEnv) {
			this.containerEnv = containerEnv;
		}

		public String getContainerEnvFile() {
			return containerEnvFile;
		}

		public void setContainerEnvFile(String containerEnvFile) {
			this.containerEnvFile = containerEnvFile;
		}

		public String getContainerNetwork() {
			return containerNetwork;
		}

		public void setContainerNetwork(String containerNetwork) {
			this.containerNetwork = containerNetwork;
		}

		public String[] getContainerNetworkConnections() {
			return containerNetworkConnections;
		}

		public void setContainerNetworkConnections(String[] containerNetworkConnections) {
			this.containerNetworkConnections = containerNetworkConnections;
		}

		public String[] getContainerDns() {
			return containerDns;
		}

		public void setContainerDns(String[] containerDns) {
			this.containerDns = containerDns;
		}

		public String[] getContainerVolumes() {
			return containerVolumes;
		}

		public void setContainerVolumes(String[] containerVolumes) {
			this.containerVolumes = containerVolumes;
		}

		public String getContainerMemoryRequest() {
			return containerMemoryRequest;
		}

		public void setContainerMemoryRequest(String containerMemoryRequest) {
			this.containerMemoryRequest = containerMemoryRequest;
		}

		public String getContainerMemoryLimit() {
			return containerMemoryLimit;
		}

		public void setContainerMemoryLimit(String containerMemoryLimit) {
			this.containerMemoryLimit = containerMemoryLimit;
		}

		public String getContainerCpuRequest() {
			return containerCpuRequest;
		}

		public void setContainerCpuRequest(String containerCpuRequest) {
			this.containerCpuRequest = containerCpuRequest;
		}

		public String getContainerCpuLimit() {
			return containerCpuLimit;
		}

		public void setContainerCpuLimit(String containerCpuLimit) {
			this.containerCpuLimit = containerCpuLimit;
		}

		public boolean isContainerPrivileged() {
			return containerPrivileged;
		}

		public void setContainerPrivileged(boolean containerPrivileged) {
			this.containerPrivileged = containerPrivileged;
		}

		public Map<String, String> getLabels() {
			return labels;
		}

		public void setLabels(Map<String, String> labels) {
			this.labels = labels;
		}

		public int getPort() {
			return port;
		}

		public void setPort(int port) {
			this.port = port;
		}

		public String[] getAccessGroups() {
			return accessGroups;
		}

		public void setAccessGroups(String[] accessGroups) {
			this.accessGroups = accessGroups;
		}

		public String getKubernetesPodPatches() {
			return kubernetesPodPatches;
		}

		public void setKubernetesPodPatches(String kubernetesPodPatches) {
			this.kubernetesPodPatches = kubernetesPodPatches;
		}

		public void setKubernetesAdditionalManifests(List<String> manifests) {
			this.kubernetesAdditionalManifests = manifests;
		}

		public List<String> getKubernetesAdditionalManifests() {
			return kubernetesAdditionalManifests;
		}

		public void setKubernetesAdditionalPersistentManifests(List<String> manifests) {
			this.kubernetesAdditionalPersistentManifests = manifests;
		}

		public List<String> getKubernetesAdditionalPersistentManifests() {
			return kubernetesAdditionalPersistentManifests;
                }

		public String getTargetPath() {
			return targetPath;
		}

		public void setTargetPath(String targetPath) {
			this.targetPath = targetPath;
		}

		public WebsocketReconnectionMode getWebsocketReconnectionMode() {
			return websocketReconnectionMode;
		}

		public void setWebsocketReconnectionMode(WebsocketReconnectionMode websocketReconnectionMode) {
			this.websocketReconnectionMode = websocketReconnectionMode;
		}

		public Boolean getShinyForceFullReload() {
			return shinyForceFullReload;
		}

		public void setShinyForceFullReload(Boolean shinyForceFullReload) {
			this.shinyForceFullReload = shinyForceFullReload;
		}

		public Integer getMaxInstances() {
			return maxInstances;
		}

		public void setMaxInstances(Integer maxInstances) {
			this.maxInstances = maxInstances;
		}

		public Boolean getHideNavbarOnMainPageLink() {
			return hideNavbarOnMainPageLink;
		}

		public void setHideNavbarOnMainPageLink(Boolean hideNavbarOnMainPageLink) {
			this.hideNavbarOnMainPageLink = hideNavbarOnMainPageLink;
		}

		public Long getMaxLifetime() {
			return maxLifetime;
		}

		public void setMaxLifetime(Long maxLifetime) {
			this.maxLifetime = maxLifetime;
		}

		public Boolean getStopOnLogout() {
			return stopOnLogout;
		}

		public void setStopOnLogout(Boolean stopOnLogout) {
			this.stopOnLogout = stopOnLogout;
		}

		public void setHeartbeatTimeout(Long heartbeatTimeout) {
			this.heartbeatTimeout = heartbeatTimeout;
		}

		public Long getHeartbeatTimeout() {
			return heartbeatTimeout;
		}

		public void setTemplateGroup(String templateGroup) {
			this.templateGroup = templateGroup;
		}

		public String getTemplateGroup() {
			return templateGroup;
		}

		public void setTemplateProperties(Map<String, String> templateProperties) {
			this.templateProperties = templateProperties;
		}

		public Map<String, String> getTemplateProperties() {
			return templateProperties;
		}

		public String[] getAccessUsers() {
			return accessUsers;
		}

		public void setAccessUsers(String[] accessUsers) {
			this.accessUsers = accessUsers;
		}

		public String getAccessExpression() {
			return accessExpression;
		}

		public void setAccessExpression(String accessExpression) {
			this.accessExpression = accessExpression;
		}

		public List<DockerSwarmSecret> getDockerSwarmSecrets() {
			return dockerSwarmSecrets;
		}

		public void setDockerSwarmSecrets(List<DockerSwarmSecret> dockerSwarmSecrets) {
			this.dockerSwarmSecrets = dockerSwarmSecrets;
		}

		public String getDockerRegistryDomain() {
			return dockerRegistryDomain;
		}

		public void setDockerRegistryDomain(String dockerRegistryDomain) {
			this.dockerRegistryDomain = dockerRegistryDomain;
		}

		public String getDockerRegistryUsername() {
			return dockerRegistryUsername;
		}

		public void setDockerRegistryUsername(String dockerRegistryUsername) {
			this.dockerRegistryUsername = dockerRegistryUsername;
		}

		public String getDockerRegistryPassword() {
			return dockerRegistryPassword;
		}

		public void setDockerRegistryPassword(String dockerRegistryPassword) {
			this.dockerRegistryPassword = dockerRegistryPassword;
		}
	}

	public static class TemplateGroup {

		private String id;
		private Map<String, String> properties;

		public Map<String, String> getProperties() {
			return properties;
		}

		public void setProperties(Map<String, String> properties) {
			this.properties = properties;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}
	}

}
