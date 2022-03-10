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
package eu.openanalytics.shinyproxy.controllers;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.Principal;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import eu.openanalytics.containerproxy.auth.IAuthenticationBackend;
import eu.openanalytics.containerproxy.service.hearbeat.HeartbeatService;
import eu.openanalytics.shinyproxy.AppRequestInfo;
import eu.openanalytics.shinyproxy.OperatorService;
import eu.openanalytics.shinyproxy.runtimevalues.AppInstanceKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ModelMap;
import org.springframework.util.StreamUtils;

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.ProxyService;
import eu.openanalytics.containerproxy.service.UserService;
import eu.openanalytics.containerproxy.util.SessionHelper;

public abstract class BaseController {

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
	OperatorService operatorService;

	private static final Logger logger = LogManager.getLogger(BaseController.class);
	private static final Map<String, String> imageCache = new HashMap<>();

	protected String getUserName(HttpServletRequest request) {
		Principal principal = request.getUserPrincipal();
		return (principal == null) ? request.getSession().getId() : principal.getName();
	}
	
	protected String getAppTitle(AppRequestInfo appRequestInfo) {
		String appName = appRequestInfo.getAppName();
		ProxySpec spec = proxyService.getProxySpec(appName);
		if (spec == null || spec.getDisplayName() == null || spec.getDisplayName().isEmpty()) return appName;
		else return spec.getDisplayName();
	}
	
	protected String getContextPath() {
		return SessionHelper.getContextPath(environment, true);
	}

	protected long getHeartbeatRate() {
		return heartbeatService.getHeartbeatRate();
	}
	
	protected Proxy findUserProxy(AppRequestInfo appRequestInfo) {
		return proxyService.findProxy(p ->
				p.getSpec().getId().equals(appRequestInfo.getAppName())
				&& p.getRuntimeValue(AppInstanceKey.inst).equals(appRequestInfo.getAppInstance())
				&& userService.isOwner(p),
				false);
	}
	
	protected String getProxyEndpoint(Proxy proxy) {
		if (proxy == null || proxy.getTargets().isEmpty()) return null;
		return proxy.getTargets().keySet().iterator().next();
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
		map.put("jqueryJs", "/webjars/jquery/3.5.1/jquery.min.js");
		map.put("cookieJs", "/webjars/js-cookie/2.2.1/js.cookie.min.js");
		map.put("handlebars", "/webjars/handlebars/4.7.6/handlebars.runtime.min.js");

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		boolean isLoggedIn = authentication != null && !(authentication instanceof AnonymousAuthenticationToken) && authentication.isAuthenticated();
		map.put("isLoggedIn", isLoggedIn);
		map.put("isAdmin", userService.isAdmin(authentication));
		map.put("isSupportEnabled", isLoggedIn && getSupportAddress() != null);
		map.put("logoutUrl", authenticationBackend.getLogoutURL());
		map.put("isAppPage", false); // defaults, used in navbar
		map.put("maxInstances", 0); // defaults, used in navbar
		map.put("contextPath", getContextPath());

		// operator specific
		map.put("operatorEnabled", operatorService.isEnabled());
		map.put("operatorForceTransfer", operatorService.mustForceTransfer());
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

}
