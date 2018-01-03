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
package eu.openanalytics.shinyproxy.container.docker;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.spotify.docker.client.DockerClient.RemoveContainerParam;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.HostConfig.Builder;
import com.spotify.docker.client.messages.PortBinding;

import eu.openanalytics.shinyproxy.container.ContainerProxyRequest;
import eu.openanalytics.shinyproxy.util.Utils;

public class DockerEngineBackend extends AbstractDockerBackend {

	@Override
	protected void doCreateProxy(DockerContainerProxy proxy, ContainerProxyRequest request) throws Exception {
		Builder hostConfigBuilder = HostConfig.builder();
		
		List<PortBinding> portBindings = Collections.emptyList();
		if (proxy.getPort() > 0) portBindings = Collections.singletonList(PortBinding.of("0.0.0.0", proxy.getPort()));
		hostConfigBuilder.portBindings(Collections.singletonMap(String.valueOf(getAppPort(proxy)), portBindings));
		
		Optional.ofNullable(Utils.memoryToBytes(request.app.getDockerMemory())).ifPresent(l -> hostConfigBuilder.memory(l));
		Optional.ofNullable(request.app.getDockerNetwork()).ifPresent(n -> hostConfigBuilder.networkMode(request.app.getDockerNetwork()));
		
		hostConfigBuilder.dns(request.app.getDockerDns());
		hostConfigBuilder.binds(getBindVolumes(request.app));
		
		ContainerConfig containerConfig = ContainerConfig.builder()
			    .hostConfig(hostConfigBuilder.build())
			    .image(request.app.getDockerImage())
			    .exposedPorts(String.valueOf(getAppPort(proxy)))
			    .cmd(request.app.getDockerCmd())
			    .env(buildEnv(request.userId, request.app))
			    .build();
		
		ContainerCreation container = dockerClient.createContainer(containerConfig);
		proxy.setContainerId(container.id());
	}
	
	@Override
	protected void doStartProxy(DockerContainerProxy proxy) throws Exception {
		if (proxy.getApp().getDockerNetworkConnections() != null) {
			for (String networkConnection: proxy.getApp().getDockerNetworkConnections()) {
				dockerClient.connectToNetwork(proxy.getContainerId(), networkConnection);
			}
		}
		
		dockerClient.startContainer(proxy.getContainerId());
		
		ContainerInfo info = dockerClient.inspectContainer(proxy.getContainerId());
		proxy.setName(info.name().substring(1));
	}
	
	@Override
	protected void doStopProxy(DockerContainerProxy proxy) throws Exception {
		if (proxy.getApp() != null && proxy.getApp().getDockerNetworkConnections() != null) {
			for (String networkConnection: proxy.getApp().getDockerNetworkConnections()) {
				dockerClient.disconnectFromNetwork(proxy.getContainerId(), networkConnection);
			}
		}
		dockerClient.removeContainer(proxy.getContainerId(), RemoveContainerParam.forceKill());
	}

}