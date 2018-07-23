package eu.openanalytics.shinyproxy;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import eu.openanalytics.containerproxy.service.HeartbeatService;

@Configuration
public class ShinyProxyConfiguration {

	@Inject
	private Environment environment;
	
	@Inject
	private HeartbeatService heartbeatService;
	
	@PostConstruct
	public void init() {
		// Enable heartbeat unless explicitly disabled.
		boolean enabled = Boolean.valueOf(environment.getProperty("proxy.heartbeat-enabled", "true"));
		heartbeatService.setEnabled(enabled);
	}
}
