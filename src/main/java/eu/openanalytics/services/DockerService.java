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
package eu.openanalytics.services;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.log4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerCertificates;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.LogsParam;
import com.spotify.docker.client.DockerClient.RemoveContainerParam;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.HostConfig.Builder;
import com.spotify.docker.client.messages.PortBinding;
import com.spotify.docker.client.messages.mount.Mount;
import com.spotify.docker.client.messages.swarm.ContainerSpec;
import com.spotify.docker.client.messages.swarm.DnsConfig;
import com.spotify.docker.client.messages.swarm.EndpointSpec;
import com.spotify.docker.client.messages.swarm.NetworkAttachmentConfig;
import com.spotify.docker.client.messages.swarm.Node;
import com.spotify.docker.client.messages.swarm.PortConfig;
import com.spotify.docker.client.messages.swarm.ServiceSpec;
import com.spotify.docker.client.messages.swarm.Task;
import com.spotify.docker.client.messages.swarm.TaskSpec;

import eu.openanalytics.ShinyProxyException;
import eu.openanalytics.services.AppService.ShinyApp;
import eu.openanalytics.services.EventService.EventType;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.servlet.handlers.ServletRequestContext;

@Service
public class DockerService {
		
	private Logger log = Logger.getLogger(DockerService.class);

	private Map<AppInstanceDetails, Proxy> proxies = new HashMap<>();
	
	private List<MappingListener> mappingListeners = Collections.synchronizedList(new ArrayList<>());
	private Set<Integer> occupiedPorts = Collections.synchronizedSet(new HashSet<>());
	
	private ExecutorService containerKiller = Executors.newSingleThreadExecutor();
	private boolean shuttingDown = false;
	
	private boolean swarmMode = false;
	
	@Inject
	Environment environment;
	
	@Inject
	AppService appService;
	
	@Inject
	UserService userService;
	
	@Inject
	EventService eventService;
	
	@Inject
	LogService logService;
	
	@Inject
	DockerClient dockerClient;

	public static class AppInstanceDetails {
		
		public String userName;
		public String appName;
		public String tagOverride;

		public AppInstanceDetails(String userName, String appName, String tagOverride) {
			this.userName = userName;
			this.appName = appName;
			this.tagOverride = tagOverride;
		}

		public AppInstanceDetails(String userName, String appName) {
			this.userName = userName;
			this.appName = appName;
		}

		@Override
		public int hashCode() {
			return new HashCodeBuilder()
				.append(userName)
				.append(appName)
				.append(tagOverride)
				.toHashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (obj == null) return false;
			if (this.getClass() != obj.getClass()) return false;
			AppInstanceDetails other = (AppInstanceDetails) obj;
			if (!Objects.equals(userName, other.userName)) return false;
			if (!Objects.equals(appName, other.appName)) return false;
			if (!Objects.equals(tagOverride, other.tagOverride)) return false;
			return true;
		}
	}

	public static class Proxy {
		
		public String name;
		public String protocol;
		public String host;
		public int port;
		public String containerId;
		public String serviceId;
		public String userName;
		public String appName;
		public String tagOverride;
		public String sessionId;
		public boolean launching;
		public long startupTimestamp;

		public Proxy(AppInstanceDetails details) {
			this.userName = details.userName;
			this.appName = details.appName;
			this.tagOverride = details.tagOverride;
			this.launching = true;
		}
		
		public String uptime() {
			long uptimeSec = (System.currentTimeMillis() - startupTimestamp)/1000;
			return String.format("%d:%02d:%02d", uptimeSec/3600, (uptimeSec%3600)/60, uptimeSec%60);
		}
		
		public Proxy copyInto(Proxy target) {
			target.name = this.name;
			target.protocol = this.protocol;
			target.host = this.host;
			target.port = this.port;
			target.containerId = this.containerId;
			target.serviceId = this.serviceId;
			target.userName = this.userName;
			target.appName = this.appName;
			target.sessionId = this.sessionId;
			target.startupTimestamp = this.startupTimestamp;
			return target;
		}
	}
	
	@PostConstruct
	public void init() {
		try {
			swarmMode = (dockerClient.inspectSwarm().id() != null);
		} catch (DockerException | InterruptedException e) {}
		log.info(String.format("Swarm mode is %s", (swarmMode ? "enabled" : "disabled")));
	}
	
	@PreDestroy
	public void shutdown() {
		shuttingDown = true;
		containerKiller.shutdown();
		synchronized (proxies) {
			for (Proxy proxy: proxies.values()) {
				releaseProxy(proxy, false, false);
			}
		}
	}

