package eu.openanalytics.auth;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.servlet.ServletException;

import org.keycloak.adapters.AdapterDeploymentContext;
import org.keycloak.adapters.KeycloakConfigResolver;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.adapters.spi.HttpFacade.Request;
import org.keycloak.adapters.spi.KeycloakAccount;
import org.keycloak.adapters.springsecurity.AdapterDeploymentContextFactoryBean;
import org.keycloak.adapters.springsecurity.account.KeycloakRole;
import org.keycloak.adapters.springsecurity.authentication.KeycloakAuthenticationEntryPoint;
import org.keycloak.adapters.springsecurity.authentication.KeycloakAuthenticationProvider;
import org.keycloak.adapters.springsecurity.authentication.KeycloakLogoutHandler;
import org.keycloak.adapters.springsecurity.filter.KeycloakAuthenticationProcessingFilter;
import org.keycloak.adapters.springsecurity.filter.KeycloakPreAuthActionsFilter;
import org.keycloak.adapters.springsecurity.management.HttpSessionManager;
import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.stereotype.Component;

import eu.openanalytics.auth.AuthenticationTypeProxy.IAuthenticationType;

@Component
public class KeycloakAuthenticationType implements IAuthenticationType {

	@Inject
	Environment environment;

	@Inject
	AuthenticationManager authenticationManager;
	
	@Inject
	ApplicationContext ctx;
	
	@Override
	public boolean hasAuthorization() {
		return true;
	}

	@Override
	public void configureHttpSecurity(HttpSecurity http) throws Exception {
		http.formLogin().disable();
		
		http
			.sessionManagement().sessionAuthenticationStrategy(sessionAuthenticationStrategy())
			.and()
			.addFilterBefore(keycloakPreAuthActionsFilter(), LogoutFilter.class)
			.addFilterBefore(keycloakAuthenticationProcessingFilter(), BasicAuthenticationFilter.class)
			.exceptionHandling().authenticationEntryPoint(authenticationEntryPoint())
			.and()
			.logout().addLogoutHandler(keycloakLogoutHandler());
	}

	@Override
	public void configureAuthenticationManagerBuilder(AuthenticationManagerBuilder auth) throws Exception {
		 auth.authenticationProvider(keycloakAuthenticationProvider());
	}

	@Bean
	@ConditionalOnProperty(name="shiny.proxy.authentication", havingValue="keycloak")
	protected KeycloakAuthenticationProcessingFilter keycloakAuthenticationProcessingFilter() throws Exception {
		KeycloakAuthenticationProcessingFilter filter = new KeycloakAuthenticationProcessingFilter(authenticationManager);
		filter.setSessionAuthenticationStrategy(sessionAuthenticationStrategy());
		// Fix: call afterPropertiesSet manually, because Spring doesn't invoke it for some reason.
		filter.setApplicationContext(ctx);
		filter.afterPropertiesSet();
		return filter;
	}

	@Bean
	@ConditionalOnProperty(name="shiny.proxy.authentication", havingValue="keycloak")
	protected KeycloakPreAuthActionsFilter keycloakPreAuthActionsFilter() {
		KeycloakPreAuthActionsFilter filter = new KeycloakPreAuthActionsFilter(httpSessionManager());
		// Fix: call afterPropertiesSet manually, because Spring doesn't invoke it for some reason.
		filter.setApplicationContext(ctx);
		try { filter.afterPropertiesSet(); } catch (ServletException e) {}
		return filter;
	}

	@Bean
	@ConditionalOnProperty(name="shiny.proxy.authentication", havingValue="keycloak")
	protected HttpSessionManager httpSessionManager() {
		return new HttpSessionManager();
	}

	@Bean
	@ConditionalOnProperty(name="shiny.proxy.authentication", havingValue="keycloak")
	protected SessionAuthenticationStrategy sessionAuthenticationStrategy() {
		return new RegisterSessionAuthenticationStrategy(new SessionRegistryImpl());
	}

	@Bean
	@ConditionalOnProperty(name="shiny.proxy.authentication", havingValue="keycloak")
	protected AdapterDeploymentContext adapterDeploymentContext() throws Exception {
		AdapterConfig cfg = new AdapterConfig();
		cfg.setRealm(environment.getProperty("shiny.proxy.keycloak.realm"));
		cfg.setAuthServerUrl(environment.getProperty("shiny.proxy.keycloak.auth-server-url"));
		cfg.setResource(environment.getProperty("shiny.proxy.keycloak.resource"));
		Map<String,Object> credentials = new HashMap<>();
		credentials.put("secret", environment.getProperty("shiny.proxy.keycloak.credentials-secret"));
		cfg.setCredentials(credentials);
		KeycloakDeployment dep = KeycloakDeploymentBuilder.build(cfg);
		AdapterDeploymentContextFactoryBean factoryBean = new AdapterDeploymentContextFactoryBean(new KeycloakConfigResolver() {
			@Override
			public KeycloakDeployment resolve(Request facade) {
				return dep;
			}
		});
		factoryBean.afterPropertiesSet();
		return factoryBean.getObject();
	}

	protected AuthenticationEntryPoint authenticationEntryPoint() throws Exception {
        return new KeycloakAuthenticationEntryPoint(adapterDeploymentContext());
    }
	
	protected KeycloakAuthenticationProvider keycloakAuthenticationProvider() {
		return new KeycloakAuthenticationProvider() {
			@Override
			public Authentication authenticate(Authentication authentication) throws AuthenticationException {
				KeycloakAuthenticationToken token = (KeycloakAuthenticationToken) super.authenticate(authentication);
				List<GrantedAuthority> auth = token.getAuthorities().stream()
						.map(t -> t.getAuthority().toUpperCase())
						.map(a -> a.startsWith("ROLE_") ? a : "ROLE_" + a)
						.map(a -> new KeycloakRole(a))
						.collect(Collectors.toList());
				return new KeycloakAuthenticationToken2(token.getAccount(), auth);
			}
		};
	}
	
	protected KeycloakLogoutHandler keycloakLogoutHandler() throws Exception {
		return new KeycloakLogoutHandler(adapterDeploymentContext());
	}
	
	private static class KeycloakAuthenticationToken2 extends KeycloakAuthenticationToken implements Serializable {
		
		private static final long serialVersionUID = -521347733024996150L;

		public KeycloakAuthenticationToken2(KeycloakAccount account, Collection<? extends GrantedAuthority> authorities) {
			super(account, authorities);
		}
		
		@Override
		public String getName() {
			return getAccount().getKeycloakSecurityContext().getIdToken().getName();
		}
	}
}
