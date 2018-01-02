package eu.openanalytics.shinyproxy.auth;

import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

public interface IAuthenticationBackend {

	public String getName();
	
	public boolean hasAuthorization();

	public void configureHttpSecurity(HttpSecurity http) throws Exception;

	public void configureAuthenticationManagerBuilder(AuthenticationManagerBuilder auth) throws Exception;

}
