/*
 * ShinyProxy
 *
 * Copyright (C) 2016-2025 Open Analytics
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
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionContext;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import eu.openanalytics.containerproxy.util.ContextPathHelper;
import eu.openanalytics.containerproxy.util.EnvironmentUtils;
import eu.openanalytics.shinyproxy.AppRequestInfo;
import eu.openanalytics.shinyproxy.ShinyProxySpecProvider;
import eu.openanalytics.shinyproxy.Thymeleaf;
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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public abstract class BaseController {

    private static final Logger logger = LogManager.getLogger(BaseController.class);
    private static final Cache<String, Optional<LogoInfo>> logoInfoCache = Caffeine.newBuilder().build();
    protected String applicationName;
    protected String title;
    private Boolean titleContainsExpression;
    private Boolean logoContainsExpression;
    protected String logo;
    private final Cache<String, Optional<String>> logoCache = Caffeine.newBuilder().build();
    protected long heartbeatRate;
    protected boolean defaultShowNavbar;
    protected String defaultSupportAddress;
    protected String defaultLogo;
    protected String defaultLogoWidth;
    protected String defaultLogoHeight;
    protected String defaultLogoStyle;
    protected String defaultLogoClasses;
    protected String bodyClasses;
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
    @Inject
    private Thymeleaf thymeleaf;
    @Inject
    protected SpecExpressionResolver expressionResolver;

    @PostConstruct
    public void baseInit() {
        defaultLogo = resolveImageURI(environment.getProperty("proxy.default-app-logo-url")).orElse(null);
        defaultLogoWidth = environment.getProperty("proxy.default-app-logo-width");
        defaultLogoHeight = environment.getProperty("proxy.default-app-logo-height");
        defaultLogoStyle = environment.getProperty("proxy.default-app-logo-style");
        defaultLogoClasses = environment.getProperty("proxy.default-app-logo-classes");
        logo = environment.getProperty("proxy.logo-url", "");
        logoContainsExpression = logo.contains("#{");
        applicationName = environment.getProperty("spring.application.name");
        title = environment.getProperty("proxy.title", "ShinyProxy");
        titleContainsExpression = title.contains("#{");
        heartbeatRate = heartbeatService.getHeartbeatRate();
        defaultShowNavbar = !Boolean.parseBoolean(environment.getProperty("proxy.hide-navbar"));
        defaultSupportAddress = environment.getProperty("proxy.support.mail-to-address");
        allowTransferApp = environment.getProperty("proxy.allow-transfer-app", Boolean.class, false);
        List<String> bodyClassesList = EnvironmentUtils.readList(environment, "proxy.body-classes");
        if (bodyClassesList != null && !bodyClassesList.isEmpty()) {
            bodyClasses = String.join(" ", bodyClassesList);
        } else {
            bodyClasses = "";
        }
    }

    protected Proxy findUserProxy(AppRequestInfo appRequestInfo) {
        return findUserProxy(appRequestInfo.getAppName(), appRequestInfo.getAppInstance());
    }

    protected Proxy findUserProxy(String appname, String appInstance) {
        return userAndAppNameAndInstanceNameProxyIndex.getProxy(userService.getCurrentUserId(), appname, appInstance);
    }

    protected void prepareMap(ModelMap map, HttpServletRequest request) {
        prepareMap(map, request, null, null);
    }

    protected void prepareMap(ModelMap map, HttpServletRequest request, ProxySpec proxySpec, Proxy proxy) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String serverName = request.getServerName();
        addTitleAndLogo(authentication, proxySpec, proxy, serverName, map);
        map.put("application_name", applicationName); // name of ShinyProxy, ContainerProxy etc

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

        boolean isLoggedIn = authentication != null && !(authentication instanceof AnonymousAuthenticationToken) && authentication.isAuthenticated();
        map.put("isLoggedIn", isLoggedIn);
        map.put("isAdmin", userService.isAdmin(authentication));
        map.put("isSupportEnabled", isLoggedIn && defaultSupportAddress != null);
        map.put("logoutUrl", authenticationBackend.getLogoutURL());
        map.put("page", ""); // defaults, used in navbar
        map.put("maxInstances", 0); // defaults, used in navbar
        map.put("contextPath", contextPathHelper.withEndingSlash());
        map.put("resourcePrefix", "/" + identifierService.instanceId);
        map.put("appMaxInstances", shinyProxySpecProvider.getMaxInstances());
        map.put("pauseSupported", backend.supportsPause());
        map.put("spInstance", identifierService.instanceId);
        map.put("allowTransferApp", allowTransferApp);
        map.put("notificationMessage", environment.getProperty("proxy.notification-message"));
        map.put("bodyClasses", bodyClasses);

        List<ProxySpec> apps = proxyService.getUserSpecs();
        Thymeleaf.GroupedProxySpecs groupedApps = thymeleaf.groupApps(apps);
        map.put("apps", apps);
        map.put("appIds", groupedApps.getIds());
        map.put("templateGroups", groupedApps.getTemplateGroups());
        map.put("groupedApps", groupedApps.getGroupedApps());
        map.put("ungroupedApps", groupedApps.getUngroupedApps());

        // app logos
        Map<ProxySpec, LogoInfo> appLogos = new HashMap<>();
        for (ProxySpec app : shinyProxySpecProvider.getSpecs()) {
            appLogos.put(app, getAppLogoInfo(app));
        }
        map.put("appLogos", appLogos);

        ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest httpServletRequest = servletRequestAttributes.getRequest();
        HttpServletResponse httpServletResponse = servletRequestAttributes.getResponse();
        map.put("request", httpServletRequest);
        map.put("response", httpServletResponse);
    }

    protected String getDefaultSupportAddress() {
        return environment.getProperty("proxy.support.mail-to-address");
    }

    protected LogoInfo getAppLogoInfo(ProxySpec proxySpec) {
        return logoInfoCache.get(proxySpec.getId(), (specId) -> {
            String src = resolveImageURI(proxySpec.getLogoURL()).orElse(defaultLogo);
            if (src == null) {
                return Optional.empty();
            }

            return Optional.of(LogoInfo.builder()
                .src(src)
                .width(coalesce(proxySpec.getLogoWidth(), defaultLogoWidth))
                .height(coalesce(proxySpec.getLogoHeight(), defaultLogoHeight))
                .style(coalesce(proxySpec.getLogoStyle(), defaultLogoStyle))
                .classes(coalesce(proxySpec.getLogoClasses(), defaultLogoClasses))
                .build());
        }).orElse(null);
    }

    protected Optional<String> resolveImageURI(String resourceURI) {
        if (resourceURI == null || resourceURI.isBlank()) {
            return Optional.empty();
        }

        if (resourceURI.toLowerCase().startsWith("file://")) {
            String mimetype = URLConnection.guessContentTypeFromName(resourceURI);
            if (mimetype == null) {
                logger.warn("Cannot determine mimetype for resource: {}", resourceURI);
                return Optional.empty();
            } else {
                try (InputStream input = new URI(resourceURI).toURL().openConnection().getInputStream()) {
                    byte[] data = StreamUtils.copyToByteArray(input);
                    String encoded = Base64.getEncoder().encodeToString(data);
                    return Optional.of(String.format("data:%s;base64,%s", mimetype, encoded));
                } catch (IOException | URISyntaxException e) {
                    logger.warn("Failed to convert file URI to data URI: " + resourceURI, e);
                    return Optional.empty();
                }
            }
        }
        return Optional.of(resourceURI);
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

    private void addTitleAndLogo(Authentication user, ProxySpec proxySpec, Proxy proxy, String serverName, ModelMap map) {
        if (!titleContainsExpression && !logoContainsExpression) {
            map.put("title", title);
            map.put("logo", getLogo(logo));
            return;
        }
        SpecExpressionContext context = SpecExpressionContext.create(
                proxy, proxySpec, user, user.getPrincipal(), user.getCredentials()
            )
            .serverName(serverName)
            .build();
        if (titleContainsExpression) {
            map.put("title", expressionResolver.evaluateToString(title, context));
        } else {
            map.put("title", title);
        }
        if (logoContainsExpression) {
            map.put("logo", getLogo(expressionResolver.evaluateToString(logo, context)));
        } else {
            map.put("logo", getLogo(logo));
        }
    }

    private String getLogo(String logo) {
        return logoCache.get(logo, this::resolveImageURI).orElse(null);
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
