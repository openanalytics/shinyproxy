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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.util.WebUtils;

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
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.servlet.handlers.ServletRequestContext;

@Service
public class DockerService {
		
	private Logger log = Logger.getLogger(DockerService.class);
	private Random rng = new Random();

	private List<Proxy> launchingProxies = Collections.synchronizedList(new ArrayList<>());
	private List<Proxy> activeProxies = Collections.synchronizedList(new ArrayList<>());
	
	private List<MappingListener> mappingListeners = Collections.synchronizedList(new ArrayList<>());
	private Set<Integer> occupiedPorts = Collections.synchronizedSet(new HashSet<>());

	private ExecutorService containerKiller = Executors.newSingleThreadExecutor();
	
	private boolean swarmMode = false;
	private boolean kubernetes = false;

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

	@Inject
	KubernetesClient kubeClient;
	
	public static class Proxy {

		public String name;
		public String protocol;
		public String host;
		public int port;
		public String containerId;
		public String serviceId;
		public String userName;
		public String appName;
		public Set<String> sessionIds = new HashSet<>();
		public long startupTimestamp;
		public Long lastHeartbeatTimestamp;
		public Pod kubePod;

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
			target.sessionIds = this.sessionIds;
			target.startupTimestamp = this.startupTimestamp;
			return target;
		}
	}
	
	@PostConstruct
	public void init() {
		if (kubernetes) {
			log.info("Kubernetes is enabled");
			return;
		}
		try {
			swarmMode = (dockerClient.inspectSwarm().id() != null);
		} catch (DockerException | InterruptedException e) {}
		log.info(String.format("Swarm mode is %s", (swarmMode ? "enabled" : "disabled")));

		Thread heartbeatThread = new Thread(new AppCleaner(), "HeartbeatThread");
		heartbeatThread.setDaemon(true);
		heartbeatThread.start();
	}
	
	
	@PreDestroy
	public void shutdown() {
		containerKiller.shutdown();
		List<Proxy> proxiesToRelease = new ArrayList<>();
		synchronized (activeProxies) {
			proxiesToRelease.addAll(activeProxies);
		}
		for (Proxy proxy: proxiesToRelease) releaseProxy(proxy, false);
	}

	@Bean KubernetesClient getKubeClient() {
		kubernetes = "true".equals(environment.getProperty("shiny.proxy.docker.kubernetes"));
		if (!kubernetes) return null;
		ConfigBuilder configBuilder = new ConfigBuilder();
		String masterUrl = environment.getProperty("shiny.proxy.docker.kubernetes-url");
		if (masterUrl != null) {
			configBuilder.withMasterUrl(masterUrl);
		}
		return new DefaultKubernetesClient(configBuilder.build());
	}

	@Bean
	@DependsOn("getKubeClient") // for kubernetes boolean
	public DockerClient getDockerClient() {
		if (kubernetes) return null;
		try {
			DefaultDockerClient.Builder builder = DefaultDockerClient.fromEnv();
			String confCertPath = environment.getProperty("shiny.proxy.docker.cert-path");
			if (confCertPath != null) {
				builder.dockerCertificates(DockerCertificates.builder().dockerCertPath(Paths.get(confCertPath)).build().orNull());
			}
			String confUrl = environment.getProperty("shiny.proxy.docker.url");
			if (confUrl != null) {
				builder.uri(confUrl);
			}
			return builder.build();
		} catch (DockerCertificateException e) {
			throw new ShinyProxyException("Failed to initialize docker client", e);
		}
	}
	
	public List<Proxy> listProxies() {
		synchronized (activeProxies) {
			return activeProxies.stream().map(p -> p.copyInto(new Proxy())).collect(Collectors.toList());
		}
	}

	public String getMapping(HttpServletRequest request, String userName, String appName, boolean startNew) {
		waitForLaunchingProxy(userName, appName);
		Proxy proxy = findProxy(userName, appName);
		if (proxy == null && startNew) {
			// The user has no proxy yet.
			proxy = startProxy(userName, appName);
		}
		if (proxy == null) {
			return null;
		} else {
			proxy.sessionIds.add(getCurrentSessionId(request));
			return proxy.name;
		}
	}
	
	public boolean sessionOwnsProxy(HttpServerExchange exchange) {
		String sessionId = getCurrentSessionId(exchange);
		if (sessionId == null) return false;
		String proxyName = exchange.getRelativePath();
		synchronized (activeProxies) {
			for (Proxy p: activeProxies) {
				if (p.sessionIds.contains(sessionId) && proxyName.startsWith("/" + p.name)) {
					return true;
				}
			}
		}
		synchronized (launchingProxies) {
			for (Proxy p: launchingProxies) {
				if (p.sessionIds.contains(sessionId) && proxyName.startsWith("/" + p.name)) {
					return true;
				}
			}
		}
		return false;
	}
	
	public List<Proxy> releaseProxies(String userName) {
		List<Proxy> proxiesToRelease = new ArrayList<>();
		synchronized (activeProxies) {
			for (Proxy proxy: activeProxies) {
				if (userName.equals(proxy.userName)) proxiesToRelease.add(proxy);
			}
		}
		for (Proxy proxy: proxiesToRelease) {
			releaseProxy(proxy, true);
		}
		return proxiesToRelease;
	}
	
	public void releaseProxy(String userName, String appName) {
		Proxy proxy = findProxy(userName, appName);
		if (proxy != null) {
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

	private String getCurrentSessionId(HttpServletRequest request) {
		if (request == null) {
			return getCurrentSessionId((HttpServerExchange) null);
		}
		javax.servlet.http.Cookie sessionCookie = WebUtils.getCookie(request, "JSESSIONID");
		if (sessionCookie == null) return null;
		return sessionCookie.getValue();
	}
	
	private void releaseProxy(Proxy proxy, boolean async) {
		activeProxies.remove(proxy);
		
		Runnable releaser = () -> {
			try {
				if (kubernetes) {
					kubeClient.pods().delete(proxy.kubePod);
				} else if (swarmMode) {
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

	private Proxy startProxy(String userName, String appName) {
		ShinyApp app = appService.getApp(appName);
		if (app == null) {
			throw new ShinyProxyException("Cannot start container: unknown application: " + appName);
		}

		if (findProxy(userName, appName) != null) {
			throw new ShinyProxyException("Cannot start container: user " + userName + " already has a running proxy");
		}

		boolean internalNetworking = "true".equals(environment.getProperty("shiny.proxy.docker.internal-networking"));
		boolean generateName = kubernetes || swarmMode ||
			"true".equals(environment.getProperty("shiny.proxy.docker.generate-name", String.valueOf(internalNetworking)));

		Proxy proxy = new Proxy();
		proxy.userName = userName;
		proxy.appName = appName;
		if (internalNetworking) {
			proxy.port = app.getPort();
		} else {
			proxy.port = getFreePort();
		}
		launchingProxies.add(proxy);
		
		String kubeNamespace = Optional.ofNullable(app.getKubernetesNamespace()).orElse("default");

		try {
			String containerProtocolDefault = "http";
			String hostURLString = environment.getProperty("shiny.proxy.docker.url");
			URL hostURL = null;
			if (hostURLString != null) {
				hostURL = new URL(hostURLString);
				containerProtocolDefault = hostURL.getProtocol();
			}
			proxy.protocol = environment.getProperty("shiny.proxy.docker.container-protocol", containerProtocolDefault);

			if (generateName) {
				byte[] nameBytes = new byte[20];
				rng.nextBytes(nameBytes);
				proxy.name = Hex.encodeHexString(nameBytes);
			}

			if (kubernetes) {
				String[] dockerVolumeStrs = Optional.ofNullable(app.getDockerVolumes()).orElse(new String[] {});
				Volume[] volumes = new Volume[dockerVolumeStrs.length];
				VolumeMount[] volumeMounts = new VolumeMount[dockerVolumeStrs.length];
				for (int i = 0; i < dockerVolumeStrs.length; i++) {
					String[] dockerVolume = dockerVolumeStrs[i].split(":");
					String hostSource = dockerVolume[0];
					String containerDest = dockerVolume[1];
					String name = "shinyproxy-volume-" + i;
					volumes[i] = new VolumeBuilder()
							.withNewHostPath(hostSource)
							.withName(name)
							.build();
					volumeMounts[i] = new VolumeMountBuilder()
							.withMountPath(containerDest)
							.withName(name)
							.build();
				}

				String[] dockerNetworkConnections = app.getDockerNetworkConnections();
				if (dockerNetworkConnections != null && dockerNetworkConnections.length > 0) {
					log.warn(String.format("Docker networks specified for app %s, but Kubernetes does not have that concept", app.getName()));
				}

				ContainerPortBuilder containerPortBuilder = new ContainerPortBuilder().withContainerPort(app.getPort());
				if (!internalNetworking) {
					containerPortBuilder.withHostPort(proxy.port);
				}

				List<EnvVar> envVars = new ArrayList<>();
				for (String envString : buildEnv(userName, app)) {
					int idx = envString.indexOf('=');
					if (idx == -1) {
						log.warn("Invalid environment variable: " + envString);
					}
					envVars.add(new EnvVar(envString.substring(0, idx), envString.substring(idx + 1), null));
				}
				ContainerBuilder containerBuilder = new ContainerBuilder()
						.withImage(app.getDockerImage())
						.withName("shiny-container")
						.withPorts(containerPortBuilder.build())
						.withEnv(envVars);

				String imagePullPolicy = environment.getProperty("shiny.proxy.docker.kubernetes-image-pull-policy", app.getKubernetesImagePullPolicy());
				if (imagePullPolicy != null) {
					containerBuilder.withImagePullPolicy(imagePullPolicy);
				}
				if (app.getDockerCmd() != null) {
					containerBuilder.withCommand(app.getDockerCmd());
				}

				String[] imagePullSecrets = environment.getProperty("shiny.proxy.docker.kubernetes-image-pull-secrets", String[].class);
				if (imagePullSecrets == null) {
					String imagePullSecret = environment.getProperty("shiny.proxy.docker.kubernetes-image-pull-secret");
					if (imagePullSecret != null) {
						imagePullSecrets = new String[] {imagePullSecret};
					} else {
						imagePullSecrets = new String[0];
					}
				}
				Pod pod = kubeClient.pods().inNamespace(kubeNamespace).createNew()
						.withApiVersion("v1")
						.withKind("Pod")
						.withNewMetadata()
							.withName(proxy.name)
						.endMetadata()
						.withNewSpec()
							.withContainers(Collections.singletonList(containerBuilder.build()))
							.withVolumes(Arrays.asList(volumes))
							.withImagePullSecrets(Arrays.asList(imagePullSecrets).stream()
								.map(LocalObjectReference::new).collect(Collectors.toList()))
						.endSpec()
						.done();

				proxy.kubePod = pod = kubeClient.resource(pod).waitUntilReady(20, TimeUnit.SECONDS);
				if (internalNetworking) {
					proxy.host = pod.getStatus().getPodIP();
				} else {
					proxy.host = pod.getStatus().getHostIP();
				}
			} else if (swarmMode) {
				Mount[] mounts = getBindVolumes(app).stream()
						.map(b -> b.split(":"))
						.map(fromTo -> Mount.builder().source(fromTo[0]).target(fromTo[1]).type("bind").build())
						.toArray(i -> new Mount[i]);

				ContainerSpec containerSpec = ContainerSpec.builder()
						.image(app.getDockerImage())
						.command(app.getDockerCmd())
						.env(buildEnv(userName, app))
						.dnsConfig(DnsConfig.builder().nameServers(app.getDockerDns()).build())
						.mounts(mounts)
						.build();
				
				NetworkAttachmentConfig[] networks = Arrays
						.stream(Optional.ofNullable(app.getDockerNetworkConnections()).orElse(new String[0]))
						.map(n -> NetworkAttachmentConfig.builder().target(n).build())
						.toArray(i -> new NetworkAttachmentConfig[i]);

				ServiceSpec.Builder serviceSpecBuilder = ServiceSpec.builder()
						.networks(networks)
						.name(proxy.name)
						.taskTemplate(TaskSpec.builder()
								.containerSpec(containerSpec)
								.build());
				if (!internalNetworking) {
					serviceSpecBuilder.endpointSpec(EndpointSpec.builder()
							.ports(PortConfig.builder().publishedPort(proxy.port).targetPort(app.getPort()).build())
							.build());
				}

				proxy.serviceId = dockerClient.createService(serviceSpecBuilder.build()).id();

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
				
				if (internalNetworking) {
					proxy.host = proxy.name;
				} else {
					Node node = dockerClient.listNodes().stream()
							.filter(n -> n.id().equals(proxy.host)).findAny()
							.orElseThrow(() -> new IllegalStateException(String.format("Swarm node not found [id: %s]", proxy.host)));
					proxy.host = node.description().hostname();
				}
				
				log.info(String.format("Container running in swarm [service: %s] [node: %s]", proxy.name, proxy.host));
			} else {
				Builder hostConfigBuilder = HostConfig.builder();
				
				Optional.ofNullable(memoryToBytes(app.getDockerMemory())).ifPresent(l -> hostConfigBuilder.memory(l));
				Optional.ofNullable(app.getDockerNetwork()).ifPresent(n -> hostConfigBuilder.networkMode(app.getDockerNetwork()));
				
				List<PortBinding> portBindings;
				if (internalNetworking) {
					portBindings = Collections.emptyList();
				} else {
					portBindings = Collections.singletonList(PortBinding.of("0.0.0.0", proxy.port));
				}
				hostConfigBuilder
						.portBindings(Collections.singletonMap(app.getPort().toString(), portBindings))
						.dns(app.getDockerDns())
						.binds(getBindVolumes(app));
				
				ContainerConfig containerConfig = ContainerConfig.builder()
					    .hostConfig(hostConfigBuilder.build())
					    .image(app.getDockerImage())
					    .exposedPorts(app.getPort().toString())
					    .cmd(app.getDockerCmd())
					    .env(buildEnv(userName, app))
					    .build();
				
				ContainerCreation container = dockerClient.createContainer(containerConfig);
				if (app.getDockerNetworkConnections() != null) {
					for (String networkConnection: app.getDockerNetworkConnections()) {
						dockerClient.connectToNetwork(container.id(), networkConnection);
					}
				}
				if (proxy.name != null) {
					dockerClient.renameContainer(container.id(), proxy.name);
				}
				dockerClient.startContainer(container.id());
				
				ContainerInfo info = dockerClient.inspectContainer(container.id());
				if (proxy.name == null) {
					proxy.name = info.name().substring(1);
				}
				if (internalNetworking) {
					proxy.host = proxy.name;
				} else {
					proxy.host = hostURL.getHost();
				}
				proxy.containerId = container.id();
			}

			proxy.startupTimestamp = System.currentTimeMillis();
		} catch (Exception e) {
			if (!internalNetworking) {
				releasePort(proxy.port);
			}
			launchingProxies.remove(proxy);
			throw new ShinyProxyException("Failed to start container: " + e.getMessage(), e);
		}


		if (!testProxy(proxy)) {
			releaseProxy(proxy, true);
			launchingProxies.remove(proxy);
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
				if (kubernetes) {
					LogWatch watcher = kubeClient.pods().inNamespace(kubeNamespace).withName(proxy.name).watchLog();
					logService.attachLogWatcher(proxy, watcher);
				} else {
					LogStream logStream;
					logStream = dockerClient.logs(proxy.containerId, LogsParam.follow(), LogsParam.stdout(), LogsParam.stderr());
					logService.attachLogWriter(proxy, logStream);
				}
			} catch (DockerException e) {
				log.error("Failed to attach to container log " + proxy.containerId, e);
			} catch (InterruptedException e) {
				log.error("Interrupted while attaching to container log " + proxy.containerId, e);
			}
		}
		
		activeProxies.add(proxy);
		launchingProxies.remove(proxy);
		log.info(String.format("Proxy activated [user: %s] [app: %s] [port: %d]", userName, appName, proxy.port));
		eventService.post(EventType.AppStart.toString(), userName, appName);
		
		return proxy;
	}
	
	private Proxy findProxy(String userName, String appName) {
		synchronized (activeProxies) {
			for (Proxy proxy: activeProxies) {
				if (userName.equals(proxy.userName) && appName.equals(proxy.appName)) return proxy;
			}
		}
		return null;
	}
	
	private void waitForLaunchingProxy(String userName, String appName) {
		int totalWaitMs = Integer.parseInt(environment.getProperty("shiny.proxy.container-wait-time", "20000"));
		int waitMs = Math.min(2000, totalWaitMs);
		int maxTries = totalWaitMs / waitMs;
		
		boolean mayProceed = retry(i -> {
			synchronized (launchingProxies) {
				for (Proxy proxy: launchingProxies) {
					if (userName.equals(proxy.userName) && appName.equals(proxy.appName)) {
						return false;
					}
				}
			}
			return true;
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

	public void heartbeatReceived(String user, String app) {
		Proxy proxy = findProxy(user, app);
		if (proxy != null) {
			proxy.lastHeartbeatTimestamp = System.currentTimeMillis();
		}
	}

	private class AppCleaner implements Runnable {
		@Override
		public void run() {
			long cleanupInterval = 2 * Long.parseLong(environment.getProperty("shiny.proxy.heartbeat-rate", "10000"));
			long heartbeatTimeout = Long.parseLong(environment.getProperty("shiny.proxy.heartbeat-timeout", "60000"));
			
			while (true) {
				try {
					List<Proxy> proxiesToRemove = new ArrayList<>();
					long currentTimestamp = System.currentTimeMillis();
					synchronized (activeProxies) {
						for (Proxy proxy: activeProxies) {
							Long lastHeartbeat = proxy.lastHeartbeatTimestamp;
							if (lastHeartbeat == null) lastHeartbeat = proxy.startupTimestamp;
							long proxySilence = currentTimestamp - lastHeartbeat;
							if (proxySilence > heartbeatTimeout) {
								log.info(String.format("Releasing inactive proxy [user: %s] [app: %s] [silence: %dms]", proxy.userName, proxy.appName, proxySilence));
								proxiesToRemove.add(proxy);
							}
						}
					}
					for (Proxy proxy: proxiesToRemove) {
						releaseProxy(proxy, true);
					}
				} catch (Throwable t) {
					log.error("Error in HeartbeatThread", t);
				}
				try {
					Thread.sleep(cleanupInterval);
				} catch (InterruptedException e) {}
			}
		}
	}
}