	@Bean
	public DockerClient getDockerClient() {
		try {
			return DefaultDockerClient.builder()
				.dockerCertificates(DockerCertificates.builder().dockerCertPath(Paths.get(environment.getProperty("shiny.proxy.docker.cert-path", ""))).build().orNull())
				.uri(environment.getProperty("shiny.proxy.docker.url"))
				.build();
		} catch (DockerCertificateException e) {
			throw new ShinyProxyException("Failed to initialize docker client", e);
		}
	}

	public Collection<Proxy> listProxies() {
		return proxies.values();
	}
	
	public String getMapping(AppInstanceDetails appDetails, boolean startNew) {
		waitForLaunchingProxy(appDetails);
		Proxy proxy = proxies.get(appDetails);
		if (proxy == null && startNew) {
			// The user has no proxy yet.
			proxy = startProxy(appDetails);
		}
		return (proxy == null) ? null : proxy.name;
	}
	
	public boolean sessionOwnsProxy(HttpServerExchange exchange) {
		String sessionId = getCurrentSessionId(exchange);
		if (sessionId == null) return false;
		String proxyName = exchange.getRelativePath();
		synchronized (proxies) {
			for (Proxy p: proxies.values()) {
				if (p.sessionId.equals(sessionId) && proxyName.startsWith("/" + p.name)) {
					return true;
				}
			}
		}
		return false;
	}
	
	public void releaseProxies(String userName) {
		List<Proxy> proxiesToRelease = new ArrayList<>();
		synchronized (proxies) {
			for (Proxy proxy: proxies.values()) {
				if (userName.equals(proxy.userName)) proxiesToRelease.add(proxy);
			}
		}
		for (Proxy proxy: proxiesToRelease) {
			releaseProxy(proxy, true);
		}
	}
	
	private String getCurrentSessionId(HttpServerExchange exchange) {
		if (exchange == null && ServletRequestContext.current() != null) {
			exchange = ServletRequestContext.current().getExchange();
		}
		if (exchange == null) return null;
		Cookie sessionCookie = exchange.getRequestCookies().get("JSESSIONID");
		if (sessionCookie == null) return null;
		return sessionCookie.getValue();
	}

	public void releaseProxy(Proxy proxy, boolean async) {
		releaseProxy(proxy, async, true);
	}
	
	private void releaseProxy(Proxy proxy, boolean async, boolean removeFromList) {
		if (removeFromList) {
			synchronized (proxies) {
				if (!proxies.containsValue(proxy)) return;
				while (proxies.values().remove(proxy));
			}
		}
		
		Runnable releaser = () -> {
			try {
				if (swarmMode) {
					dockerClient.removeService(proxy.serviceId);
				} else {
					ShinyApp app = appService.getApp(proxy.appName);
					if (app != null && app.getDockerNetworkConnections() != null) {
						for (String networkConnection: app.getDockerNetworkConnections()) {
							dockerClient.disconnectFromNetwork(proxy.containerId, networkConnection);
						}
					}
					dockerClient.removeContainer(proxy.containerId, RemoveContainerParam.forceKill());
				}
				releasePort(proxy.port);
				log.info(String.format("Proxy released [user: %s] [app: %s] [port: %d]", proxy.userName, proxy.appName, proxy.port));
				eventService.post(EventType.AppStop.toString(), proxy.userName, proxy.appName);
			} catch (Exception e){
				log.error("Failed to release proxy " + proxy.name, e);
			}
		};
		if (async) containerKiller.submit(releaser);
		else releaser.run();
		
		synchronized (mappingListeners) {
			for (MappingListener listener: mappingListeners) {
				listener.mappingRemoved(proxy.name);
			}
		}
	}
	
