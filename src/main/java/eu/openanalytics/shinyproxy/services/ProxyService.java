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
package eu.openanalytics.shinyproxy.services;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.util.WebUtils;

import eu.openanalytics.shinyproxy.ShinyProxyException;
import eu.openanalytics.shinyproxy.container.ContainerProxyRequest;
import eu.openanalytics.shinyproxy.container.ContainerProxyStatus;
import eu.openanalytics.shinyproxy.container.IContainerBackend;
import eu.openanalytics.shinyproxy.container.IContainerProxy;
import eu.openanalytics.shinyproxy.services.AppService.ShinyApp;
import eu.openanalytics.shinyproxy.services.EventService.EventType;
import eu.openanalytics.shinyproxy.util.Utils;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.servlet.handlers.ServletRequestContext;

@Service
public class ProxyService {
		
	private Logger log = Logger.getLogger(ProxyService.class);

	private List<IContainerProxy> activeProxies = Collections.synchronizedList(new ArrayList<>());
	
	private Map<IContainerProxy, Long> proxyHeartbeats = Collections.synchronizedMap(new HashMap<>());
	private Map<IContainerProxy, Set<String>> proxySessionIds = Collections.synchronizedMap(new HashMap<>());
	
	private List<MappingListener> mappingListeners = Collections.synchronizedList(new ArrayList<>());
	
	private ExecutorService containerKiller = Executors.newSingleThreadExecutor();
	
	@Inject
	private IContainerBackend backend;
	
	@Inject
	private Environment environment;
	
	@Inject
	private AppService appService;
	
	@Inject
	private EventService eventService;
	
	@Inject
	private LogService logService;
	
	@PostConstruct
	public void init() {
		Thread heartbeatThread = new Thread(new AppCleaner(), "HeartbeatThread");
		heartbeatThread.setDaemon(true);
		//heartbeatThread.start();
	}
	
	@PreDestroy
	public void shutdown() {
		containerKiller.shutdown();
		for (IContainerProxy proxy: getProxies(p -> true)) backend.stopProxy(proxy);
	}
	
	public List<IContainerProxy> listProxies() {
		return getProxies(p -> true);
	}
	
	public String getMapping(HttpServletRequest request, String userName, String appName, boolean startNew) {
		/*waitForLaunchingProxy(userName, appName);
		IContainerProxy proxy = findProxy(userName, appName);
		if (proxy == null && startNew) {
			// The user has no proxy yet.
			proxy = startProxy(userName, appName);
		}
		if (proxy == null) {
			log.info("getMapping : proxy=null");
			return null;
		} else {
			Set<String> sessionIds = proxySessionIds.get(proxy);
			if (sessionIds == null) {
				sessionIds = new HashSet<>();
				proxySessionIds.put(proxy, sessionIds);
			}
			sessionIds.add(getCurrentSessionId(request));
			return proxy.getName();
		}*/
		return appName;
	}
	
	public boolean sessionOwnsProxy(HttpServerExchange exchange) {
		return true;
		/*
		log.info("sessionOwnsProxy started: getRelativePath=" + exchange.getRelativePath());
		String sessionId = getCurrentSessionId(exchange);
		log.info("sessionOwnsProxy : sessionId=" + sessionId);
		if (sessionId == null) return false;
		
		String proxyName = exchange.getRelativePath();
		
		log.info("sessionOwnsProxy finished: proxyName=" + proxyName);
		return !getProxies(p -> matchesSessionId(p, sessionId) && proxyName.startsWith("/" + p.getName())).isEmpty();*/
	}
	
	public void releaseProxies(String userName) {
		/*for (IContainerProxy proxy: getProxies(p -> userName.equals(p.getUserId()))) {
			releaseProxy(proxy, true);
		}*/
	}
	
	public void releaseProxy(String userName, String appName) {
		IContainerProxy proxy = findProxy(userName, appName);
		if (proxy != null) releaseProxy(proxy, true);
	}
	
