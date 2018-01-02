package eu.openanalytics.shinyproxy.auth;

import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

public interface IAuthenticationBackend {

	/**
	 * Get the name for this authentication backend, as used in the application.yml file.
	 */
	public String getName();

	/**
	 * Return true if this authentication backend supports authorization.
	 * In this context, authorization means the separation of permission levels
	 * via groups.
	 * 
	 * If there is no authorization, all users have the same (administrator) permissions.
	 */
	public boolean hasAuthorization();

	/**
	 * Perform customization on the http level, such as filters and login forms.
	 */
	public void configureHttpSecurity(HttpSecurity http) throws Exception;

	/**
	 * Perform customization on the authentication manager level, such as authentication
	 * handling and authority population.
	 */
	public void configureAuthenticationManagerBuilder(AuthenticationManagerBuilder auth) throws Exception;

}
