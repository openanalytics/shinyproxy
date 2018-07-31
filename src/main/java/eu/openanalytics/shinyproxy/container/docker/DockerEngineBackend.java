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
package eu.openanalytics.shinyproxy.container.docker;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.log4j.Logger;

import com.spotify.docker.client.DockerClient.RemoveContainerParam;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.HostConfig.Builder;
import com.spotify.docker.client.messages.PortBinding;

import eu.openanalytics.shinyproxy.container.ContainerProxyRequest;
import eu.openanalytics.shinyproxy.controllers.BaseController;
import eu.openanalytics.shinyproxy.util.Utils;

public class DockerEngineBackend extends AbstractDockerBackend {
	
	public static Logger logger = Logger.getLogger(DockerEngineBackend.class);

	@Override
	protected void prepareProxy(DockerContainerProxy proxy, ContainerProxyRequest request) throws Exception {
		Builder hostConfigBuilder = HostConfig.builder();
		
		List<PortBinding> portBindings = Collections.emptyList();
		if (proxy.getPort() > 0) portBindings = Collections.singletonList(PortBinding.of("0.0.0.0", proxy.getPort()));
		hostConfigBuilder.portBindings(Collections.singletonMap(String.valueOf(getAppPort(proxy)), portBindings));
		
		Optional.ofNullable(Utils.memoryToBytes(request.app.getDockerMemory())).ifPresent(l -> hostConfigBuilder.memory(l));
		Optional.ofNullable(request.app.getDockerNetwork()).ifPresent(n -> hostConfigBuilder.networkMode(request.app.getDockerNetwork()));
		
		hostConfigBuilder.dns(request.app.getDockerDns());
		hostConfigBuilder.binds(getBindVolumes(request.app));
		hostConfigBuilder.privileged(Boolean.valueOf(getProperty(PROPERTY_PRIVILEGED, request.app, DEFAULT_PRIVILEGED)));
		
		ContainerConfig containerConfig = ContainerConfig.builder()
			    .hostConfig(hostConfigBuilder.build())
			    .image(request.app.getDockerImage())
			    .exposedPorts(String.valueOf(getAppPort(proxy)))
			    .cmd(request.app.getDockerCmd())
			    .env(buildEnv(request.userId, request.app))
			    .build();
		
		//ContainerCreation container = dockerClient.createContainer(containerConfig);
		proxy.setContainerId("123123123"/*container.id()*/);
	}
	
	@Override
	protected void doStartProxy(DockerContainerProxy proxy) throws Exception {
		logger.info("doStartProxy started");
		if (proxy.getApp().getDockerNetworkConnections() != null) {
			for (String networkConnection: proxy.getApp().getDockerNetworkConnections()) {
				logger.info("doStartProxy : networkConnection=" + networkConnection);
				
				dockerClient.connectToNetwork(proxy.getContainerId(), networkConnection);
			}
		}
		
		//dockerClient.startContainer(proxy.getContainerId());
		
		//ContainerInfo info = dockerClient.inspectContainer(proxy.getContainerId());
		proxy.setName(proxy.getApp().getName()/*info.name().substring(1)*/);
		
		logger.info("doStartProxy finished");
	}
	
	@Override
	protected void doStopProxy(DockerContainerProxy proxy) throws Exception {
		logger.info("doStopProxy started");
		if (proxy.getApp() != null && proxy.getApp().getDockerNetworkConnections() != null) {
			for (String networkConnection: proxy.getApp().getDockerNetworkConnections()) {
				dockerClient.disconnectFromNetwork(proxy.getContainerId(), networkConnection);
			}
		}
		logger.info("doStopProxy finished");
		//dockerClient.removeContainer(proxy.getContainerId(), RemoveContainerParam.forceKill());
	}

	@Override
	protected void calculateTargetURL(DockerContainerProxy proxy) throws Exception {
		logger.info("calculateTargetURL started");
		super.calculateTargetURL(proxy);
		
		// For internal networks, DNS resolution by name only works with custom names.
		// See comments on https://github.com/docker/for-win/issues/1009
		/*if (proxy.getTarget().contains(proxy.getName())) {
			ContainerInfo info = dockerClient.inspectContainer(proxy.getContainerId());
			proxy.setTarget(proxy.getTarget().replace(proxy.getName(), info.config().hostname()));
		}*/
		logger.info("calculateTargetURL finished");
	}
}
