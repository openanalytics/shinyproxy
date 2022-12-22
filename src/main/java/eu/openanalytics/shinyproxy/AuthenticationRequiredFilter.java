/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2021 Open Analytics
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

import eu.openanalytics.shinyproxy.controllers.dto.ShinyProxyApiResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.ClientAuthorizationRequiredException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.security.web.util.ThrowableAnalyzer;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.GenericFilterBean;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * A filter that blocks the default {@link AuthenticationEntryPoint} when requests are made to certain endpoints.
 * These endpoints are only accessed from AJAX calls.
 * These endpoints are:
 * - /app_proxy/** (without spaces), i.e. any subpath on the app_direct endpoint (thus not the page that loads the app)
 * - /heartbeat/* , i.e. heartbeat requests
 *
 * When the filter detects that a user is not authenticated when requesting one of these endpoints, it returns the response:
 * {"status":"fail", "data":"shinyproxy_authentication_required"} with status code 401.
 * This response is specific unique enough such that it can be handled by the frontend.
 *
 * Note: this cannot be easily implemented as a {@link AuthenticationEntryPoint} since these entrypoints are sometimes,
 * but not always overridden by the authentication backend.
 * See #26403, #28490
 */
public class AuthenticationRequiredFilter extends GenericFilterBean {

    private final ThrowableAnalyzer throwableAnalyzer = new DefaultThrowableAnalyzer();

    private static final RequestMatcher REQUEST_MATCHER = new OrRequestMatcher(
            new AntPathRequestMatcher("/app_proxy/**"),
            new AntPathRequestMatcher("/heartbeat/*"));

    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        try {
            chain.doFilter(request, response);
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            if (REQUEST_MATCHER.matches(request) && isAuthException(ex)) {
                if (response.isCommitted()) {
                    throw new ServletException("Unable to handle the Spring Security Exception because the response is already committed.", ex);
                }
                SecurityContextHolder.getContext().setAuthentication(null);
                ShinyProxyApiResponse.authenticationRequired(response);
                return;
            }
            throw ex;
        }
    }

    /**
     * @param ex the exception to check
     * @return whether this exception indicates that the user is not authenticated
     */
    private boolean isAuthException(Exception ex) {
        Throwable[] causeChain = throwableAnalyzer.determineCauseChain(ex);
        Throwable type = throwableAnalyzer.getFirstThrowableOfType(AuthenticationException.class, causeChain);
        if (type != null) {
            return true;
        }
        type = throwableAnalyzer.getFirstThrowableOfType(ClientAuthorizationRequiredException.class, causeChain);
        if (type != null) {
            return true;
        }
        type = throwableAnalyzer.getFirstThrowableOfType(AccessDeniedException.class, causeChain);
        return type != null;
    }

    /**
     * Based on {@link ExceptionTranslationFilter.DefaultThrowableAnalyzer}
     */
    private static final class DefaultThrowableAnalyzer extends ThrowableAnalyzer {
        protected void initExtractorMap() {
            super.initExtractorMap();

            registerExtractor(ServletException.class, throwable -> {
                ThrowableAnalyzer.verifyThrowableHierarchy(throwable,
                        ServletException.class);
                return ((ServletException) throwable).getRootCause();
            });
        }
    }

}