	private void releaseProxy(IContainerProxy proxy, boolean async) {
		/*activeProxies.remove(proxy);
		
		Runnable releaser = () -> {
			try {
				backend.stopProxy(proxy);
				log.info(String.format("Proxy released [user: %s] [app: %s]", proxy.getUserId(), proxy.getApp().getName()));
				eventService.post(EventType.AppStop.toString(), proxy.getUserId(), proxy.getApp().getName());
			} catch (Exception e){
				log.error("Failed to release proxy " + proxy.getName(), e);
			}
		};
		if (async) containerKiller.submit(releaser);
		else releaser.run();
		
		synchronized (mappingListeners) {
			for (MappingListener listener: mappingListeners) {
				listener.mappingRemoved(proxy.getName());
			}
		}*/
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
	
	private IContainerProxy startProxy(String userName, String appName) {
		/*log.info("startProxy started");
		List<ShinyApp> apps = appService.getApps();
		log.info("startProxy apps.toString(): " + apps.toString());*/
		ShinyApp app = appService.getApp(appName);
		/*if (app == null) {
			throw new ShinyProxyException("Cannot start container: unknown application: " + appName);
		}
		
		if (findProxy(userName, appName) != null) {
			throw new ShinyProxyException("Cannot start container: user " + userName + " already has an active proxy for " + appName);
		}*/
		
		ContainerProxyRequest request = new ContainerProxyRequest();
		request.userId = userName;
		request.app = app;

		IContainerProxy proxy = backend.createProxy(request);
		/*activeProxies.add(proxy);
		try {
			backend.startProxy(proxy);			
		} finally {
			if (proxy.getStatus() != ContainerProxyStatus.Up) activeProxies.remove(proxy);
		}
		
		try {
			URI target = new URI(proxy.getTarget());
			//URI target = new URI("http://192.168.233.128:3838");
			synchronized (mappingListeners) {
				for (MappingListener listener: mappingListeners) {
					listener.mappingAdded(proxy.getName(), target);
					log.info("startProxy(): proxy name:" + proxy.getName() + " target:" + target.toString());
				}
			}
		} catch (URISyntaxException ignore) {}
		
		if (logService.isContainerLoggingEnabled()) {
			BiConsumer<File, File> outputAttacher = backend.getOutputAttacher(proxy);
			if (outputAttacher == null) {
				log.warn("Cannot log container output: " + backend.getClass() + " does not support output attaching.");
			} else {
				logService.attachToOutput(proxy, outputAttacher);
			}
		}
		
		log.info(String.format("Proxy activated [user: %s] [app: %s]", userName, appName));
		eventService.post(EventType.AppStart.toString(), userName, appName);
		log.info("startProxy finished");*/
		return proxy;
	}
	
	private void waitForLaunchingProxy(String userName, String appName) {
		int totalWaitMs = Integer.parseInt(environment.getProperty("shiny.proxy.container-wait-time", "20000"));
		int waitMs = Math.min(2000, totalWaitMs);
		int maxTries = totalWaitMs / waitMs;
		
		boolean mayProceed = Utils.retry(i -> {
			return getProxies(p -> p.getStatus() == ContainerProxyStatus.Starting && isUserProxy(p, userName, appName)).isEmpty();
		}, maxTries, waitMs);
		
		if (!mayProceed) throw new ShinyProxyException("Cannot proceed: waiting for proxy to launch");
	}
	
	private IContainerProxy findProxy(String userName, String appName) {
		return getProxies(proxy -> isUserProxy(proxy, userName, appName)).stream().findAny().orElse(null);
	}
	
	private List<IContainerProxy> getProxies(Predicate<IContainerProxy> filter) {
		List<IContainerProxy> matches = new ArrayList<>();
		synchronized (activeProxies) {
			for (IContainerProxy proxy: activeProxies) {
				if (filter.test(proxy)) matches.add(proxy);
			}
		}
		return matches;
	}
	
	private boolean isUserProxy(IContainerProxy proxy, String userId, String appName) {
		return userId.equals(proxy.getUserId()) && appName.equals(proxy.getApp().getName());
	}
	
	private boolean matchesSessionId(IContainerProxy proxy, String sessionId) {
		Set<String> sessionIds = proxySessionIds.get(proxy);
		if (sessionIds == null || sessionIds.isEmpty()) return false;
		return sessionIds.contains(sessionId);
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
		IContainerProxy proxy = findProxy(user, app);
		if (proxy != null) proxyHeartbeats.put(proxy, System.currentTimeMillis());
	}

	private class AppCleaner implements Runnable {
		@Override
		public void run() {
			long cleanupInterval = 2 * Long.parseLong(environment.getProperty("shiny.proxy.heartbeat-rate", "10000"));
			long heartbeatTimeout = Long.parseLong(environment.getProperty("shiny.proxy.heartbeat-timeout", "60000"));
			
			while (true) {
				try {
					long currentTimestamp = System.currentTimeMillis();
					for (IContainerProxy proxy: getProxies(p -> p.getStatus() == ContainerProxyStatus.Up)) {
						Long lastHeartbeat = proxyHeartbeats.get(proxy);
						if (lastHeartbeat == null) lastHeartbeat = proxy.getStartupTimestamp();
						long proxySilence = currentTimestamp - lastHeartbeat;
						if (proxySilence > heartbeatTimeout) {
							log.info(String.format("Releasing inactive proxy [user: %s] [app: %s] [silence: %dms]", proxy.getUserId(), proxy.getApp().getName(), proxySilence));
							//releaseProxy(proxy, true);
						}
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
