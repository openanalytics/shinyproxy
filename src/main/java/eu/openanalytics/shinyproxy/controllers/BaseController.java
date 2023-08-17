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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ModelMap;
import org.springframework.util.StreamUtils;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public abstract class BaseController {

    private static final Logger logger = LogManager.getLogger(BaseController.class);
    private static final Map<String, String> imageCache = new HashMap<>();
    @Inject
    protected ShinyProxySpecProvider shinyProxySpecProvider;
    @Inject
    ProxyService proxyService;
    @Inject
    UserService userService;
    @Inject
    Environment environment;
    @Inject
    IAuthenticationBackend authenticationBackend;
    @Inject
    HeartbeatService heartbeatService;
    @Inject
    IdentifierService identifierService;
    @Inject
    private IContainerBackend backend;
    @Inject
    protected ContextPathHelper contextPathHelper;
    @Inject
    protected UserAndAppNameAndInstanceNameProxyIndex userAndAppNameAndInstanceNameProxyIndex;
    @Inject
    protected UserAndTargetIdProxyIndex userAndTargetIdProxyIndex;

    protected long getHeartbeatRate() {
        return heartbeatService.getHeartbeatRate();
    }

    protected Proxy findUserProxy(AppRequestInfo appRequestInfo) {
        return findUserProxy(appRequestInfo.getAppName(), appRequestInfo.getAppInstance());
    }

    protected Proxy findUserProxy(String appname, String appInstance) {
        return proxyService.findUserProxy(p -> p.getSpecId().equals(appname) && p.getRuntimeValue(AppInstanceKey.inst).equals(appInstance));
    }

    protected void prepareMap(ModelMap map, HttpServletRequest request) {
        map.put("application_name", environment.getProperty("spring.application.name")); // name of ShinyProxy, ContainerProxy etc
        map.put("title", environment.getProperty("proxy.title", "ShinyProxy"));
        map.put("logo", resolveImageURI(environment.getProperty("proxy.logo-url")));

        String hideNavBarParam = request.getParameter("sp_hide_navbar");
        if (Objects.equals(hideNavBarParam, "true")) {
            map.put("showNavbar", false);
        } else {
            map.put("showNavbar", !Boolean.parseBoolean(environment.getProperty("proxy.hide-navbar")));
        }

        map.put("bootstrapCss", "/webjars/bootstrap/3.4.1/css/bootstrap.min.css");
        map.put("bootstrapJs", "/webjars/bootstrap/3.4.1/js/bootstrap.min.js");
        map.put("jqueryJs", "/webjars/jquery/3.6.1/jquery.min.js");
        map.put("handlebars", "/webjars/handlebars/4.7.7/handlebars.runtime.min.js");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isLoggedIn = authentication != null && !(authentication instanceof AnonymousAuthenticationToken) && authentication.isAuthenticated();
        map.put("isLoggedIn", isLoggedIn);
        map.put("isAdmin", userService.isAdmin(authentication));
        map.put("isSupportEnabled", isLoggedIn && getSupportAddress() != null);
        map.put("logoutUrl", authenticationBackend.getLogoutURL());
        map.put("page", ""); // defaults, used in navbar
        map.put("maxInstances", 0); // defaults, used in navbar
        map.put("contextPath", contextPathHelper.withEndingSlash());
        map.put("resourcePrefix", "/" + identifierService.instanceId);
        map.put("appMaxInstances", shinyProxySpecProvider.getMaxInstances());
        map.put("pauseSupported", backend.supportsPause());
        map.put("spInstance", identifierService.instanceId);
    }

    protected String getSupportAddress() {
        return environment.getProperty("proxy.support.mail-to-address");
    }

    protected String resolveImageURI(String resourceURI) {
        if (resourceURI == null || resourceURI.isEmpty()) return resourceURI;
        if (imageCache.containsKey(resourceURI)) return imageCache.get(resourceURI);

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
        imageCache.put(resourceURI, resolvedValue);
        return resolvedValue;
    }

    /**
     * Validates whether a proxy should be allowed to start.
     */
    protected boolean validateProxyStart(ProxySpec spec) {
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

}
