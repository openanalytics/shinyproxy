/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2024 Open Analytics
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
import eu.openanalytics.containerproxy.service.ProxyAccessControlService;
import eu.openanalytics.containerproxy.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.inject.Inject;
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

    @Inject
    private ProxyAccessControlService proxyAccessControlService;

    @Inject
    private HandlerMappingIntrospector handlerMappingIntrospector;

    @Override
    public void apply(HttpSecurity http) throws Exception {
        if (auth.hasAuthorization()) {

            // Limit access to the app pages according to spec permissions
            http.authorizeHttpRequests(authz -> authz
                .requestMatchers(
                    new MvcRequestMatcher(handlerMappingIntrospector, "/app/{specId}/**"),
                    new MvcRequestMatcher(handlerMappingIntrospector, "/app_i/{specId}/**"),
                    new MvcRequestMatcher(handlerMappingIntrospector, "/app_direct/{specId}/**"),
                    new MvcRequestMatcher(handlerMappingIntrospector, "/app_direct_i/{specId}/**"))
                .access((authentication, context) -> new AuthorizationDecision(proxyAccessControlService.canAccessOrHasExistingProxy(authentication.get(), context)))
            );
            http.addFilterAfter(new AuthenticationRequiredFilter(), ExceptionTranslationFilter.class);

            savedRequestAwareAuthenticationSuccessHandler.setRedirectStrategy(new DefaultRedirectStrategy() {
                @Override
                public void sendRedirect(HttpServletRequest request, HttpServletResponse response, String url) throws IOException {
                    String redirectUrl = calculateRedirectUrl(request.getContextPath(), url);
                    AppRequestInfo appRequestInfo = AppRequestInfo.fromURI(redirectUrl);
                    if (appRequestInfo != null) {
                        // before auth, the user tried to open the page of an app, redirect back to that app
                        // (we don't redirect to any other page, see  #30648 and #28624)
                        // remove ?continue from the url (see #31733)
                        String newUrl = ServletUriComponentsBuilder.fromHttpUrl(url).replaceQueryParam("continue").build().toUriString();
                        request.getSession().setAttribute(AUTH_SUCCESS_URL_SESSION_ATTR, newUrl);
                    }
                    response.sendRedirect(ServletUriComponentsBuilder.fromCurrentContextPath().path("/auth-success").build().toUriString());
                }
            });
        }

        // Limit access to the admin pages
        http.authorizeHttpRequests(authz -> authz
            .requestMatchers(
                new MvcRequestMatcher(handlerMappingIntrospector, "/admin"),
                new MvcRequestMatcher(handlerMappingIntrospector, "/admin/data"))
            .access((authentication, context) -> new AuthorizationDecision(userService.isAdmin(authentication.get())))
        );

    }
}
