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

import com.spotify.docker.client.DockerClient.RemoveContainerParam;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.HostConfig.Builder;
import com.spotify.docker.client.messages.PortBinding;

import eu.openanalytics.shinyproxy.container.ContainerProxyRequest;
import eu.openanalytics.shinyproxy.util.Utils;

public class ShinyServerCustomBackend extends AbstractDockerBackend {

	@Override
	protected void prepareProxy(DockerContainerProxy proxy, ContainerProxyRequest request) throws Exception {
		
	}
	
	@Override
	protected void doStartProxy(DockerContainerProxy proxy) throws Exception {

	}
	
	@Override
	protected void doStopProxy(DockerContainerProxy proxy) throws Exception {
		
	}

	@Override
	protected void calculateTargetURL(DockerContainerProxy proxy) throws Exception {

	}
}
