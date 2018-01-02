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
