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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
import org.springframework.security.config.annotation.authentication.configurers.ldap.LdapAuthenticationProviderConfigurer;
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
		LDAPProviderConfig[] configs = LDAPProviderConfig.loadAll(environment);
		for (LDAPProviderConfig cfg: configs) {
			LdapAuthenticationProviderConfigurer<AuthenticationManagerBuilder> configurer = new LdapAuthenticationProviderConfigurer<>();
			
			String[] userDnPatterns = { cfg.userDnPattern };
			if (userDnPatterns[0] == null || userDnPatterns[0].isEmpty()) userDnPatterns = new String[0];

			if (cfg.managerDn != null && cfg.managerDn.isEmpty()) cfg.managerDn = null;
			
			// Manually instantiate contextSource so it can be passed into authoritiesPopulator below.
			DefaultSpringSecurityContextSource contextSource = new DefaultSpringSecurityContextSource(cfg.url);
			if (cfg.managerDn != null) {
				contextSource.setUserDn(cfg.managerDn);
				contextSource.setPassword(cfg.managerPassword);
			}
			contextSource.afterPropertiesSet();

			// Manually instantiate authoritiesPopulator because it uses a customized class.
			CNLdapAuthoritiesPopulator authoritiesPopulator = new CNLdapAuthoritiesPopulator(contextSource, cfg.groupSearchBase);
			authoritiesPopulator.setGroupRoleAttribute("cn");
			authoritiesPopulator.setGroupSearchFilter(cfg.groupSearchFilter);

			configurer
				.userDnPatterns(userDnPatterns)
				.userSearchBase(cfg.userSearchBase)
				.userSearchFilter(cfg.userSearchFilter)
				.ldapAuthoritiesPopulator(authoritiesPopulator)
				.contextSource(contextSource)
				.configure(auth);
		}
	}
	
	private static class LDAPProviderConfig {
		
		public String url;
		public String userDnPattern;
		public String userSearchBase;
		public String userSearchFilter;
		public String groupSearchBase;
		public String groupSearchFilter;
		public String managerDn;
		public String managerPassword;
		
		public static LDAPProviderConfig[] loadAll(Environment env) {
			LDAPProviderConfig single = load(env, -1);
			if (single != null) return new LDAPProviderConfig[] { single };
			
			List<LDAPProviderConfig> providers = new ArrayList<>();
			for (int i=0 ;; i++) {
				LDAPProviderConfig cfg = load(env, i);
				if (cfg == null) break;
				else providers.add(cfg);
			}
			return providers.toArray(new LDAPProviderConfig[providers.size()]);
		}
		
		public static LDAPProviderConfig load(Environment env, int index) {
			String prop = "shiny.proxy.ldap.%s";
			if (index >= 0) prop = String.format("shiny.proxy.ldap[%d]", index) + ".%s";
			
			String url = env.getProperty(String.format(prop, "url"));
			if (url == null) return null;
			
			LDAPProviderConfig cfg = new LDAPProviderConfig();
			cfg.url = url;
			cfg.userDnPattern = env.getProperty(String.format(prop, "user-dn-pattern"));
			cfg.userSearchBase = env.getProperty(String.format(prop, "user-search-base"), "");
			cfg.userSearchFilter = env.getProperty(String.format(prop, "user-search-filter"));
			cfg.groupSearchBase = env.getProperty(String.format(prop, "group-search-base"), "");
			cfg.groupSearchFilter = env.getProperty(String.format(prop, "group-search-filter"), "(uniqueMember={0})");
			cfg.managerDn = env.getProperty(String.format(prop, "manager-dn"));
			cfg.managerPassword = env.getProperty(String.format(prop, "manager-password"));
			
			return cfg;
		}
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
