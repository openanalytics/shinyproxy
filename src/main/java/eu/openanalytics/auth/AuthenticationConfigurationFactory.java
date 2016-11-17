package eu.openanalytics.auth;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.env.Environment;
import org.springframework.ldap.core.ContextSource;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configurers.provisioning.InMemoryUserDetailsManagerConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;

public class AuthenticationConfigurationFactory {
	
	private enum AuthType {
		none,
		simple,
		ldap;
		
		public static AuthType get(String name) {
			for (AuthType value: values()) {
				if (value.toString().equalsIgnoreCase(name)) return value;
			}
			return none;
		}
	}
	
	public static boolean hasAuth(Environment environment) {
		AuthType authType = AuthType.get(environment.getProperty("shiny.proxy.authentication", ""));
		return authType != AuthType.none;
	}
	
	public static void configure(AuthenticationManagerBuilder auth, Environment environment) throws Exception {
		AuthType authType = AuthType.get(environment.getProperty("shiny.proxy.authentication", ""));
		if (authType == AuthType.simple) configureSimple(auth, environment);
		if (authType == AuthType.ldap) configureLDAP(auth, environment);
	}
	
	private static void configureSimple(AuthenticationManagerBuilder auth, Environment environment) throws Exception {
		InMemoryUserDetailsManagerConfigurer<AuthenticationManagerBuilder> userDetails = auth.inMemoryAuthentication();
		int i=0;
		SimpleUser user = loadUser(i++, environment);
		while (user != null) {
			userDetails.withUser(user.name).password(user.password).roles(user.roles);
			user = loadUser(i++, environment);
		}
	}
	
	private static SimpleUser loadUser(int index, Environment environment) {
		String userName = environment.getProperty(String.format("shiny.proxy.users[%d].name", index));
		if (userName == null) return null;
		String password = environment.getProperty(String.format("shiny.proxy.users[%d].password", index));
		String[] roles = environment.getProperty(String.format("shiny.proxy.users[%d].groups", index), String[].class);
		roles = Arrays.stream(roles).map(s -> s.toUpperCase()).toArray(i -> new String[i]);
		return new SimpleUser(userName, password, roles);
	}
	
	private static class SimpleUser {
		
		public String name;
		public String password;
		public String[] roles;
		
		public SimpleUser(String name, String password, String[] roles) {
			this.name = name;
			this.password = password;
			this.roles = roles;
		}
		
	}
	
	private static void configureLDAP(AuthenticationManagerBuilder auth, Environment environment) throws Exception {
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
