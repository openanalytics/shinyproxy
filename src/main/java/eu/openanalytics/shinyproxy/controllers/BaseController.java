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
package eu.openanalytics.shinyproxy.controllers;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.backend.IContainerBackend;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.IdentifierService;
import eu.openanalytics.containerproxy.service.ProxyService;
import eu.openanalytics.containerproxy.service.UserAndTargetIdProxyIndex;
import eu.openanalytics.containerproxy.service.UserService;
import eu.openanalytics.containerproxy.service.hearbeat.HeartbeatService;
import eu.openanalytics.containerproxy.util.ContextPathHelper;
import eu.openanalytics.shinyproxy.AppRequestInfo;
import eu.openanalytics.shinyproxy.ShinyProxySpecProvider;
import eu.openanalytics.shinyproxy.UserAndAppNameAndInstanceNameProxyIndex;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ModelMap;
import org.springframework.util.StreamUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Base64;
import java.util.Objects;

public abstract class BaseController {

    private static final Logger logger = LogManager.getLogger(BaseController.class);
    private static final Cache<String, LogoInfo> logoInfCache = Caffeine.newBuilder().build();
    protected String applicationName;
    protected String title;
    protected String logo;
    protected long heartbeatRate;
    protected boolean defaultShowNavbar;
    protected String supportAddress;
    protected String defaultLogo;
    protected String defaultLogoWidth;
    protected String defaultLogoHeight;
    protected String defaultLogoStyle;
    protected String defaultLogoClasses;
    @Inject
    protected ShinyProxySpecProvider shinyProxySpecProvider;
    @Inject
    protected ProxyService proxyService;
    @Inject
    protected UserService userService;
    @Inject
    protected Environment environment;
    @Inject
    protected IAuthenticationBackend authenticationBackend;
    @Inject
    protected HeartbeatService heartbeatService;
    @Inject
    protected IdentifierService identifierService;
    @Inject
    protected ContextPathHelper contextPathHelper;
    @Inject
    protected UserAndAppNameAndInstanceNameProxyIndex userAndAppNameAndInstanceNameProxyIndex;
    @Inject
    protected UserAndTargetIdProxyIndex userAndTargetIdProxyIndex;
    protected Boolean allowTransferApp;
    @Inject
    private IContainerBackend backend;

    @PostConstruct
    public void baseInit() {
        defaultLogo = resolveImageURI(environment.getProperty("proxy.default-app-logo-url"));
        defaultLogoWidth = environment.getProperty("proxy.default-app-logo-width");
        defaultLogoHeight = environment.getProperty("proxy.default-app-logo-height");
        defaultLogoStyle = environment.getProperty("proxy.default-app-logo-style");
        defaultLogoClasses = environment.getProperty("proxy.default-app-logo-classes");
        logo = resolveImageURI(environment.getProperty("proxy.logo-url"));
        applicationName = environment.getProperty("spring.application.name");
        title = environment.getProperty("proxy.title", "ShinyProxy");
        heartbeatRate = heartbeatService.getHeartbeatRate();
        defaultShowNavbar = !Boolean.parseBoolean(environment.getProperty("proxy.hide-navbar"));
        supportAddress = environment.getProperty("proxy.support.mail-to-address");
        allowTransferApp = environment.getProperty("proxy.allow-transfer-app", Boolean.class, false);
    }

    protected Proxy findUserProxy(AppRequestInfo appRequestInfo) {
        return findUserProxy(appRequestInfo.getAppName(), appRequestInfo.getAppInstance());
    }

    protected Proxy findUserProxy(String appname, String appInstance) {
        return userAndAppNameAndInstanceNameProxyIndex.getProxy(userService.getCurrentUserId(), appname, appInstance);
    }

