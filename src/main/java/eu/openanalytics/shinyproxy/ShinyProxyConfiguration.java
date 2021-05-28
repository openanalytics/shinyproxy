/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2021 Open Analytics
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
package eu.openanalytics.shinyproxy;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValueKeyRegistry;
import eu.openanalytics.shinyproxy.runtimevalues.PublicPathKey;
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

	static {
		RuntimeValueKeyRegistry.addRuntimeValueKey(PublicPathKey.inst);
	}
}