	private Proxy startProxy(AppInstanceDetails details) {
		ShinyApp app = appService.getApp(details.appName);
		if (app == null) {
			throw new ShinyProxyException("Cannot start container: unknown application: " + details.appName);
		}
		
		if (proxies.get(details) != null) {
			throw new ShinyProxyException("Cannot start container: user " + details.userName + " already has a running proxy");
		}
		
		Proxy proxy = new Proxy(details);
		proxy.port = getFreePort();
		proxy.sessionId = getCurrentSessionId(null);
		synchronized (proxies) {
			if (shuttingDown) throw new ShinyProxyException("Cannot start container: already shutting down");
			proxies.put(details, proxy);
		}
		
		String dockerImage = app.getDockerImage();
		if (details.tagOverride != null) {
			int idx = dockerImage.indexOf(':');
			if (idx == -1) {
				dockerImage += ':' + details.tagOverride;
			} else {
				dockerImage = dockerImage.substring(0, idx + 1) + details.tagOverride;
			}
		}

		try {
			URL hostURL = new URL(environment.getProperty("shiny.proxy.docker.url"));
			proxy.protocol = environment.getProperty("shiny.proxy.docker.container-protocol", hostURL.getProtocol());
			
			if (swarmMode) {
				Mount[] mounts = getBindVolumes(app).stream()
						.map(b -> b.split(":"))
						.map(fromTo -> Mount.builder().source(fromTo[0]).target(fromTo[1]).type("bind").build())
						.toArray(i -> new Mount[i]);

				ContainerSpec containerSpec = ContainerSpec.builder()
						.image(dockerImage)
						.command(app.getDockerCmd())
						.env(buildEnv(details.userName, app))
						.dnsConfig(DnsConfig.builder().nameServers(app.getDockerDns()).build())
						.mounts(mounts)
						.build();
				
				NetworkAttachmentConfig[] networks = Arrays
						.stream(Optional.ofNullable(app.getDockerNetworkConnections()).orElse(new String[0]))
						.map(n -> NetworkAttachmentConfig.builder().target(n).build())
						.toArray(i -> new NetworkAttachmentConfig[i]);
				
				proxy.name = proxy.appName + "_" + proxy.port;
				proxy.serviceId = dockerClient.createService(ServiceSpec.builder()
						.name(proxy.name)
						.networks(networks)
						.taskTemplate(TaskSpec.builder()
								.containerSpec(containerSpec)
								.build())
						.endpointSpec(EndpointSpec.builder()
								.ports(PortConfig.builder().publishedPort(proxy.port).targetPort(3838).build())
								.build())
						.build()).id();

				boolean containerFound = retry(i -> {
					try {
						Task serviceTask = dockerClient
							.listTasks(Task.Criteria.builder().serviceName(proxy.name).build())
							.stream().findAny().orElseThrow(() -> new IllegalStateException("Swarm service has no tasks"));
						proxy.containerId = serviceTask.status().containerStatus().containerId();
						proxy.host = serviceTask.nodeId();
					} catch (Exception e) {
						throw new RuntimeException("Failed to inspect swarm service tasks");
					}
					return (proxy.containerId != null);
				}, 10, 2000);
				if (!containerFound) throw new IllegalStateException("Swarm container did not start in time");
				
				Node node = dockerClient.listNodes().stream()
						.filter(n -> n.id().equals(proxy.host)).findAny()
						.orElseThrow(() -> new IllegalStateException(String.format("Swarm node not found [id: %s]", proxy.host)));
				proxy.host = node.description().hostname();
				
				log.info(String.format("Container running in swarm [service: %s] [node: %s]", proxy.name, proxy.host));
			} else {
				Builder hostConfigBuilder = HostConfig.builder();
				
				Optional.ofNullable(memoryToBytes(app.getDockerMemory())).ifPresent(l -> hostConfigBuilder.memory(l));
				Optional.ofNullable(app.getDockerNetwork()).ifPresent(n -> hostConfigBuilder.networkMode(app.getDockerNetwork()));
				
				hostConfigBuilder
						.portBindings(Collections.singletonMap("3838", Collections.singletonList(PortBinding.of("0.0.0.0", proxy.port))))
						.dns(app.getDockerDns())
						.binds(getBindVolumes(app));
				
				ContainerConfig containerConfig = ContainerConfig.builder()
					    .hostConfig(hostConfigBuilder.build())
					    .image(dockerImage)
					    .exposedPorts("3838")
					    .cmd(app.getDockerCmd())
					    .env(buildEnv(details.userName, app))
					    .build();
				
				ContainerCreation container = dockerClient.createContainer(containerConfig);
				if (app.getDockerNetworkConnections() != null) {
					for (String networkConnection: app.getDockerNetworkConnections()) {
						dockerClient.connectToNetwork(container.id(), networkConnection);
					}
				}
				dockerClient.startContainer(container.id());
				
				ContainerInfo info = dockerClient.inspectContainer(container.id());
				proxy.host = hostURL.getHost();
				proxy.name = info.name().substring(1);
				proxy.containerId = container.id();
			}

			proxy.startupTimestamp = System.currentTimeMillis();
		} catch (Exception e) {
			releasePort(proxy.port);
			proxies.remove(details);
			throw new ShinyProxyException("Failed to start container: " + e.getMessage(), e);
		}

		if (!testProxy(proxy)) {
			releaseProxy(proxy, true);
			throw new ShinyProxyException("Container did not respond in time");
		}
		
		try {
			URI target = new URI(String.format("%s://%s:%d", proxy.protocol, proxy.host, proxy.port));
			synchronized (mappingListeners) {
				for (MappingListener listener: mappingListeners) {
					listener.mappingAdded(proxy.name, target);
				}
			}
		} catch (URISyntaxException ignore) {}
		
		if (logService.isContainerLoggingEnabled()) {
			try {
				LogStream logStream = dockerClient.logs(proxy.containerId, LogsParam.follow(), LogsParam.stdout(), LogsParam.stderr());
				logService.attachLogWriter(proxy, logStream);
			} catch (DockerException e) {
				log.error("Failed to attach to container log " + proxy.containerId, e);
			} catch (InterruptedException e) {
				log.error("Interrupted while attaching to container log " + proxy.containerId, e);
			}
		}
		
		proxy.launching = false;
		log.info(String.format("Proxy activated [user: %s] [app: %s] [tagOverride: %s] [port: %d]", details.userName, details.appName, details.tagOverride, proxy.port));
		// TODO add tagOverride
		eventService.post(EventType.AppStart.toString(), details.userName, details.appName);
		
		return proxy;
	}
	
