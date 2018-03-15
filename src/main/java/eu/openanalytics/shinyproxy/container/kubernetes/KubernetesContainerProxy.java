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
package eu.openanalytics.shinyproxy.container.kubernetes;

import eu.openanalytics.shinyproxy.container.AbstractContainerProxy;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;

public class KubernetesContainerProxy extends AbstractContainerProxy {

	private Pod pod;
	private Service service;
	
	public Pod getPod() {
		return pod;
	}
	
	public void setPod(Pod pod) {
		this.pod = pod;
	}
	
	public Service getService() {
		return service;
	}
	
	public void setService(Service service) {
		this.service = service;
	}
}
