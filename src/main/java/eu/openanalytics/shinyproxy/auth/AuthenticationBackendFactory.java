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
package eu.openanalytics.shinyproxy.auth;

import javax.inject.Inject;

import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import eu.openanalytics.shinyproxy.auth.impl.KeycloakAuthenticationBackend;
import eu.openanalytics.shinyproxy.auth.impl.LDAPAuthenticationBackend;
import eu.openanalytics.shinyproxy.auth.impl.NoAuthenticationBackend;
import eu.openanalytics.shinyproxy.auth.impl.SimpleAuthenticationBackend;
import eu.openanalytics.shinyproxy.auth.impl.SocialAuthenticationBackend;

/**
 * Instantiates an appropriate authentication backend depending on the application configuration.
 * 
 * The keycloak backend is a special case because it defines a set of beans by itself, and cannot be instantiated manually by us.
 * That's also why the authenticationBackend bean is Primary, to avoid conflict with the keycloakBackend bean.
 */
@Service(value="authenticationBackend")
@Primary
public class AuthenticationBackendFactory extends AbstractFactoryBean<IAuthenticationBackend> {

	@Inject
	private Environment environment;
	
	@Inject
	private ApplicationContext applicationContext;
	
	@Inject
	private KeycloakAuthenticationBackend keycloakBackend;
	
	@Override
	public Class<?> getObjectType() {
		return IAuthenticationBackend.class;
	}

	@Override
	protected IAuthenticationBackend createInstance() throws Exception {
		IAuthenticationBackend backend = null;

		String type = environment.getProperty("shiny.proxy.authentication", "none");
		switch (type) {
		case NoAuthenticationBackend.NAME:
			backend = new NoAuthenticationBackend();
			break;
		case LDAPAuthenticationBackend.NAME:
			backend = new LDAPAuthenticationBackend();
			break;
		case SimpleAuthenticationBackend.NAME:
			backend = new SimpleAuthenticationBackend();
			break;
		case SocialAuthenticationBackend.NAME:
			backend = new SocialAuthenticationBackend();
			break;
		case KeycloakAuthenticationBackend.NAME:
			return keycloakBackend;
		}
		if (backend == null) throw new RuntimeException("Unknown authentication type:" + type);
		
		applicationContext.getAutowireCapableBeanFactory().autowireBean(backend);
		return backend;
	}

}
