/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2023 Open Analytics
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
package eu.openanalytics.shinyproxy;

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.security.ICustomSecurityConfig;
import eu.openanalytics.containerproxy.service.UserService;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static eu.openanalytics.containerproxy.ui.AuthController.AUTH_SUCCESS_URL_SESSION_ATTR;

@Component
public class UISecurityConfig implements ICustomSecurityConfig {

    @Inject
    private IAuthenticationBackend auth;

    @Inject
    private UserService userService;

    @Inject
    @Lazy
    private SavedRequestAwareAuthenticationSuccessHandler savedRequestAwareAuthenticationSuccessHandler;

    @Override
    public void apply(HttpSecurity http) throws Exception {
        if (auth.hasAuthorization()) {

            // Limit access to the app pages according to spec permissions
            http.authorizeRequests().antMatchers("/app/{specId}/**").access("@proxyAccessControlService.canAccessOrHasExistingProxy(authentication, #specId)");
            http.authorizeRequests().antMatchers("/app_i/{specId}/**").access("@proxyAccessControlService.canAccessOrHasExistingProxy(authentication, #specId)");
            http.authorizeRequests().antMatchers("/app_direct/{specId}/**").access("@proxyAccessControlService.canAccessOrHasExistingProxy(authentication, #specId)");
            http.authorizeRequests().antMatchers("/app_direct_i/{specId}/**").access("@proxyAccessControlService.canAccessOrHasExistingProxy(authentication, #specId)");

            http.addFilterAfter(new AuthenticationRequiredFilter(), ExceptionTranslationFilter.class);

            savedRequestAwareAuthenticationSuccessHandler.setRedirectStrategy(new DefaultRedirectStrategy() {
                @Override
                public void sendRedirect(HttpServletRequest request, HttpServletResponse response, String url) throws IOException {
                    String redirectUrl = calculateRedirectUrl(request.getContextPath(), url);
                    AppRequestInfo appRequestInfo = AppRequestInfo.fromURI(redirectUrl);
                    if (appRequestInfo != null) {
                        // before auth, the user tried to open the page of an app, redirect back to that app
                        // (we don't redirect to any other app, see  #30648 and #28624)
                        request.getSession().setAttribute(AUTH_SUCCESS_URL_SESSION_ATTR, url);
                    }
                    response.sendRedirect(ServletUriComponentsBuilder.fromCurrentContextPath().path("/auth-success").build().toUriString());
                }
            });
        }
        // Limit access to the admin pages
        http.authorizeRequests().antMatchers("/admin").access("@userService.isAdmin()");
        http.authorizeRequests().antMatchers("/admin/data").access("@userService.isAdmin()");

    }
}
