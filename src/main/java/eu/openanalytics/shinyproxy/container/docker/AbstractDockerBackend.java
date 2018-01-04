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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import javax.inject.Inject;

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
import eu.openanalytics.shinyproxy.container.ContainerProxyRequest;
import eu.openanalytics.shinyproxy.container.ContainerProxyStatus;
import eu.openanalytics.shinyproxy.container.IContainerProxy;
import eu.openanalytics.shinyproxy.services.AppService.ShinyApp;
import eu.openanalytics.shinyproxy.services.UserService;
import eu.openanalytics.shinyproxy.util.Utils;

public abstract class AbstractDockerBackend extends AbstractContainerBackend {
	
	protected static final String PROPERTY_PREFIX = "shiny.proxy.docker.";

	private Logger log = Logger.getLogger(AbstractDockerBackend.class);
	private Set<Integer> occupiedPorts = Collections.synchronizedSet(new HashSet<>());
	private boolean useInternalDockerNetwork;
	
	protected DockerClient dockerClient;
	
	@Inject
	protected UserService userService;
	
	@Override
	public void initialize() throws ShinyProxyException {
		DefaultDockerClient.Builder builder = null;
		
		try {
			builder = DefaultDockerClient.fromEnv();
		} catch (DockerCertificateException e) {
			throw new ShinyProxyException("Failed to initialize docker client", e);
		}

		String confCertPath = environment.getProperty(PROPERTY_PREFIX + "cert-path");
		if (confCertPath != null) {
			try { 
				builder.dockerCertificates(DockerCertificates.builder().dockerCertPath(Paths.get(confCertPath)).build().orNull());
			} catch (DockerCertificateException e) {
				throw new ShinyProxyException("Failed to initialize docker client using certificates from " + confCertPath, e);
			}
		}

		String confUrl = environment.getProperty(PROPERTY_PREFIX + "url");
		if (confUrl != null) {
			builder.uri(confUrl);
		}

		dockerClient = builder.build();
		
		// If ShinyProxy runs as a container itself, some things like port publishing can be omitted.
		useInternalDockerNetwork = Boolean.valueOf(environment.getProperty(PROPERTY_PREFIX + "internal-networking"));
	}
	
	@Override
	public IContainerProxy createProxy(ContainerProxyRequest request) throws ShinyProxyException {
		DockerContainerProxy proxy = new DockerContainerProxy();
		proxy.setStatus(ContainerProxyStatus.New);
		proxy.setUserId(request.userId);
		proxy.setApp(request.app);

		try {
			allocatePort(proxy);
			doCreateProxy(proxy, request);
		} catch (Exception e) {
			releasePort(proxy);
			if (e instanceof ShinyProxyException) throw (ShinyProxyException) e;
			throw new ShinyProxyException("Failed to create container", e);
		}

		return proxy;
	}

	protected abstract void doCreateProxy(DockerContainerProxy proxy, ContainerProxyRequest request) throws Exception;
	
	@Override
	public void startProxy(IContainerProxy proxy) throws ShinyProxyException {
		DockerContainerProxy dockerProxy = (DockerContainerProxy) proxy;
		dockerProxy.setStatus(ContainerProxyStatus.Starting);
		
		try {
			doStartProxy(dockerProxy);
			calculateTargetURL(dockerProxy);
		} catch (Throwable t) {
			releasePort(dockerProxy);
			throw new ShinyProxyException("Failed to start container", t);
		}
		
		if (!testProxy(dockerProxy)) {
			stopProxy(proxy);
			throw new ShinyProxyException("Container did not respond in time");
		}
		
		dockerProxy.setStartupTimestamp(System.currentTimeMillis());
		dockerProxy.setStatus(ContainerProxyStatus.Up);
	}
	
	protected abstract void doStartProxy(DockerContainerProxy proxy) throws Exception;

	@Override
	public void stopProxy(IContainerProxy proxy) throws ShinyProxyException {
		if (!(proxy instanceof DockerContainerProxy)) throw new ShinyProxyException("Cannot release proxy: not a Docker proxy: " + proxy.getName());
		try {
			DockerContainerProxy dProxy = (DockerContainerProxy) proxy;
			dProxy.setStatus(ContainerProxyStatus.Stopping);
			releasePort(dProxy);
			doStopProxy(dProxy);
			dProxy.setStatus(ContainerProxyStatus.Stopped);
		} catch (Exception e) {
			throw new ShinyProxyException("Failed to stop container", e);
		}
	}

