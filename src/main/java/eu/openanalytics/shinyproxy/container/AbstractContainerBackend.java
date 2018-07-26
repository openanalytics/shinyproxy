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
package eu.openanalytics.shinyproxy.container;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
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
import org.springframework.core.env.Environment;

import eu.openanalytics.shinyproxy.ShinyProxyException;
import eu.openanalytics.shinyproxy.services.AppService.ShinyApp;
import eu.openanalytics.shinyproxy.services.UserService;
import eu.openanalytics.shinyproxy.util.Utils;

public abstract class AbstractContainerBackend<T extends AbstractContainerProxy> implements IContainerBackend {

	protected static final String PROPERTY_URL = "url";
	protected static final String PROPERTY_CONTAINER_PROTOCOL = "container-protocol";
	protected static final String PROPERTY_INTERNAL_NETWORKING = "internal-networking";
	protected static final String PROPERTY_APP_PORT = "port";
	protected static final String PROPERTY_PORT_RANGE_START = "port-range-start";
	protected static final String PROPERTY_PORT_RANGE_MAX = "port-range-max";
	protected static final String PROPERTY_PRIVILEGED = "privileged";
	protected static final String PROPERTY_CERT_PATH = "cert-path";
	
	protected static final String DEFAULT_TARGET_PROTOCOL = "http";
	protected static final String DEFAULT_TARGET_URL = DEFAULT_TARGET_PROTOCOL + "://192.168.233.128";
	protected static final String DEFAULT_PRIVILEGED = "false";
	
	protected static final String ENV_VAR_SP_USER_NAME = "SHINYPROXY_USERNAME";
	protected static final String ENV_VAR_SP_USER_GROUPS = "SHINYPROXY_USERGROUPS";
	
	private Set<Integer> occupiedPorts = Collections.synchronizedSet(new HashSet<>());
	
	private boolean useInternalNetwork;
	
	@Inject
	protected Environment environment;
	
	@Inject
	protected UserService userService;
	
	@Override
	public void initialize() throws ShinyProxyException {
		// If ShinyProxy runs as a container itself, some things like port publishing can be omitted.
		useInternalNetwork = Boolean.valueOf(getProperty(PROPERTY_INTERNAL_NETWORKING));
	}
	
	@Override
	public IContainerProxy createProxy(ContainerProxyRequest request) throws ShinyProxyException {
		T proxy = instantiateProxy();
		proxy.setStatus(ContainerProxyStatus.New);
		proxy.setUserId(request.userId);
		proxy.setApp(request.app);
		
		if (!useInternalNetwork) proxy.setPort(allocatePort());
		
		try {
			prepareProxy(proxy, request);
		} catch (Exception e) {
			disposeProxy(proxy);
			if (e instanceof ShinyProxyException) throw (ShinyProxyException) e;
			throw new ShinyProxyException("Failed to create container", e);
		}

		return proxy;
	}

	protected abstract T instantiateProxy();
	
	protected abstract void prepareProxy(T proxy, ContainerProxyRequest request) throws Exception;
	
	@Override
	public void startProxy(IContainerProxy p) throws ShinyProxyException {
		T proxy = getProxyCast(p);
		proxy.setStatus(ContainerProxyStatus.Starting);
		
		try {
			doStartProxy(proxy);
			calculateTargetURL(proxy);
		} catch (Throwable t) {
			disposeProxy(proxy);
			throw new ShinyProxyException("Failed to start container", t);
		}
		
		if (!testProxy(proxy)) {
			stopProxy(proxy);
			throw new ShinyProxyException("Container did not respond in time");
		}
		
		proxy.setStartupTimestamp(System.currentTimeMillis());
		proxy.setStatus(ContainerProxyStatus.Up);
	}
	
	protected abstract void doStartProxy(T proxy) throws Exception;

