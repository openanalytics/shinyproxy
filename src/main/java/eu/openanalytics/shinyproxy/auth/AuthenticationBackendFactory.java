package eu.openanalytics.shinyproxy.auth;

import javax.inject.Inject;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import eu.openanalytics.shinyproxy.auth.impl.KeycloakAuthenticationType;
import eu.openanalytics.shinyproxy.auth.impl.LDAPAuthenticationType;
import eu.openanalytics.shinyproxy.auth.impl.NoneAuthenticationType;
import eu.openanalytics.shinyproxy.auth.impl.SimpleAuthenticationType;
import eu.openanalytics.shinyproxy.auth.impl.SocialAuthenticationType;

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
		case LDAPAuthenticationType.NAME:
			backend = new LDAPAuthenticationType();
			break;
		case SimpleAuthenticationType.NAME:
			backend = new SimpleAuthenticationType();
			break;
		case SocialAuthenticationType.NAME:
			backend = new SocialAuthenticationType();
			break;
		case KeycloakAuthenticationType.NAME:
			backend = new KeycloakAuthenticationType();
			break;
		case NoneAuthenticationType.NAME:
			backend = new NoneAuthenticationType();
		}
		if (backend == null) throw new RuntimeException("Unknown authentication type:" + type);
		
		applicationContext.getAutowireCapableBeanFactory().autowireBean(backend);
		return backend;
	}

}
