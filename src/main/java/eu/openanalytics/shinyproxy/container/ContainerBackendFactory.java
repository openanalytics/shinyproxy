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

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;

import eu.openanalytics.shinyproxy.container.docker.DockerEngineBackend;
import eu.openanalytics.shinyproxy.container.docker.DockerSwarmBackend;

@Service
public class ContainerBackendFactory extends AbstractFactoryBean<IContainerBackend> implements ApplicationContextAware {
	
	private ApplicationContext applicationContext;
	
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
	
	@Override
	public Class<?> getObjectType() {
		return IContainerBackend.class;
	}

	@Override
	protected IContainerBackend createInstance() throws Exception {
		try {
			return tryCreate(DockerSwarmBackend.class);
		} catch (Exception e) {
			// Fall back to next backend type.
		}
		return tryCreate(DockerEngineBackend.class);
	}

	private IContainerBackend tryCreate(Class<? extends IContainerBackend> backendClass) throws Exception {
		IContainerBackend backend = backendClass.newInstance();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(backend);
		backend.initialize();
		return backend;
	}
}