	protected abstract void doStopProxy(DockerContainerProxy proxy) throws Exception;
	
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
	protected List<String> buildEnv(String userName, ShinyApp app) throws IOException {
		List<String> env = new ArrayList<>();
		env.add(String.format("SHINYPROXY_USERNAME=%s", userName));
		
		String[] groups = userService.getGroups(userService.getCurrentAuth());
		env.add(String.format("SHINYPROXY_USERGROUPS=%s", Arrays.stream(groups).collect(Collectors.joining(","))));
		
		String envFile = app.getDockerEnvFile();
		if (envFile != null && Files.isRegularFile(Paths.get(envFile))) {
			Properties envProps = new Properties();
			envProps.load(new FileInputStream(envFile));
			for (Object key: envProps.keySet()) {
				env.add(String.format("%s=%s", key, envProps.get(key)));
			}
		}

		for (Map.Entry<String, String> entry : app.getDockerEnv().entrySet()) {
			env.add(String.format("%s=%s", entry.getKey(), entry.getValue()));
		}
		
		return env;
	}
	
	protected void allocatePort(DockerContainerProxy proxy) {
		if (useInternalDockerNetwork) return;
		
		int startPort = Integer.valueOf(environment.getProperty(PROPERTY_PREFIX + "port-range-start"));
		int maxPort = Integer.valueOf(environment.getProperty(PROPERTY_PREFIX + "port-range-max", "-1"));
		int nextPort = startPort;
		while (occupiedPorts.contains(nextPort)) nextPort++;
		
		if (maxPort > 0 && nextPort > maxPort) {
			throw new ShinyProxyException("Cannot create container: all allocated ports are currently in use."
					+ " Please try again later or contact an administrator.");
		}
		
		occupiedPorts.add(nextPort);
		proxy.setPort(nextPort);
	}
	
	protected void releasePort(DockerContainerProxy proxy) {
		if (useInternalDockerNetwork) return;
		occupiedPorts.remove(proxy.getPort());
	}
	
	protected void calculateTargetURL(DockerContainerProxy proxy) throws MalformedURLException {
		URL hostURL = new URL(environment.getProperty(PROPERTY_PREFIX + "url", "http://localhost"));
		String protocol = environment.getProperty(PROPERTY_PREFIX + "container-protocol", hostURL.getProtocol());
		String hostName = hostURL.getHost();
		int port = proxy.getPort();
		
		if (useInternalDockerNetwork) {
			protocol = environment.getProperty(PROPERTY_PREFIX + "container-protocol", "http");
			hostName = proxy.getName();
			port = getAppPort(proxy);
		}
		
		String target = String.format("%s://%s:%d", protocol, hostName, port);
		proxy.setTarget(target);
	}
	
	protected int getAppPort(DockerContainerProxy proxy) {
		String port = proxy.getApp().getPort();
		if (port == null || port.isEmpty()) return 3838;
		return Integer.parseInt(port);
	}
	
	protected boolean testProxy(DockerContainerProxy proxy) {
		int totalWaitMs = Integer.parseInt(environment.getProperty("shiny.proxy.container-wait-time", "20000"));
		int waitMs = Math.min(2000, totalWaitMs);
		int maxTries = totalWaitMs / waitMs;
		int timeoutMs = Integer.parseInt(environment.getProperty("shiny.proxy.container-wait-timeout", "5000"));
		
		return Utils.retry(i -> {
			try {
				URL testURL = new URL(proxy.getTarget());
				HttpURLConnection connection = ((HttpURLConnection) testURL.openConnection());
				connection.setConnectTimeout(timeoutMs);
				int responseCode = connection.getResponseCode();
				if (responseCode == 200) return true;
			} catch (Exception e) {
				if (i > 1) log.warn(String.format("Container unresponsive, trying again (%d/%d): %s", i, maxTries, proxy.getTarget()));
			}
			return false;
		}, maxTries, waitMs);
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