    protected void prepareMap(ModelMap map, HttpServletRequest request) {
        map.put("application_name", applicationName); // name of ShinyProxy, ContainerProxy etc
        map.put("title", title);
        map.put("logo", logo);

        String hideNavBarParam = request.getParameter("sp_hide_navbar");
        if (Objects.equals(hideNavBarParam, "true")) {
            map.put("showNavbar", false);
        } else {
            map.put("showNavbar", defaultShowNavbar);
        }

        map.put("bootstrapCss", "/webjars/bootstrap/3.4.1/css/bootstrap.min.css");
        map.put("bootstrapJs", "/webjars/bootstrap/3.4.1/js/bootstrap.min.js");
        map.put("jqueryJs", "/webjars/jquery/3.7.1/jquery.min.js");
        map.put("handlebars", "/webjars/handlebars/4.7.7/handlebars.runtime.min.js");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isLoggedIn = authentication != null && !(authentication instanceof AnonymousAuthenticationToken) && authentication.isAuthenticated();
        map.put("isLoggedIn", isLoggedIn);
        map.put("isAdmin", userService.isAdmin(authentication));
        map.put("isSupportEnabled", isLoggedIn && supportAddress != null);
        map.put("logoutUrl", authenticationBackend.getLogoutURL());
        map.put("page", ""); // defaults, used in navbar
        map.put("maxInstances", 0); // defaults, used in navbar
        map.put("contextPath", contextPathHelper.withEndingSlash());
        map.put("resourcePrefix", "/" + identifierService.instanceId);
        map.put("appMaxInstances", shinyProxySpecProvider.getMaxInstances());
        map.put("pauseSupported", backend.supportsPause());
        map.put("spInstance", identifierService.instanceId);
        map.put("allowTransferApp", allowTransferApp);

        ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest httpServletRequest = servletRequestAttributes.getRequest();
        HttpServletResponse httpServletResponse = servletRequestAttributes.getResponse();
        map.put("request", httpServletRequest);
        map.put("response", httpServletResponse);
    }

    protected String getSupportAddress() {
        return environment.getProperty("proxy.support.mail-to-address");
    }

    protected LogoInfo getAppLogoInfo(ProxySpec proxySpec) {
        return logoInfCache.get(proxySpec.getId(), (specId) -> {
            String src = coalesce(resolveImageURI(proxySpec.getLogoURL()), defaultLogo);
            if (src == null) {
                return null;
            }

            return LogoInfo.builder()
                .src(src)
                .width(coalesce(proxySpec.getLogoWidth(), defaultLogoWidth))
                .height(coalesce(proxySpec.getLogoHeight(), defaultLogoHeight))
                .style(coalesce(proxySpec.getLogoStyle(), defaultLogoStyle))
                .classes(coalesce(proxySpec.getLogoClasses(), defaultLogoClasses))
                .build();
        });
    }

    protected String resolveImageURI(String resourceURI) {
        if (resourceURI == null || resourceURI.isEmpty()) {
            return null;
        }

        String resolvedValue = resourceURI;
        if (resourceURI.toLowerCase().startsWith("file://")) {
            String mimetype = URLConnection.guessContentTypeFromName(resourceURI);
            if (mimetype == null) {
                logger.warn("Cannot determine mimetype for resource: " + resourceURI);
            } else {
                try (InputStream input = new URL(resourceURI).openConnection().getInputStream()) {
                    byte[] data = StreamUtils.copyToByteArray(input);
                    String encoded = Base64.getEncoder().encodeToString(data);
                    resolvedValue = String.format("data:%s;base64,%s", mimetype, encoded);
                } catch (IOException e) {
                    logger.warn("Failed to convert file URI to data URI: " + resourceURI, e);
                }
            }
        }
        return resolvedValue;
    }

    /**
     * Checks whether starting a proxy violates the max instances of this spec and user.
     * This corresponds to the `max-instances` property of an app.
     */
    protected boolean validateMaxInstances(ProxySpec spec) {
        Integer maxInstances = shinyProxySpecProvider.getMaxInstancesForSpec(spec);

        if (maxInstances == -1) {
            return true;
        }

        // note: there is a very small change that the user is able to start more instances than allowed, if the user
        // starts many proxies at once. E.g. in the following scenario:
        // - max proxies = 2
        // - user starts a proxy
        // - user sends a start proxy request -> this function is called and returns true
        // - just before this new proxy is added to the list of active proxies, the user sends a new start proxy request
        // - again this new proxy is allowed, because there is still only one proxy in the list of active proxies
        // -> the user has three proxies running.
        // Because of chance that this happens is small and that the consequences are low, we accept this risk.
        long currentAmountOfInstances = proxyService.getUserProxiesBySpecId(spec.getId()).count();

        return currentAmountOfInstances < maxInstances;
    }

    private <T> T coalesce(T first, T second) {
        return first != null ? first : second;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class LogoInfo {

        String src;

        String width;

        String height;

        String style;

        String classes;

    }
}