	private void waitForLaunchingProxy(AppInstanceDetails details) {
		int totalWaitMs = Integer.parseInt(environment.getProperty("shiny.proxy.container-wait-time", "20000"));
		int waitMs = Math.min(2000, totalWaitMs);
		int maxTries = totalWaitMs / waitMs;
		
		boolean mayProceed = retry(i -> {
			Proxy proxy = proxies.get(details);
			return proxy == null || !proxy.launching;
		}, maxTries, waitMs);
		
		if (!mayProceed) throw new ShinyProxyException("Cannot proceed: waiting for proxy to launch");
	}
	
	private boolean testProxy(Proxy proxy) {
		int totalWaitMs = Integer.parseInt(environment.getProperty("shiny.proxy.container-wait-time", "20000"));
		int waitMs = Math.min(2000, totalWaitMs);
		int maxTries = totalWaitMs / waitMs;
		int timeoutMs = Integer.parseInt(environment.getProperty("shiny.proxy.container-wait-timeout", "5000"));
		
		return retry(i -> {
			String urlString = String.format("%s://%s:%d", proxy.protocol, proxy.host, proxy.port);
			try {
				URL testURL = new URL(urlString);
				HttpURLConnection connection = ((HttpURLConnection) testURL.openConnection());
				connection.setConnectTimeout(timeoutMs);
				int responseCode = connection.getResponseCode();
				if (responseCode == 200) return true;
			} catch (Exception e) {
				if (i > 1) log.warn(String.format("Container unresponsive, trying again (%d/%d): %s", i, maxTries, urlString));
			}
			return false;
		}, maxTries, waitMs);
	}

	private List<String> buildEnv(String userName, ShinyApp app) throws IOException {
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
	
	private List<String> getBindVolumes(ShinyApp app) {
		List<String> volumes = new ArrayList<>();

		if (app.getDockerVolumes() != null) {
			for (String vol: app.getDockerVolumes()) {
				volumes.add(vol);
			}
		}
		
		return volumes;
	}
	
	private int getFreePort() {
		int startPort = Integer.valueOf(environment.getProperty("shiny.proxy.docker.port-range-start"));
		int maxPort = Integer.valueOf(environment.getProperty("shiny.proxy.docker.port-range-max", "-1"));
		int nextPort = startPort;
		while (occupiedPorts.contains(nextPort)) nextPort++;
		if (maxPort > 0 && nextPort > maxPort) {
			throw new ShinyProxyException("Cannot start container: all allocated ports are currently in use."
					+ " Please try again later or contact an administrator.");
		}
		occupiedPorts.add(nextPort);
		return nextPort;
	}
	
	private void releasePort(int port) {
		occupiedPorts.remove(port);
	}

	private boolean retry(IntPredicate job, int tries, int waitTime) {
		boolean retVal = false;
		for (int currentTry = 1; currentTry <= tries; currentTry++) {
			if (job.test(currentTry)) {
				retVal = true;
				break;
			}
			try { Thread.sleep(waitTime); } catch (InterruptedException ignore) {}
		}
		return retVal;
	}
	
	private Long memoryToBytes(String memory) {
		if (memory == null || memory.isEmpty()) return null;
		Matcher matcher = Pattern.compile("(\\d+)([bkmg]?)").matcher(memory.toLowerCase());
		if (!matcher.matches()) throw new IllegalArgumentException("Invalid memory argument: " + memory);
		long mem = Long.parseLong(matcher.group(1));
		String unit = matcher.group(2);
		switch (unit) {
		case "k":
			mem *= 1024;
			break;
		case "m":
			mem *= 1024*1024;
			break;
		case "g":
			mem *= 1024*1024*1024;
			break;
		default:
		}
		return mem;
	}
	
	public void addMappingListener(MappingListener listener) {
		mappingListeners.add(listener);
	}
	
	public void removeMappingListener(MappingListener listener) {
		mappingListeners.remove(listener);
	}
	
	public static interface MappingListener {
		public void mappingAdded(String mapping, URI target);
		public void mappingRemoved(String mapping);
	}
}
