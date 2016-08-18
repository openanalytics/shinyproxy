package eu.openanalytics.services;

import javax.inject.Inject;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class UserService {

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
}
