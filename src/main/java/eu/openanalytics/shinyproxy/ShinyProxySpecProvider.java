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

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.spec.AccessControl;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.model.spec.DockerSwarmSecret;
import eu.openanalytics.containerproxy.model.spec.Parameters;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;
import eu.openanalytics.shinyproxy.runtimevalues.MaxInstancesKey;
import eu.openanalytics.shinyproxy.runtimevalues.ShinyForceFullReloadKey;
import eu.openanalytics.shinyproxy.runtimevalues.WebSocketReconnectionModeKey;
import eu.openanalytics.shinyproxy.runtimevalues.WebsocketReconnectionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
	private static final String PROP_DEFAULT_ALWAYS_SWITCH_INSTANCE = "proxy.default-always-switch-instance";

	private List<ProxySpec> specs = new ArrayList<>();

	private List<TemplateGroup> templateGroups = new ArrayList<>();

	private static Environment environment;

	private Integer defaultMaxInstances;

	private Boolean defaultAlwaysSwitchInstance;

	@Autowired
	public void setEnvironment(Environment env){
		ShinyProxySpecProvider.environment = env;
	}

	@PostConstruct
	public void afterPropertiesSet() {
		this.specs.stream().collect(Collectors.groupingBy(ProxySpec::getId)).forEach((id, duplicateSpecs) -> {
			if (duplicateSpecs.size() > 1) throw new IllegalArgumentException(String.format("Configuration error: spec with id '%s' is defined multiple times", id));
		});
		defaultMaxInstances = environment.getProperty(PROP_DEFAULT_MAX_INSTANCES, Integer.class, 1);
		defaultAlwaysSwitchInstance = environment.getProperty(PROP_DEFAULT_ALWAYS_SWITCH_INSTANCE, Boolean.class, false);
	}

	public List<ProxySpec> getSpecs() {
		return new ArrayList<>(specs);
	}

	public ProxySpec getSpec(String id) {
		if (id == null || id.isEmpty()) return null;
		return specs.stream().filter(s -> id.equals(s.getId())).findAny().orElse(null);
	}

	public void setSpecs(List<ShinyProxySpec> specs) {
		this.specs = specs.stream().map(ShinyProxySpec::getProxySpec).collect(Collectors.toList());
	}

	public void setTemplateGroups(List<TemplateGroup> templateGroups) {
		this.templateGroups = templateGroups;
	}

	public List<TemplateGroup> getTemplateGroups() {
		return templateGroups;
	}

	public List<RuntimeValue> getRuntimeValues(ProxySpec proxy) {
		List<RuntimeValue> runtimeValues = new ArrayList<>();

		WebsocketReconnectionMode webSocketReconnectionMode = proxy.getSpecExtension(ShinyProxySpecExtension.class).getWebsocketReconnectionMode();
		if (webSocketReconnectionMode == null) {
			runtimeValues.add(new RuntimeValue(WebSocketReconnectionModeKey.inst, environment.getProperty("proxy.default-websocket-reconnection-mode", WebsocketReconnectionMode.class, WebsocketReconnectionMode.None)));
		} else {
			runtimeValues.add(new RuntimeValue(WebSocketReconnectionModeKey.inst, webSocketReconnectionMode));
		}

		runtimeValues.add(new RuntimeValue(MaxInstancesKey.inst, getMaxInstancesForSpec(proxy)));
		runtimeValues.add(new RuntimeValue(ShinyForceFullReloadKey.inst, getShinyForceFullReload(proxy)));

		return runtimeValues;
	}

	public Integer getMaxInstancesForSpec(ProxySpec proxySpec) {
		// TODO support SpEL
		Integer maxInstances = proxySpec.getSpecExtension(ShinyProxySpecExtension.class).getMaxInstances();
		if (maxInstances != null) {
            return maxInstances;
		}
		return defaultMaxInstances;
	}

	public Map<String, Integer> getMaxInstances() {
		Map<String, Integer> result = new HashMap<>();

		// TODO support SpEL
		for (ProxySpec proxySpec: getSpecs()) {
			Integer maxInstances = proxySpec.getSpecExtension(ShinyProxySpecExtension.class).getMaxInstances();
			if (maxInstances != null) {
				result.put(proxySpec.getId(), maxInstances);
			} else {
				result.put(proxySpec.getId(), defaultMaxInstances);
			}
		}

		return result;
	}

	public Boolean getShinyForceFullReload(ProxySpec proxySpec) {
		Boolean shinyProxyForceFullReload = proxySpec.getSpecExtension(ShinyProxySpecExtension.class).getShinyForceFullReload();
		if (shinyProxyForceFullReload != null) {
			return shinyProxyForceFullReload;
		}
		return false;
	}

	public Boolean getShinyForceFullReload(String specId) {
		return getShinyForceFullReload(getSpec(specId));
	}

	public Boolean getHideNavbarOnMainPageLink(ProxySpec proxySpec) {
		Boolean hideNavbarOnMainPageLink = proxySpec.getSpecExtension(ShinyProxySpecExtension.class).getHideNavbarOnMainPageLink();
		if (hideNavbarOnMainPageLink != null) {
			return hideNavbarOnMainPageLink;
		}
		return false;
	}
	
	public Boolean getAlwaysShowSwitchInstance(ProxySpec proxySpec) {
		Boolean alwaysShowSwitchInstance = proxySpec.getSpecExtension(ShinyProxySpecExtension.class).getAlwaysShowSwitchInstance();
		if (alwaysShowSwitchInstance != null) {
			return alwaysShowSwitchInstance;
		}
		return defaultAlwaysSwitchInstance;
	}

	public void postProcessRecoveredProxy(Proxy proxy) {
		proxy.addRuntimeValues(getRuntimeValues(proxy.getSpec()));
	}

	public static class ShinyProxySpec {

		private final ProxySpec proxySpec;
		private final ContainerSpec containerSpec;
		private final AccessControl accessControl;

		public ShinyProxySpec() {
			proxySpec = new ProxySpec();
			containerSpec = new ContainerSpec();
			accessControl = new AccessControl();

			Map<String, Integer> portMapping = new HashMap<>();
			portMapping.put("default", 3838);
			containerSpec.setPortMapping(portMapping);
			proxySpec.setContainerSpecs(Collections.singletonList(containerSpec));
			proxySpec.setAccessControl(accessControl);
		}

		public String getId() {
			return proxySpec.getId();
		}

		public void setId(String id) {
			proxySpec.setId(id);
		}

		public String getDisplayName() {
			return proxySpec.getDisplayName();
		}

		public void setDisplayName(String displayName) {
			proxySpec.setDisplayName(displayName);
		}

		public String getDescription() {
			return proxySpec.getDescription();
		}

		public void setDescription(String description) {
			proxySpec.setDescription(description);
		}

		public String getLogoURL() {
			return proxySpec.getLogoURL();
		}

		public void setLogoURL(String logoURL) {
			proxySpec.setLogoURL(logoURL);
		}

		public String getContainerImage() {
			return containerSpec.getImage();
		}

		public void setContainerImage(String containerImage) {
			containerSpec.setImage(containerImage);
		}

		public String[] getContainerCmd() {
			return containerSpec.getCmd();
		}

		public void setContainerCmd(String[] containerCmd) {
			containerSpec.setCmd(containerCmd);
		}

		public Map<String, String> getContainerEnv() {
			return containerSpec.getEnv();
		}

		public void setContainerEnv(Map<String, String> containerEnv) {
			containerSpec.setEnv(containerEnv);
		}

		public String getContainerEnvFile() {
			return containerSpec.getEnvFile();
		}

		public void setContainerEnvFile(String containerEnvFile) {
			containerSpec.setEnvFile(containerEnvFile);
		}

		public String getContainerNetwork() {
			return containerSpec.getNetwork();
		}

		public void setContainerNetwork(String containerNetwork) {
			containerSpec.setNetwork(containerNetwork);
		}

		public String[] getContainerNetworkConnections() {
			return containerSpec.getNetworkConnections();
		}

		public void setContainerNetworkConnections(String[] containerNetworkConnections) {
			containerSpec.setNetworkConnections(containerNetworkConnections);
		}

		public String[] getContainerDns() {
			return containerSpec.getDns();
		}

		public void setContainerDns(String[] containerDns) {
			containerSpec.setDns(containerDns);
		}

		public String[] getContainerVolumes() {
			return containerSpec.getVolumes();
		}

		public void setContainerVolumes(String[] containerVolumes) {
			containerSpec.setVolumes(containerVolumes);
		}

		public String getContainerMemoryRequest() {
			return containerSpec.getMemoryRequest();
		}

		public void setContainerMemoryRequest(String containerMemoryRequest) {
			containerSpec.setMemoryRequest(containerMemoryRequest);
		}

		public String getContainerMemoryLimit() {
			return containerSpec.getMemoryLimit();
		}

		public void setContainerMemoryLimit(String containerMemoryLimit) {
			containerSpec.setMemoryLimit(containerMemoryLimit);
		}

		public String getContainerCpuRequest() {
			return containerSpec.getCpuRequest();
		}

		public void setContainerCpuRequest(String containerCpuRequest) {
			containerSpec.setCpuRequest(containerCpuRequest);
		}

		public String getContainerCpuLimit() {
			return containerSpec.getCpuLimit();
		}

		public void setContainerCpuLimit(String containerCpuLimit) {
			containerSpec.setCpuLimit(containerCpuLimit);
		}

		public boolean isContainerPrivileged() {
			return containerSpec.isPrivileged();
		}

		public void setContainerPrivileged(boolean containerPrivileged) {
			containerSpec.setPrivileged(containerPrivileged);
		}

		public Map<String, String> getLabels() {
			return containerSpec.getLabels();
		}

		public void setLabels(Map<String, String> labels) {
			containerSpec.setLabels(labels);
		}

		public int getPort() {
			return containerSpec.getPortMapping().get("default");
		}

		public void setPort(int port) {
			containerSpec.getPortMapping().put("default", port);
		}

		public String[] getAccessGroups() {
			return accessControl.getGroups();
		}

		public void setAccessGroups(String[] accessGroups) {
			accessControl.setGroups(accessGroups);
		}

		public String getTargetPath() {
			return containerSpec.getTargetPath();
		}

		public void setTargetPath(String targetPath) {
			containerSpec.setTargetPath(targetPath);
		}

		public String[] getAccessUsers() {
			return accessControl.getUsers();
		}

		public void setAccessUsers(String[] accessUsers) {
			accessControl.setUsers(accessUsers);
		}

		public String getAccessExpression() {
			return accessControl.getExpression();
		}

		public void setAccessExpression(String accessExpression) {
			accessControl.setExpression(accessExpression);
		}

		public List<DockerSwarmSecret> getDockerSwarmSecrets() {
			return containerSpec.getDockerSwarmSecrets();
		}

		public void setDockerSwarmSecrets(List<DockerSwarmSecret> dockerSwarmSecrets) {
			containerSpec.setDockerSwarmSecrets(dockerSwarmSecrets);
		}

		public String getDockerRegistryDomain() {
			return containerSpec.getDockerRegistryDomain();
		}

		public void setDockerRegistryDomain(String dockerRegistryDomain) {
			containerSpec.setDockerRegistryDomain(dockerRegistryDomain);
		}

		public String getDockerRegistryUsername() {
			return containerSpec.getDockerRegistryUsername();
		}

		public void setDockerRegistryUsername(String dockerRegistryUsername) {
			containerSpec.setDockerRegistryUsername(dockerRegistryUsername);
		}

		public String getDockerRegistryPassword() {
			return containerSpec.getDockerRegistryPassword();
		}

		public void setDockerRegistryPassword(String dockerRegistryPassword) {
			containerSpec.setDockerRegistryPassword(dockerRegistryPassword);
		}

        public Parameters getParameters() {
            return proxySpec.getParameters();
        }

        public void setParameters(Parameters parameters) {
			proxySpec.setParameters(parameters);
        }

		public String getMaxLifetime() {
			return proxySpec.getMaxLifeTime();
		}

		public void setMaxLifetime(String maxLifetime) {
			proxySpec.setMaxLifeTime(maxLifetime);
		}

		public Boolean getStopOnLogout() {
			return proxySpec.stopOnLogout();
		}

		public void setStopOnLogout(Boolean stopOnLogout) {
			proxySpec.setStopOnLogout(stopOnLogout);
		}

		public String getHeartbeatTimeout() {
			return proxySpec.getHeartbeatTimeout();
		}

		public void setHeartbeatTimeout(String heartbeatTimeout) {
			proxySpec.setHeartbeatTimeout(heartbeatTimeout);
		}

		public ProxySpec getProxySpec() {
			return proxySpec;
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
