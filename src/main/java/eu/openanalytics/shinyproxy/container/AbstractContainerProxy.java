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

public abstract class AbstractContainerProxy implements IContainerProxy {

	private String name;
	private String containerId;
	private String userId;
	private ShinyApp app;
	
	private String target;
	private int port;
	
	private long startupTimestamp;

	private ContainerProxyStatus status;

	@Override
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String getContainerId() {
		return containerId;
	}
	
	public void setContainerId(String containerId) {
		this.containerId = containerId;
	}
	
	@Override
	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	@Override
	public ShinyApp getApp() {
		return app;
	}

	public void setApp(ShinyApp app) {
		this.app = app;
	}

	@Override
	public String getTarget() {
		return target;
	}

	public void setTarget(String target) {
		this.target = target;
	}

	public int getPort() {
		return port;
	}
	
	public void setPort(int port) {
		this.port = port;
	}
	
	@Override
	public long getStartupTimestamp() {
		return startupTimestamp;
	}

	public void setStartupTimestamp(long startupTimestamp) {
		this.startupTimestamp = startupTimestamp;
	}

	@Override
	public String getUptime() {
		long uptimeSec = (System.currentTimeMillis() - startupTimestamp)/1000;
		return String.format("%d:%02d:%02d", uptimeSec/3600, (uptimeSec%3600)/60, uptimeSec%60);
	}

	@Override
	public ContainerProxyStatus getStatus() {
		return status;
	}

	public void setStatus(ContainerProxyStatus status) {
		this.status = status;
	}
}
