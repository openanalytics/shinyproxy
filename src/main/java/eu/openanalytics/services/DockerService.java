/**
 * Copyright 2016 Open Analytics, Belgium
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.openanalytics.services;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerCertificates;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.PortBinding;

import eu.openanalytics.ShinyProxyException;
import eu.openanalytics.services.AppService.ShinyApp;
import eu.openanalytics.services.EventService.EventType;

@Service
public class DockerService {
		
	private Logger log = Logger.getLogger(DockerService.class);

	private List<Proxy> activeProxies = Collections.synchronizedList(new ArrayList<>());
	private List<MappingListener> mappingListeners = Collections.synchronizedList(new ArrayList<>());
	private Set<Integer> occupiedPorts = Collections.synchronizedSet(new HashSet<>());
	
	private ExecutorService containerKiller = Executors.newSingleThreadExecutor();
	
	@Inject
	Environment environment;
	
	@Inject
	AppService appService;
	
	@Inject
	EventService eventService;
	
	@Inject
	DockerClient dockerClient;

	public static class Proxy {
		public String name;
		public int port;
		public String containerId;
		public String userName;
		public String appName;
		public long startupTimestamp;
	}
	
	@Bean
	public DockerClient getDockerClient() {
		try {
			return DefaultDockerClient.builder()
				.dockerCertificates(DockerCertificates.builder().dockerCertPath(Paths.get(environment.getProperty("shiny.proxy.docker.cert-path"))).build().orNull())
				.uri(environment.getProperty("shiny.proxy.docker.url"))
				.build();
		} catch (DockerCertificateException e) {
			throw new ShinyProxyException("Failed to initialize docker client", e);
		}
	}

	public List<Container> getShinyContainers() {
		List<Container> shinyContainers = new ArrayList<>();
		try {
			List<Container> exec = dockerClient.listContainers();
			String imageName = environment.getProperty("shiny.proxy.docker.image-name");
			for (Container container : exec) {
				if (container.image().equals(imageName)) {
					shinyContainers.add(container);
				}
			}
		} catch (DockerException | InterruptedException e) {
			log.error("Failed to list containers", e);
		}
		return shinyContainers;
	}
	
	public String getContainerUptime(String containerId) {
		Proxy activeProxy = null;
		for (Proxy p: activeProxies) {
			if (containerId.equals(p.containerId)) activeProxy = p;
		}
		if (activeProxy == null) return "n/a";
		long uptimeSec = (System.currentTimeMillis() - activeProxy.startupTimestamp)/1000;
		return String.format("%d:%02d:%02d", uptimeSec/3600, (uptimeSec%3600)/60, uptimeSec%60);
	}
	
	public List<Proxy> listProxies() {
		List<Proxy> proxies = new ArrayList<>();
		synchronized (activeProxies) {
			for (Proxy proxy: activeProxies) {
				Proxy copy = new Proxy();
				copy.name = proxy.name;
				copy.port = proxy.port;
				copy.containerId = proxy.containerId;
				copy.userName = proxy.userName;
				copy.appName = proxy.appName;
				copy.startupTimestamp = proxy.startupTimestamp;
				proxies.add(copy);
			}
		}
		return proxies;
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

	public String getMapping(String userName, String appName) {
		Proxy proxy = findProxy(userName, appName);
		if (proxy == null) {
			// The user has no proxy yet.
			proxy = startProxy(userName, appName);
		}
		return (proxy == null) ? null : proxy.name;
	}
	
	public void releaseProxies(String userName) {
		List<Proxy> proxiesToRelease = new ArrayList<>();
		synchronized (activeProxies) {
			for (Proxy proxy: activeProxies) {
				if (userName.equals(proxy.userName)) proxiesToRelease.add(proxy);
			}
		}
		for (Proxy proxy: proxiesToRelease) {
			releaseProxy(proxy, true);
		}
	}
	
	public void releaseProxy(String userName, String appName) {
		Proxy proxy = findProxy(userName, appName);
		if (proxy != null) {
			releaseProxy(proxy, true);
		}
	}
	
	private void releaseProxy(Proxy proxy, boolean async) {
		activeProxies.remove(proxy);
		Runnable r = new Runnable() {
			@Override
			public void run() {
				try {
					dockerClient.stopContainer(proxy.containerId, 3);
					dockerClient.removeContainer(proxy.containerId);
					releasePort(proxy.port);
					log.info(String.format("Proxy released [user: %s] [app: %s] [port: %d]", proxy.userName, proxy.appName, proxy.port));
					eventService.post(EventType.AppStop.toString(), proxy.userName, proxy.appName);
				} catch (Exception e){
					log.error("Failed to stop container " + proxy.name, e);
				}
			}
		};
		if (async) {
			containerKiller.submit(r);
		} else {
			r.run();
		}
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
		
		Proxy proxy = findProxy(userName, appName);
		if (proxy != null) {
			throw new ShinyProxyException("Cannot start container: user " + userName + " already has a running proxy");
		}
		
		proxy = new Proxy();
		proxy.userName = userName;
		proxy.appName = appName;
		proxy.port = getFreePort();
		
		try {
			final Map<String, List<PortBinding>> portBindings = new HashMap<String, List<PortBinding>>();
			List<PortBinding> hostPorts = new ArrayList<PortBinding>();
		    hostPorts.add(PortBinding.of("0.0.0.0", proxy.port));
			portBindings.put("3838", hostPorts);
			final HostConfig hostConfig = HostConfig.builder()
					.portBindings(portBindings)
					.dns(app.getDockerDns())
					.build();
			
			final ContainerConfig containerConfig = ContainerConfig.builder()
				    .hostConfig(hostConfig)
				    .image(app.getDockerImage())
				    .exposedPorts("3838")
				    .cmd(app.getDockerCmd())
				    .env(String.format("SHINYPROXY_USERNAME=%s", userName))
				    .build();
			
			ContainerCreation container = dockerClient.createContainer(containerConfig);
			dockerClient.startContainer(container.id());

			ContainerInfo info = dockerClient.inspectContainer(container.id());
			proxy.name = info.name().substring(1);
			proxy.containerId = container.id();
			proxy.startupTimestamp = System.currentTimeMillis();
		} catch (Exception e) {
			releasePort(proxy.port);
			throw new ShinyProxyException("Failed to start container: " + e.getMessage(), e);
		}

		if (!testContainer(proxy, 20, 500, 5000)) {
			releaseProxy(proxy, true);
			throw new ShinyProxyException("Container did not respond in time");
		}
		
		try {
			URI target = new URI("http://" + environment.getProperty("shiny.proxy.docker.host") + ":" + proxy.port);
			synchronized (mappingListeners) {
				for (MappingListener listener: mappingListeners) {
					listener.mappingAdded(proxy.name, target);
				}
			}
		} catch (URISyntaxException ignore) {}
		
		activeProxies.add(proxy);
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
	
	private boolean testContainer(Proxy proxy, int maxTries, int waitMs, int timeoutMs) {
		String urlString = String.format("http://%s:%d", environment.getProperty("shiny.proxy.docker.host"), proxy.port);
		for (int currentTry = 1; currentTry <= maxTries; currentTry++) {
			try {
				URL testURL = new URL(urlString);
				HttpURLConnection connection = ((HttpURLConnection) testURL.openConnection());
				connection.setConnectTimeout(timeoutMs);
				int responseCode = connection.getResponseCode();
				if (responseCode == 200) return true;
			} catch (Exception e) {
				log.warn(String.format("Container unresponsive, trying again (%d/%d): %s", currentTry, maxTries, urlString));
				try { Thread.sleep(waitMs); } catch (InterruptedException ignore) {}
			}
		}
		return false;
	}

	private int getFreePort() {
		int startPort = Integer.valueOf(environment.getProperty("shiny.proxy.docker.port-range-start"));
		int nextPort = startPort;
		while (occupiedPorts.contains(nextPort)) nextPort++;
		occupiedPorts.add(nextPort);
		return nextPort;
	}
	
	private void releasePort(int port) {
		occupiedPorts.remove(port);
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
