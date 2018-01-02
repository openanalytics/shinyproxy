package eu.openanalytics.shinyproxy.auth;

import javax.inject.Inject;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import eu.openanalytics.shinyproxy.auth.impl.KeycloakAuthenticationBackend;
import eu.openanalytics.shinyproxy.auth.impl.LDAPAuthenticationBackend;
import eu.openanalytics.shinyproxy.auth.impl.NoAuthenticationBackend;
import eu.openanalytics.shinyproxy.auth.impl.SimpleAuthenticationBackend;
import eu.openanalytics.shinyproxy.auth.impl.SocialAuthenticationBackend;

@Service(value="authenticationBackend")
public class AuthenticationBackendFactory extends AbstractFactoryBean<IAuthenticationBackend> implements ApplicationContextAware {

	@Inject
	private Environment environment;
	
	private ApplicationContext applicationContext;
	
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	@Override
	public Class<?> getObjectType() {
		return IAuthenticationBackend.class;
	}

	@Override
	protected IAuthenticationBackend createInstance() throws Exception {
		IAuthenticationBackend backend = null;

		String type = environment.getProperty("shiny.proxy.authentication", "none");
		switch (type) {
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
			backend = new KeycloakAuthenticationBackend();
			break;
		case NoAuthenticationBackend.NAME:
			backend = new NoAuthenticationBackend();
		}
		if (backend == null) throw new RuntimeException("Unknown authentication type:" + type);
		
		applicationContext.getAutowireCapableBeanFactory().autowireBean(backend);
		return backend;
	}

}