	protected void calculateTargetURL(T proxy) throws Exception {
		URL hostURL = new URL(getProperty(PROPERTY_URL, null, DEFAULT_TARGET_URL));
		String protocol = getProperty(PROPERTY_CONTAINER_PROTOCOL, null, hostURL.getProtocol());
		String hostName = hostURL.getHost();
		int port = proxy.getPort();
		
		if (isUseInternalNetwork()) {
			protocol = getProperty(PROPERTY_CONTAINER_PROTOCOL, null, DEFAULT_TARGET_PROTOCOL);
			hostName = proxy.getName();
			port = getAppPort(proxy);
		}
		
		String target = String.format("%s://%s:%d", protocol, hostName, port);
		proxy.setTarget(target);
	}
	
	@Override
	public void stopProxy(IContainerProxy p) throws ShinyProxyException {
		T proxy = getProxyCast(p);
		try {
			proxy.setStatus(ContainerProxyStatus.Stopping);
			disposeProxy(proxy);
			doStopProxy(proxy);
			proxy.setStatus(ContainerProxyStatus.Stopped);
		} catch (Exception e) {
			throw new ShinyProxyException("Failed to stop container", e);
		}
	}

	protected abstract void doStopProxy(T proxy) throws Exception;
	
	protected void disposeProxy(T proxy) {
		if (!useInternalNetwork) releasePort(proxy.getPort());
	}
	
	@Override
	public BiConsumer<File, File> getOutputAttacher(IContainerProxy proxy) {
		// Default: do not support output attaching.
		return null;
	}
	
	protected boolean testProxy(T proxy) {
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
				if (i > 1 && getLog() != null) getLog().warn(String.format("Container unresponsive, trying again (%d/%d): %s", i, maxTries, proxy.getTarget()));
			}
			return false;
		}, maxTries, waitMs);
	}
	
	protected int getAppPort(T proxy) {
		String port = proxy.getApp().get(PROPERTY_APP_PORT);
		if (port == null || port.isEmpty()) return 3838;
		return Integer.parseInt(port);
	}
	
	protected List<String> buildEnv(String userName, ShinyApp app) throws IOException {
		List<String> env = new ArrayList<>();
		env.add(String.format("%s=%s", ENV_VAR_SP_USER_NAME, userName));
		
		String[] groups = userService.getGroups(userService.getCurrentAuth());
		env.add(String.format("%s=%s", ENV_VAR_SP_USER_GROUPS, Arrays.stream(groups).collect(Collectors.joining(","))));
		
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
	
	protected int allocatePort() {
		int startPort = Integer.valueOf(getProperty(PROPERTY_PORT_RANGE_START, null, "3838"));
		int maxPort = Integer.valueOf(getProperty(PROPERTY_PORT_RANGE_MAX, null, "-1"));
		int nextPort = startPort;
		while (occupiedPorts.contains(nextPort)) nextPort++;
		
		if (maxPort > 0 && nextPort > maxPort) {
			throw new ShinyProxyException("Cannot create container: all allocated ports are currently in use."
					+ " Please try again later or contact an administrator.");
		}
		
		//occupiedPorts.add(nextPort);
		return nextPort;
	}
	
	protected void releasePort(int port) {
		//occupiedPorts.remove(port);
	}
	
	protected abstract Logger getLog();
	
	protected String getProperty(String name) {
		return getProperty(name, null, null);
	}
	
	protected String getProperty(String name, ShinyApp app, String defaultValue) {
		return getProperty(name, app, String.class, defaultValue);
	}
	
	@SuppressWarnings("unchecked")
	protected <E> E getProperty(String name, ShinyApp app, Class<E> type, E defaultValue) {
		E value = null;
		if (app != null) {
			if (type.equals(String[].class)) {
				value = (E) app.getArray(name);
			} else if (type.equals(Map.class)) {
				value = (E) app.getMap(name);
			} else {
				value = (E) app.get(name);
			}
		}
		if (value == null) {
			String key = getPropertyPrefix() + name;
			value = environment.getProperty(key, type, defaultValue);
		}
		return value;
	}
	
	protected abstract String getPropertyPrefix();
	
	public boolean isUseInternalNetwork() {
		return useInternalNetwork;
	}
	
	@SuppressWarnings("unchecked")
	private T getProxyCast(IContainerProxy proxy) {
		return (T) proxy;
	}
}
