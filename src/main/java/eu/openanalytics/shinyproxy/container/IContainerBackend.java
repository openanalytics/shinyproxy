/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2017 Open Analytics
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

import java.io.File;
import java.util.function.BiConsumer;

import eu.openanalytics.shinyproxy.ShinyProxyException;

public interface IContainerBackend {

	/**
	 * Initialize this container backend.
	 * This method is called lazily, when the backend is needed for the first time.
	 * 
	 * @throws ShinyProxyException If anything goes wrong during initialization of the backend.
	 */
	public void initialize() throws ShinyProxyException;
	
	/**
	 * Create a new proxy for the given request.
	 * The proxy should be instantiated and prepared, but not yet started.
	 * The {@link IContainerBackend#startProxy(IContainerProxy)} method will be called
	 * shortly after successful creation.
	 * 
	 * @param request The request for a new proxy.
	 * @return A new proxy, ready to be started up.
	 * @throws ShinyProxyException If the creation fails for any reason.
	 */
	public IContainerProxy createProxy(ContainerProxyRequest request) throws ShinyProxyException;

	/**
	 * Start the given proxy, which may take some time depending on the type of backend.
	 * The proxy will be in the {@link ContainerProxyStatus#New} state before entering this method.
	 * When this method returns, the proxy should be in the {@link ContainerProxyStatus#Up} state.
	 * 
	 * @param proxy The proxy to start up.
	 * @throws ShinyProxyException If the startup fails for any reason.
	 */
	public void startProxy(IContainerProxy proxy) throws ShinyProxyException;
	
	/**
	 * Stop the given proxy. Any resources used by the proxy should be released.
	 * 
	 * @param proxy The proxy to stop.
	 * @throws ShinyProxyException If an error occurs while stopping the proxy.
	 */
	public void stopProxy(IContainerProxy proxy) throws ShinyProxyException;
	
	/**
	 * Get a function that will attach the standard output and standard error of
	 * the given proxy's container to two files.
	 * Any stdout/stderr from the container should be written into the files.
	 * 
	 * The function will be executed in a separate thread, and is assumed to block
	 * until the container stops.
	 * 
	 * @param proxy The proxy whose container output should be attached to the files.
	 * @return A function that will attach the output, or null if this backend does
	 * not support output attaching.
	 */
	public BiConsumer<File, File> getOutputAttacher(IContainerProxy proxy);
}
