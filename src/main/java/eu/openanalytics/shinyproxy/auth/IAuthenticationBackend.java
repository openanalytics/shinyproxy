/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2017 Open Analytics
 *
 * ===========================================================================
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
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
