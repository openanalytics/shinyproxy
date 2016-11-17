package eu.openanalytics.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.event.AbstractAuthenticationEvent;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import eu.openanalytics.services.AppService.ShinyApp;
import eu.openanalytics.services.DockerService.Proxy;
import eu.openanalytics.services.EventService.EventType;

@Service
public class UserService implements ApplicationListener<AbstractAuthenticationEvent> {

	private Logger log = Logger.getLogger(UserService.class);

	private Map<HeartbeatKey, Long> heartbeatTimestamps = new ConcurrentHashMap<>();
	
	@Inject
	Environment environment;

	@Inject
	DockerService dockerService;

	@Inject
	EventService eventService;
	
	@Inject
	AppService appService;
	
	@PostConstruct
	public void init() {
		new Thread(new AppCleaner(), "HeartbeatThread").start();
	}
	
	public String[] getAdminRoles() {
		String[] adminGroups = environment.getProperty("shiny.proxy.admin-groups", String[].class);
		if (adminGroups == null) adminGroups = new String[0];
		for (int i = 0; i < adminGroups.length; i++) {
			adminGroups[i] = adminGroups[i].toUpperCase();
		}
		return adminGroups;
	}
	
	public List<ShinyApp> getAccessibleApps(Authentication principalAuth) {
		List<ShinyApp> accessibleApps = new ArrayList<>();
		for (ShinyApp app: appService.getApps()) {
			if (canAccess(principalAuth, app.getName())) accessibleApps.add(app);
		}
		return accessibleApps;
	}
	
	private boolean canAccess(Authentication principalAuth, String appName) {
		ShinyApp app = appService.getApp(appName);
		if (app == null) return false;
		if (app.getGroups() == null || app.getGroups().length == 0) return true;
		if (principalAuth == null) return true;
		
		for (GrantedAuthority auth: principalAuth.getAuthorities()) {
			String role = auth.getAuthority().toUpperCase();
			if (role.startsWith("ROLE_")) role = role.substring(5);
			for (String group: app.getGroups()) {
				if (group.equalsIgnoreCase(role)) return true;
			}
		}
		return false;
	}

	@Override
	public void onApplicationEvent(AbstractAuthenticationEvent event) {
		Authentication source = event.getAuthentication();
		if (event instanceof AbstractAuthenticationFailureEvent) {
			Exception e = ((AbstractAuthenticationFailureEvent) event).getException();
			log.info(String.format("Authentication failure [user: %s] [error: %s]", source.getName(), e.getMessage()));
		} else if (event instanceof AuthenticationSuccessEvent) {
			String userName = source.getName();
			log.info(String.format("User logged in [user: %s]", userName));
			eventService.post(EventType.Login.toString(), userName, null);
		}
	}

	public void logout(String userName) {
		List<HeartbeatKey> keysToRemove = new ArrayList<>();
		for (HeartbeatKey key: heartbeatTimestamps.keySet()) {
			if (key.userName.equals(userName)) keysToRemove.add(key);
		}
		for (HeartbeatKey key: keysToRemove) {
			heartbeatTimestamps.remove(key);
		}
		dockerService.releaseProxies(userName);
		log.info(String.format("User logged out [user: %s]", userName));
		eventService.post(EventType.Logout.toString(), userName, null);
	}
	
	public void heartbeatReceived(String user, String app) {
		heartbeatTimestamps.put(getKey(user, app), System.currentTimeMillis());
	}
	
	private class AppCleaner implements Runnable {
		@Override
		public void run() {
			long cleanupInterval = 2 * Long.parseLong(environment.getProperty("shiny.proxy.heartbeat-rate", "10000"));
			long heartbeatTimeout = Long.parseLong(environment.getProperty("shiny.proxy.heartbeat-timeout", "60000"));
			
			while (true) {
				try {
					long currentTimestamp = System.currentTimeMillis();
					for (Proxy proxy: dockerService.listProxies()) {
						HeartbeatKey key = getKey(proxy.userName, proxy.appName);
						Long lastHeartbeat = heartbeatTimestamps.get(key);
						if (lastHeartbeat == null) lastHeartbeat = proxy.startupTimestamp;
						long proxySilence = currentTimestamp - lastHeartbeat;
						if (proxySilence > heartbeatTimeout) {
							log.info(String.format("Releasing inactive proxy [user: %s] [app: %s] [silence: %dms]", proxy.userName, proxy.appName, proxySilence));
							dockerService.releaseProxy(proxy.userName, proxy.appName);
							heartbeatTimestamps.remove(key);
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
	
	private HeartbeatKey getKey(String userName, String appName) {
		return new HeartbeatKey(userName, appName);
	}
	
	
	private static class HeartbeatKey {
		
		private String userName;
		private String appName;
		
		public HeartbeatKey(String userName, String appName) {
			this.userName = userName;
			this.appName = appName;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((appName == null) ? 0 : appName.hashCode());
			result = prime * result + ((userName == null) ? 0 : userName.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			HeartbeatKey other = (HeartbeatKey) obj;
			if (appName == null) {
				if (other.appName != null)
					return false;
			} else if (!appName.equals(other.appName))
				return false;
			if (userName == null) {
				if (other.userName != null)
					return false;
			} else if (!userName.equals(other.userName))
				return false;
			return true;
		}
	}
}
