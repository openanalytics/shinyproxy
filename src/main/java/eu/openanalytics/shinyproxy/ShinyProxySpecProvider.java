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

import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.spec.AccessControl;
import eu.openanalytics.containerproxy.model.spec.ContainerSpec;
import eu.openanalytics.containerproxy.model.spec.DockerSwarmSecret;
import eu.openanalytics.containerproxy.model.spec.Parameters;
import eu.openanalytics.containerproxy.model.spec.PortMapping;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.UserService;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionContext;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import eu.openanalytics.containerproxy.spec.expression.SpelField;
import eu.openanalytics.shinyproxy.runtimevalues.ShinyForceFullReloadKey;
import eu.openanalytics.shinyproxy.runtimevalues.WebSocketReconnectionModeKey;
import eu.openanalytics.shinyproxy.runtimevalues.WebsocketReconnectionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
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

	private String defaultMaxInstances;

	private Boolean defaultAlwaysSwitchInstance;

	@Inject
	private SpecExpressionResolver expressionResolver;

	@Inject
	@Lazy
	private UserService userService;

	@Autowired
	public void setEnvironment(Environment env){
		ShinyProxySpecProvider.environment = env;
	}

	@PostConstruct
	public void afterPropertiesSet() {
		this.specs.stream().collect(Collectors.groupingBy(ProxySpec::getId)).forEach((id, duplicateSpecs) -> {
			if (duplicateSpecs.size() > 1) throw new IllegalArgumentException(String.format("Configuration error: spec with id '%s' is defined multiple times", id));
		});
		defaultMaxInstances = environment.getProperty(PROP_DEFAULT_MAX_INSTANCES, String.class, "1");
		defaultAlwaysSwitchInstance = environment.getProperty(PROP_DEFAULT_ALWAYS_SWITCH_INSTANCE, Boolean.class, false);
		specs.forEach(ProxySpec::setContainerIndex);
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

		runtimeValues.add(new RuntimeValue(ShinyForceFullReloadKey.inst, getShinyForceFullReload(proxy)));

		return runtimeValues;
	}

	public Integer getMaxInstancesForSpec(ProxySpec proxySpec) {
		Authentication user = userService.getCurrentAuth();
		SpecExpressionContext context = SpecExpressionContext.create(
				user,
				user.getPrincipal(),
				user.getCredentials());

		Integer maxInstances = proxySpec.getSpecExtension(ShinyProxySpecExtension.class).getMaxInstances().resolve(expressionResolver, context).getValueOrNull();
		if (maxInstances != null) {
            return maxInstances;
		}
		return expressionResolver.evaluateToInteger(defaultMaxInstances, context);
	}

	public Map<String, Integer> getMaxInstances() {
		Authentication user = userService.getCurrentAuth();
		SpecExpressionContext context = SpecExpressionContext.create(
				user,
				user.getPrincipal(),
				user.getCredentials());

		Map<String, Integer> result = new HashMap<>();

		Integer resolvedDefault = expressionResolver.evaluateToInteger(defaultMaxInstances, context);

		for (ProxySpec proxySpec: getSpecs()) {
			Integer maxInstances = proxySpec.getSpecExtension(ShinyProxySpecExtension.class).getMaxInstances().resolve(expressionResolver, context).getValueOrNull();
			if (maxInstances != null) {
				result.put(proxySpec.getId(), maxInstances);
			} else {
				result.put(proxySpec.getId(), resolvedDefault);
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

	public static class ShinyProxySpec {

		private final ProxySpec.ProxySpecBuilder proxySpec;
		private final ContainerSpec.ContainerSpecBuilder containerSpec;
		private final AccessControl accessControl;
		private final PortMapping.PortMappingBuilder defaultPortMapping;
		private List<PortMapping> additionalPortMappings = new ArrayList<>();

		public ShinyProxySpec() {
			proxySpec = ProxySpec.builder();
			containerSpec = ContainerSpec.builder();
			accessControl = new AccessControl();
			defaultPortMapping = PortMapping.builder().name("default").port(3838);
			proxySpec.accessControl(accessControl);
		}

		public String getId() {
			return proxySpec.build().getId();
		}

		public void setId(String id) {
			proxySpec.id(id);
		}

		public String getDisplayName() {
			return proxySpec.build().getDisplayName();
		}

		public void setDisplayName(String displayName) {
			proxySpec.displayName(displayName);
		}

		public String getDescription() {
			return proxySpec.build().getDescription();
		}

		public void setDescription(String description) {
			proxySpec.description(description);
		}

		public String getLogoURL() {
			return proxySpec.build().getLogoURL();
		}

		public void setLogoURL(String logoURL) {
			proxySpec.logoURL(logoURL);
		}

		public SpelField.String getContainerImage() {
			return containerSpec.build().getImage();
		}

		public void setContainerImage(SpelField.String containerImage) {
			containerSpec.image(containerImage);
		}

		public SpelField.StringList getContainerCmd() {
			return containerSpec.build().getCmd();
		}

		public void setContainerCmd(List<String> containerCmd) {
			containerSpec.cmd(new SpelField.StringList(containerCmd));
		}

		public SpelField.StringMap getContainerEnv() {
			return containerSpec.build().getEnv();
		}

		public void setContainerEnv(Map<String, String> containerEnv) {
			containerSpec.env(new SpelField.StringMap(containerEnv));
		}

		public SpelField.String getContainerEnvFile() {
			return containerSpec.build().getEnvFile();
		}

		public void setContainerEnvFile(SpelField.String containerEnvFile) {
			containerSpec.envFile(containerEnvFile);
		}

		public SpelField.String getContainerNetwork() {
			return containerSpec.build().getNetwork();
		}

		public void setContainerNetwork(SpelField.String containerNetwork) {
			containerSpec.network(containerNetwork);
		}

		public SpelField.StringList getContainerNetworkConnections() {
			return containerSpec.build().getNetworkConnections();
		}

		public void setContainerNetworkConnections(List<String> containerNetworkConnections) {
			containerSpec.networkConnections(new SpelField.StringList(containerNetworkConnections));
		}

		public SpelField.StringList getContainerDns() {
			return containerSpec.build().getDns();
		}

		public void setContainerDns(List<String> containerDns) {
			containerSpec.dns(new SpelField.StringList(containerDns));
		}

		public SpelField.StringList getContainerVolumes() {
			return containerSpec.build().getVolumes();
		}

		public void setContainerVolumes(List<String> containerVolumes) {
			containerSpec.volumes(new SpelField.StringList(containerVolumes));
		}

		public SpelField.String getContainerMemoryRequest() {
			return containerSpec.build().getMemoryRequest();
		}

		public void setContainerMemoryRequest(SpelField.String containerMemoryRequest) {
			containerSpec.memoryRequest(containerMemoryRequest);
		}

		public SpelField.String getContainerMemoryLimit() {
			return containerSpec.build().getMemoryLimit();
		}

		public void setContainerMemoryLimit(SpelField.String containerMemoryLimit) {
			containerSpec.memoryLimit(containerMemoryLimit);
		}

		public SpelField.String getContainerCpuRequest() {
			return containerSpec.build().getCpuRequest();
		}

		public void setContainerCpuRequest(SpelField.String containerCpuRequest) {
			containerSpec.cpuRequest(containerCpuRequest);
		}

		public SpelField.String getContainerCpuLimit() {
			return containerSpec.build().getCpuLimit();
		}

		public void setContainerCpuLimit(SpelField.String containerCpuLimit) {
			containerSpec.cpuLimit(containerCpuLimit);
		}

		public boolean isContainerPrivileged() {
			return containerSpec.build().isPrivileged();
		}

		public void setContainerPrivileged(boolean containerPrivileged) {
			containerSpec.privileged(containerPrivileged);
		}

		public SpelField.StringMap getLabels() {
			return containerSpec.build().getLabels();
		}

		public void setLabels(Map<String, String> labels) {
			containerSpec.labels(new SpelField.StringMap(labels));
		}

		public int getPort() {
			return defaultPortMapping.build().getPort();
		}

		public void setPort(int port) {
			defaultPortMapping.port(port);
		}

		public String[] getAccessGroups() {
			return accessControl.getGroups();
		}

		public void setAccessGroups(String[] accessGroups) {
			accessControl.setGroups(accessGroups);
		}

		public SpelField.String getTargetPath() {
			return defaultPortMapping.build().getTargetPath();
		}

		public void setTargetPath(SpelField.String targetPath) {
			defaultPortMapping.targetPath(targetPath);
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
			return containerSpec.build().getDockerSwarmSecrets();
		}

		public void setDockerSwarmSecrets(List<DockerSwarmSecret> dockerSwarmSecrets) {
			containerSpec.dockerSwarmSecrets(dockerSwarmSecrets);
		}

		public String getDockerRegistryDomain() {
			return containerSpec.build().getDockerRegistryDomain();
		}

		public void setDockerRegistryDomain(String dockerRegistryDomain) {
			containerSpec.dockerRegistryDomain(dockerRegistryDomain);
		}

		public String getDockerRegistryUsername() {
			return containerSpec.build().getDockerRegistryUsername();
		}

		public void setDockerRegistryUsername(String dockerRegistryUsername) {
			containerSpec.dockerRegistryUsername(dockerRegistryUsername);
		}

		public String getDockerRegistryPassword() {
			return containerSpec.build().getDockerRegistryPassword();
		}

		public void setDockerRegistryPassword(String dockerRegistryPassword) {
			containerSpec.dockerRegistryPassword(dockerRegistryPassword);
		}

        public Parameters getParameters() {
            return proxySpec.build().getParameters();
        }

        public void setParameters(Parameters parameters) {
			proxySpec.parameters(parameters);
        }

		public SpelField.Long getMaxLifetime() {
			return proxySpec.build().getMaxLifeTime();
		}

		public void setMaxLifetime(SpelField.Long maxLifetime) {
			proxySpec.maxLifeTime(maxLifetime);
		}

		public Boolean getStopOnLogout() {
			return proxySpec.build().getStopOnLogout();
		}

		public void setStopOnLogout(Boolean stopOnLogout) {
			proxySpec.stopOnLogout(stopOnLogout);
		}

		public SpelField.Long getHeartbeatTimeout() {
			return proxySpec.build().getHeartbeatTimeout();
		}

		public void setHeartbeatTimeout(SpelField.Long heartbeatTimeout) {
			proxySpec.heartbeatTimeout(heartbeatTimeout);
		}

		public List<PortMapping> getAdditionalPortMappings() {
			return additionalPortMappings;
		}

		public void setAdditionalPortMappings(List<PortMapping> additionalPortMappings) {
			this.additionalPortMappings = additionalPortMappings;
		}

		public ProxySpec getProxySpec() {
			additionalPortMappings.add(defaultPortMapping.build());
			containerSpec.portMapping(additionalPortMappings);
			proxySpec.containerSpecs(Collections.singletonList(containerSpec.build()));
			return proxySpec.build();
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
