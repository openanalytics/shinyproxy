/**
 * Copyright 2016 Open Analytics, Belgium
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.openanalytics.auth;

import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.env.Environment;
import org.springframework.ldap.core.ContextSource;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;
import org.springframework.stereotype.Component;

import eu.openanalytics.auth.AuthenticationTypeProxy.IAuthenticationType;

@Component
public class LDAPAuthenticationType implements IAuthenticationType {

	@Inject
	Environment environment;
	
	@Override
	public boolean hasAuthorization() {
		return true;
	}
	
	@Override
	public void configureHttpSecurity(HttpSecurity http) throws Exception {
		// Nothing to do.
	}

	@Override
	public void configureAuthenticationManagerBuilder(AuthenticationManagerBuilder auth) throws Exception {
		String[] userDnPatterns = { environment.getProperty("shiny.proxy.ldap.user-dn-pattern") };
		if (userDnPatterns[0] == null || userDnPatterns[0].isEmpty()) userDnPatterns = new String[0];

		String managerDn = environment.getProperty("shiny.proxy.ldap.manager-dn");
		if (managerDn != null && managerDn.isEmpty()) managerDn = null;
		
		// Manually instantiate contextSource so it can be passed into authoritiesPopulator below.
		String ldapUrl = environment.getProperty("shiny.proxy.ldap.url");
		DefaultSpringSecurityContextSource contextSource = new DefaultSpringSecurityContextSource(ldapUrl);
		if (managerDn != null) {
			contextSource.setUserDn(managerDn);
			contextSource.setPassword(environment.getProperty("shiny.proxy.ldap.manager-password"));
		}
		contextSource.afterPropertiesSet();

		// Manually instantiate authoritiesPopulator because it uses a customized class.
		CNLdapAuthoritiesPopulator authoritiesPopulator = new CNLdapAuthoritiesPopulator(
				contextSource,
				environment.getProperty("shiny.proxy.ldap.group-search-base", ""));
		authoritiesPopulator.setGroupRoleAttribute("cn");
		authoritiesPopulator.setGroupSearchFilter(environment.getProperty("shiny.proxy.ldap.group-search-filter", "(uniqueMember={0})"));

		auth
			.ldapAuthentication()
				.userDnPatterns(userDnPatterns)
				.userSearchBase(environment.getProperty("shiny.proxy.ldap.user-search-base", ""))
				.userSearchFilter(environment.getProperty("shiny.proxy.ldap.user-search-filter"))
				.ldapAuthoritiesPopulator(authoritiesPopulator)
				.contextSource(contextSource);
	}
	
	private static class CNLdapAuthoritiesPopulator extends DefaultLdapAuthoritiesPopulator {

		private static final Log logger = LogFactory.getLog(DefaultLdapAuthoritiesPopulator.class);

		public CNLdapAuthoritiesPopulator(ContextSource contextSource, String groupSearchBase) {
			super(contextSource, groupSearchBase);
		}

		@Override
		public Set<GrantedAuthority> getGroupMembershipRoles(String userDn, String username) {
			if (getGroupSearchBase() == null) {
				return new HashSet<GrantedAuthority>();
			}

			Set<GrantedAuthority> authorities = new HashSet<GrantedAuthority>();

			if (logger.isDebugEnabled()) {
				logger.debug("Searching for roles for user '" + username + "', DN = " + "'"
						+ userDn + "', with filter " + getGroupSearchFilter()
						+ " in search base '" + getGroupSearchBase() + "'");
			}

			// Here's the modification: added {2}, which refers to the user cn if available.
			Set<String> userRoles = getLdapTemplate().searchForSingleAttributeValues(
					getGroupSearchBase(), getGroupSearchFilter(),
					new String[] { userDn, username, getCn(userDn) }, getGroupRoleAttribute());

			if (logger.isDebugEnabled()) {
				logger.debug("Roles from search: " + userRoles);
			}

			for (String role : userRoles) {

				if (isConvertToUpperCase()) {
					role = role.toUpperCase();
				}

				authorities.add(new SimpleGrantedAuthority(getRolePrefix() + role));
			}

			return authorities;
		}

		private String getCn(String dn) {
			try {
				LdapName ln = new LdapName(dn);
				for (Rdn rdn : ln.getRdns()) {
					if (rdn.getType().equalsIgnoreCase("CN")) {
						return rdn.getValue().toString();
					}
				}
			} catch (InvalidNameException e) {}
			return "";
		}
	}
}
