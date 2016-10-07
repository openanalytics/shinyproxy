package eu.openanalytics.services;

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
import org.springframework.stereotype.Service;

import eu.openanalytics.services.DockerService.Proxy;
import eu.openanalytics.services.EventService.EventType;

@Service
public class UserService implements ApplicationListener<AbstractAuthenticationEvent> {

	private Logger log = Logger.getLogger(UserService.class);

	private Map<String, Long> heartbeatTimestamps = new ConcurrentHashMap<>();
	
	@Inject
	Environment environment;

	@Inject
	DockerService dockerService;

	@Inject
	EventService eventService;
	
	@PostConstruct
	public void init() {
		new Thread(new AppCleaner(), "HeartbeatThread").start();
	}
	
	public String[] getAdminRoles() {
		String[] adminGroups = environment.getProperty("shiny.proxy.ldap.admin-groups", String[].class);
		if (adminGroups == null) adminGroups = new String[0];
		for (int i = 0; i < adminGroups.length; i++) {
			adminGroups[i] = adminGroups[i].toUpperCase();
		}
		return adminGroups;
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
		heartbeatTimestamps.remove(userName);
		dockerService.releaseProxy(userName);
		log.info(String.format("User logged out [user: %s]", userName));
		eventService.post(EventType.Logout.toString(), userName, null);
	}
	
	public void heartbeatReceived(String user, String app) {
		heartbeatTimestamps.put(user, System.currentTimeMillis());
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
						Long lastHeartbeat = heartbeatTimestamps.get(proxy.userName);
						if (lastHeartbeat == null) lastHeartbeat = proxy.startupTimestamp;
						long proxySilence = currentTimestamp - lastHeartbeat;
						if (proxySilence > heartbeatTimeout) {
							log.info(String.format("Releasing inactive proxy [user: %s] [app: %s] [silence: %dms]", proxy.userName, proxy.appName, proxySilence));
							dockerService.releaseProxy(proxy.userName);
							heartbeatTimestamps.remove(proxy.userName);
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
