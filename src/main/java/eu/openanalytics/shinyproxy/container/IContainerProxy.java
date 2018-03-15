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

import eu.openanalytics.shinyproxy.services.AppService.ShinyApp;

public interface IContainerProxy {

	/**
	 * Get the user-friendly name of this proxy.
	 * Two running proxies cannot have the same name.
	 */
	public String getName();

	/**
	 * Get the unique container ID of this proxy.
	 */
	public String getContainerId();
	
	/**
	 * Get the unique ID of the user owning this proxy.
	 */
	public String getUserId();

	/**
	 * Get the ShinyApp that this proxy is configured for.
	 */
	public ShinyApp getApp();
	
	/**
	 * Get the target URL that this proxy proxies to.
	 */
	public String getTarget();
	
	/**
	 * Get the timestamp (millis since epoch) when this proxy was started.
	 */
	public long getStartupTimestamp();
	
	/**
	 * Get the uptime (in a user-friendly format) of this proxy.
	 */
	public String getUptime();
	
	/**
	 * Get the current status of this proxy.
	 */
	public ContainerProxyStatus getStatus();

}
