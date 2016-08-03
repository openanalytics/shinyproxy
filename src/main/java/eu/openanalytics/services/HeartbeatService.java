package eu.openanalytics.services;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import eu.openanalytics.services.DockerService.Proxy;

@Service
public class HeartbeatService {

	@Inject
	Environment environment;
	
	@Inject
	DockerService dockerService;
	
	private Logger log = Logger.getLogger(HeartbeatService.class);
	
	private Map<String, Long> heartbeatTimestamps;
	
	@PostConstruct
	public void init() {
		heartbeatTimestamps = new ConcurrentHashMap<>();
		new Thread(new AppCleaner(), "HeartbeatThread").start();
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
