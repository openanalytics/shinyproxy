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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import org.apache.log4j.Logger;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerCertificates;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.LogsParam;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;

import eu.openanalytics.shinyproxy.ShinyProxyException;
import eu.openanalytics.shinyproxy.container.AbstractContainerBackend;
import eu.openanalytics.shinyproxy.container.IContainerProxy;
import eu.openanalytics.shinyproxy.services.AppService.ShinyApp;

public abstract class AbstractDockerBackend extends AbstractContainerBackend<DockerContainerProxy> {
	
	private static final String PROPERTY_PREFIX = "shiny.proxy.docker.";

	private Logger log = Logger.getLogger(AbstractDockerBackend.class);
	
	protected DockerClient dockerClient;
	
	@Override
	public void initialize() throws ShinyProxyException {
		super.initialize();
		
		DefaultDockerClient.Builder builder = null;
		
		try {
			builder = DefaultDockerClient.fromEnv();
		} catch (DockerCertificateException e) {
			throw new ShinyProxyException("Failed to initialize docker client", e);
		}

		String confCertPath = getProperty(PROPERTY_CERT_PATH);
		if (confCertPath != null) {
			try { 
				builder.dockerCertificates(DockerCertificates.builder().dockerCertPath(Paths.get(confCertPath)).build().orNull());
			} catch (DockerCertificateException e) {
				throw new ShinyProxyException("Failed to initialize docker client using certificates from " + confCertPath, e);
			}
		}

		String confUrl = getProperty(PROPERTY_URL);
		if (confUrl != null) builder.uri(confUrl);

		dockerClient = builder.build();
	}
	
	@Override
	protected DockerContainerProxy instantiateProxy() {
		return new DockerContainerProxy();
	}
	
	@Override
	public BiConsumer<File, File> getOutputAttacher(IContainerProxy proxy) {
		return (stdOut, stdErr) -> {
			try {
				LogStream logStream = dockerClient.logs(proxy.getContainerId(), LogsParam.follow(), LogsParam.stdout(), LogsParam.stderr());
				logStream.attach(new FileOutputStream(stdOut), new FileOutputStream(stdErr));
			} catch (IOException | InterruptedException | DockerException e) {
				log.error("Error while attaching to container output", e);
			}
		};
	}
	
	@Override
	protected Logger getLog() {
		return log;
	}
	
	@Override
	protected String getPropertyPrefix() {
		return PROPERTY_PREFIX;
	}
	
	protected List<String> getBindVolumes(ShinyApp app) {
		List<String> volumes = new ArrayList<>();

		if (app.getDockerVolumes() != null) {
			for (String vol: app.getDockerVolumes()) {
				volumes.add(vol);
			}
		}
		
		return volumes;
	}
}
