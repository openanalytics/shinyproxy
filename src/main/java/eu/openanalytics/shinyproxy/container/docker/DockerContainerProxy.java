package eu.openanalytics.shinyproxy.container.docker;

import eu.openanalytics.shinyproxy.container.AbstractContainerProxy;

public class DockerContainerProxy extends AbstractContainerProxy {

	private int port;
	private String serviceId; // only used in swarm backends.
	
	public int getPort() {
		return port;
	}
	
	public void setPort(int port) {
		this.port = port;
	}
	
	public String getServiceId() {
		return serviceId;
	}
	
	public void setServiceId(String serviceId) {
		this.serviceId = serviceId;
	}
}
