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

import java.util.Arrays;
import java.util.Optional;
import java.util.Random;

import org.apache.commons.codec.binary.Hex;

import com.spotify.docker.client.messages.mount.Mount;
import com.spotify.docker.client.messages.swarm.ContainerSpec;
import com.spotify.docker.client.messages.swarm.DnsConfig;
import com.spotify.docker.client.messages.swarm.EndpointSpec;
import com.spotify.docker.client.messages.swarm.NetworkAttachmentConfig;
import com.spotify.docker.client.messages.swarm.PortConfig;
import com.spotify.docker.client.messages.swarm.ServiceSpec;
import com.spotify.docker.client.messages.swarm.Task;
import com.spotify.docker.client.messages.swarm.TaskSpec;

import eu.openanalytics.shinyproxy.ShinyProxyException;
import eu.openanalytics.shinyproxy.container.ContainerProxyRequest;
import eu.openanalytics.shinyproxy.util.Utils;

public class DockerSwarmBackend extends AbstractDockerBackend {
	
	private Random rng = new Random();
	
	@Override
	public void initialize() throws ShinyProxyException {
		super.initialize();
		String swarmId = null;
		try {
			swarmId = dockerClient.inspectSwarm().id();
		} catch (Exception e) {}
		if (swarmId == null) throw new ShinyProxyException("Container backend is not a Docker Swarm");
	}
	
	@Override
	protected void doCreateProxy(DockerContainerProxy proxy, ContainerProxyRequest request) throws Exception {
		// Generate a unique random name for the service.
		byte[] nameBytes = new byte[20];
		rng.nextBytes(nameBytes);
		proxy.setName(Hex.encodeHexString(nameBytes));
	}

	@Override
	protected void doStartProxy(DockerContainerProxy proxy) throws Exception {
		Mount[] mounts = getBindVolumes(proxy.getApp()).stream()
				.map(b -> b.split(":"))
				.map(fromTo -> Mount.builder().source(fromTo[0]).target(fromTo[1]).type("bind").build())
				.toArray(i -> new Mount[i]);

		ContainerSpec containerSpec = ContainerSpec.builder()
				.image(proxy.getApp().getDockerImage())
				.command(proxy.getApp().getDockerCmd())
				.env(buildEnv(proxy.getUserId(), proxy.getApp()))
				.dnsConfig(DnsConfig.builder().nameServers(proxy.getApp().getDockerDns()).build())
				.mounts(mounts)
				.build();

		NetworkAttachmentConfig[] networks = Arrays
				.stream(Optional.ofNullable(proxy.getApp().getDockerNetworkConnections()).orElse(new String[0]))
				.map(n -> NetworkAttachmentConfig.builder().target(n).build())
				.toArray(i -> new NetworkAttachmentConfig[i]);

		ServiceSpec.Builder serviceSpecBuilder = ServiceSpec.builder()
				.networks(networks)
				.name(proxy.getName())
				.taskTemplate(TaskSpec.builder()
						.containerSpec(containerSpec)
						.build());
		
		if (proxy.getPort() > 0) {
			serviceSpecBuilder.endpointSpec(EndpointSpec.builder()
					.ports(PortConfig.builder().publishedPort(proxy.getPort()).targetPort(getAppPort(proxy)).build())
					.build());
		}
		
		proxy.setServiceId(dockerClient.createService(serviceSpecBuilder.build()).id());

		boolean containerFound = Utils.retry(i -> {
			try {
				Task serviceTask = dockerClient
						.listTasks(Task.Criteria.builder().serviceName(proxy.getName()).build())
						.stream().findAny().orElseThrow(() -> new IllegalStateException("Swarm service has no tasks"));
				proxy.setContainerId(serviceTask.status().containerStatus().containerId());
			} catch (Exception e) {
				throw new RuntimeException("Failed to inspect swarm service tasks");
			}
			return (proxy.getContainerId() != null);
		}, 10, 2000);
		if (!containerFound) {
			dockerClient.removeService(proxy.getServiceId());
			throw new IllegalStateException("Swarm container did not start in time");
		}
	}

	@Override
	protected void doStopProxy(DockerContainerProxy proxy) throws Exception {
		dockerClient.removeService(proxy.getServiceId());
	}

}
