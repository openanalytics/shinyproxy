package eu.openanalytics.services;

import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.event.AbstractAuthenticationEvent;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class UserService implements ApplicationListener<AbstractAuthenticationEvent> {

	private Logger log = Logger.getLogger(UserService.class);

	@Inject
	Environment environment;

	public String[] getAdminRoles() {
		String[] adminGroups = environment.getProperty("shiny.proxy.ldap.admin-groups", String[].class);
		if (adminGroups == null) adminGroups = new String[0];
		for (int i = 0; i < adminGroups.length; i++) {
			adminGroups[i] = adminGroups[i].toUpperCase();
		}
		return adminGroups;
	}

	@Override
	public void onApplicationEvent(AbstractAuthenticationEvent event) {
		Authentication source = event.getAuthentication();
		if (event instanceof AbstractAuthenticationFailureEvent) {
			Exception e = ((AbstractAuthenticationFailureEvent) event).getException();
			log.info(String.format("Authentication failure [user: %s] [error: %s]", source.getName(), e.getMessage()));
		} else if (event instanceof AuthenticationSuccessEvent) {
			log.info(String.format("User logged in [user: %s]", source.getName()));
		}
	}

}
