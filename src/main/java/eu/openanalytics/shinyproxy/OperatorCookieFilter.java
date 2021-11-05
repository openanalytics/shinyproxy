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


import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.GenericFilterBean;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class OperatorCookieFilter extends GenericFilterBean {

    public static final List<FilterMatcher> REQUEST_MATCHERS = Arrays.asList(
            // all methods (to support the various auth backends)
            new FilterMatcher(new AntPathRequestMatcher("/"), false, ""),
            // all methods (to support the various auth backends)
            new FilterMatcher(new AntPathRequestMatcher("/login"),false, ""),
            new FilterMatcher(new AntPathRequestMatcher("/logout-success", HttpMethod.GET.name()), false, "logout-success"),
            // redirect to main page in order to re-try auth on new server
            new FilterMatcher(new AntPathRequestMatcher("/auth-error", HttpMethod.GET.name()), true, ""),
            // redirect to main page in order to re-try auth on new server
            new FilterMatcher(new AntPathRequestMatcher("/app-access-denied", HttpMethod.GET.name()), true, ""));

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        FilterMatcher match = match(httpRequest);

        if (match == null) {
            chain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // only continue if the FilterMatcher allows authenticated requests OR if the user is NOT logged in
        if (match.alsoIfAuthenticated || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            String currentInstance = httpRequest.getHeader("X-ShinyProxy-Instance");
            String latestInstance = httpRequest.getHeader("X-ShinyProxy-Latest-Instance");

            if (currentInstance != null && latestInstance != null) {
                if (!currentInstance.equals(latestInstance)) {
                    String pattern = match.requestMatcher.getPattern();
                    HttpServletResponse httpResponse = (HttpServletResponse) response;
                    httpResponse.sendRedirect(
                            ServletUriComponentsBuilder
                                    .fromCurrentContextPath()
                                    .path("/server-transfer")
                                    .queryParam("redirectUri", pattern)
                                    .build()
                                    .toUriString()
                    );
                    return;
                }
            }
        }

        chain.doFilter(request, response);
    }

    public static FilterMatcher match(HttpServletRequest request) {
        for (FilterMatcher matcher : REQUEST_MATCHERS) {
            if (matcher.requestMatcher.matches(request)) {
                return matcher;
            }
        }
        return null;
    }

    public static String getRedirectUriByMatch(String redirectUri) {
        if (redirectUri == null) {
            return "";
        }
        for (FilterMatcher matcher : REQUEST_MATCHERS) {
            if (matcher.requestMatcher.getPattern().equals(redirectUri)) {
                return matcher.redirectUri;
            }
        }
        return "";
    }

    private static class FilterMatcher {
        public final AntPathRequestMatcher requestMatcher;
        public final Boolean alsoIfAuthenticated;
        public final String redirectUri;

        public FilterMatcher(AntPathRequestMatcher requestMatcher, Boolean alsoIfAuthenticated, String redirectUri) {
            this.requestMatcher = requestMatcher;
            this.alsoIfAuthenticated = alsoIfAuthenticated;
            this.redirectUri = redirectUri;
        }
    }

}

