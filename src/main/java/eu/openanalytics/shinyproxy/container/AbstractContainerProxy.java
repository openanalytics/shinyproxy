package eu.openanalytics.shinyproxy.container;

import eu.openanalytics.shinyproxy.services.AppService.ShinyApp;

public abstract class AbstractContainerProxy implements IContainerProxy {

	private String name;
	private String containerId;
	private String userId;
	private ShinyApp app;
	private String target;
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
