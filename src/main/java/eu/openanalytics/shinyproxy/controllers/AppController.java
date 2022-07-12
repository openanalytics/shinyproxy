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

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.InvalidParametersException;
import eu.openanalytics.containerproxy.util.BadRequestException;
import eu.openanalytics.containerproxy.util.ProxyMappingManager;
import eu.openanalytics.containerproxy.util.Retrying;
import eu.openanalytics.shinyproxy.AppRequestInfo;
import eu.openanalytics.containerproxy.service.ParametersService;
import eu.openanalytics.containerproxy.model.runtime.ProvidedParameters;
import eu.openanalytics.shinyproxy.ShinyProxySpecProvider;
import eu.openanalytics.containerproxy.model.runtime.AllowedParametersForUser;
import eu.openanalytics.shinyproxy.runtimevalues.AppInstanceKey;
import eu.openanalytics.shinyproxy.runtimevalues.PublicPathKey;
import eu.openanalytics.shinyproxy.runtimevalues.WebSocketReconnectionModeKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.inject.Inject;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class AppController extends BaseController {

	@Inject
	private ProxyMappingManager mappingManager;

	@Inject
	private ShinyProxySpecProvider shinyProxySpecProvider;

    @Inject
    private ParametersService parameterizedAppService;

	private final Logger logger = LogManager.getLogger(getClass());

	@RequestMapping(value={"/app_i/*/*", "/app/*"}, method=RequestMethod.GET)
	public String app(ModelMap map, HttpServletRequest request) {
		AppRequestInfo appRequestInfo = AppRequestInfo.fromRequestOrException(request);

		prepareMap(map, request);

		Proxy proxy = findUserProxy(appRequestInfo);
		awaitReady(proxy);

        ProxySpec spec = proxyService.getProxySpec(appRequestInfo.getAppName());
        // TODO if spec = null;

		map.put("appTitle", getAppTitle(appRequestInfo)); // TODO optimise
		map.put("appName", appRequestInfo.getAppName());
		map.put("appInstance", appRequestInfo.getAppInstance());
		map.put("appInstanceDisplayName", appRequestInfo.getAppInstanceDisplayName());
		map.put("containerPath", (proxy == null) ? "" : buildContainerPath(request, proxy, appRequestInfo));
		map.put("proxyId", (proxy == null) ? "" : proxy.getId());
		map.put("webSocketReconnectionMode", (proxy == null) ? "" : proxy.getRuntimeValue(WebSocketReconnectionModeKey.inst));
		map.put("heartbeatRate", getHeartbeatRate());
		map.put("page", "app");
		map.put("maxInstances", shinyProxySpecProvider.getMaxInstancesForSpec(appRequestInfo.getAppName()));
		map.put("shinyForceFullReload", shinyProxySpecProvider.getShinyForceFullReload(appRequestInfo.getAppName()));
        if (spec.getParameters() != null) {
            AllowedParametersForUser allowedParametersForUser = parameterizedAppService.calculateAllowedParametersForUser(spec);
            map.put("parameterAllowedCombinations", allowedParametersForUser.getAllowedCombinations());
            map.put("parameterValues", allowedParametersForUser.getValues());
            map.put("parameterDefinitions", spec.getParameters().getDefinitions()); // TODO null
            map.put("parameterIds", spec.getParameters().getIds()); // TODO null
        } else  {
            map.put("parameterAllowedCombinations", null);
            map.put("parameterValues", null);
            map.put("parameterDefinitions", null);
            map.put("parameterIds", null);
        }

		// operator specific
		String spInstanceOverride = getSpInstanceOverride(request);
		map.put("spInstanceOverride", spInstanceOverride);
		if (spInstanceOverride != null) {
			map.put("resourceSuffix", "?sp_instance_override=" + spInstanceOverride);
		}

		map.put("operatorShowTransferMessage", operatorService.showTransferMessageOnAppPage());

		return "app";
	}
	
	@RequestMapping(value={"/app_i/*/*", "/app/*"}, method=RequestMethod.POST)
	@ResponseBody
	public Map<String,String> startApp(HttpServletRequest request) throws InvalidParametersException {
        return startApp(request, null);
	}

    @RequestMapping(value={"/app_i/*/*", "/app/*"}, method=RequestMethod.POST, consumes = "application/json")
    @ResponseBody
    public Map<String,String> startAppWithParameters(HttpServletRequest request, @RequestBody AppBody appBody) throws InvalidParametersException {
        return startApp(request, appBody.getParameters());
    }

    private Map<String,String> startApp(HttpServletRequest request, ProvidedParameters parameters) throws InvalidParametersException {
        AppRequestInfo appRequestInfo = AppRequestInfo.fromRequestOrException(request);

        Proxy proxy = getOrStart(appRequestInfo, parameters);
        String containerPath = buildContainerPath(request, proxy, appRequestInfo);

        Map<String,String> response = new HashMap<>();
        response.put("containerPath", containerPath);
        response.put("proxyId", proxy.getId());
        response.put("webSocketReconnectionMode", proxy.getRuntimeValue(WebSocketReconnectionModeKey.inst));
        return response;
    }


    @RequestMapping(value={"/app_direct_i/**", "/app_direct/**"})
	public void appDirect(HttpServletRequest request, HttpServletResponse response) throws IOException, InvalidParametersException {
		AppRequestInfo appRequestInfo = AppRequestInfo.fromRequestOrException(request);

		Proxy proxy = getOrStart(appRequestInfo, null);
		awaitReady(proxy);

		String mapping = getProxyEndpoint(proxy);
		
		if (appRequestInfo.getSubPath() == null) {
			try {
				response.sendRedirect(request.getRequestURI() + "/");
			} catch (Exception e) {
				throw new RuntimeException("Error redirecting proxy request", e);
			}
			return;
		}
		
		try {
			mappingManager.dispatchAsync(mapping + appRequestInfo.getSubPath(), request, response);
		} catch (Exception e) {
			throw new RuntimeException("Error routing proxy request", e);
		}
	}

	@RequestMapping(value="/app_proxy/**")
	public void appProxy(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String requestUrl = request.getRequestURI().substring(getBasePublicPath().length());
		String proxyId = mappingManager.getProxyId(requestUrl);

		Proxy proxy = proxyService.getProxy(proxyId);
		if (proxy == null) {
			response.setStatus(410);
			response.getWriter().write("{\"status\":\"error\", \"message\":\"app_stopped_or_non_existent\"}");
			return;
		}
		if (!userService.isOwner(proxy)) {
			response.setStatus(401);
			response.getWriter().write("{\"status\":\"error\", \"message\":\"shinyproxy_authentication_required\"}");
			return;
		}
		if (requestUrl.equals(proxyId)) {
			// requested an empty path -> redirect to the root path
			// i.e. request /app_proxy/<proxy_id> redirect to /app_proxy/<proxy_id>/
			try {
				response.sendRedirect(request.getRequestURI() + "/");
			} catch (Exception e) {
				throw new RuntimeException("Error redirecting proxy request", e);
			}
			return;
		}
		try {
			mappingManager.dispatchAsync(requestUrl, request, response);
		} catch (Exception e) {
			throw new RuntimeException("Error routing proxy request", e);
		}
	}

	private Proxy getOrStart(AppRequestInfo appRequestInfo, ProvidedParameters parameters) throws InvalidParametersException {
		Proxy proxy = findUserProxy(appRequestInfo);
		if (proxy == null) {
			ProxySpec spec = proxyService.getProxySpec(appRequestInfo.getAppName());

			if (spec == null) throw new BadRequestException("Unknown proxy spec: " + appRequestInfo.getAppName());

			List<RuntimeValue> runtimeValues = shinyProxySpecProvider.getRuntimeValues(spec);
			String id = UUID.randomUUID().toString();
			runtimeValues.add(new RuntimeValue(PublicPathKey.inst, getPublicPath(id)));
			runtimeValues.add(new RuntimeValue(AppInstanceKey.inst, appRequestInfo.getAppInstance()));

			if (!validateProxyStart(spec)) {
				throw new BadRequestException("Cannot start new proxy because the maximum amount of instances of this proxy has been reached");
			}

			proxy = proxyService.startProxy(spec, false, runtimeValues, id, parameters);
		}
		return proxy;
	}


	private boolean awaitReady(Proxy proxy) {
		if (proxy == null) return false;
		if (proxy.getStatus() == ProxyStatus.Up) return true;
		if (proxy.getStatus() == ProxyStatus.Stopping || proxy.getStatus() == ProxyStatus.Stopped) return false;
		
		int totalWaitMs = Integer.parseInt(environment.getProperty("proxy.container-wait-time", "20000"));
		int waitMs = Math.min(500, totalWaitMs);
		int maxTries = totalWaitMs / waitMs;
		Retrying.retry(i -> proxy.getStatus() != ProxyStatus.Starting, maxTries, waitMs);
		
		return (proxy.getStatus() == ProxyStatus.Up);
	}
	
	private String buildContainerPath(HttpServletRequest request, Proxy proxy, AppRequestInfo appRequestInfo) {
		String queryString = ServletUriComponentsBuilder.fromRequest(request).replaceQueryParam("sp_hide_navbar").build().getQuery();

		queryString = (queryString == null) ? "" : "?" + queryString;
		
		return getPublicPath(proxy.getId()) + queryString;
	}

	private String getSpInstanceOverride(HttpServletRequest request) {
		String override = request.getParameter("sp_instance_override");
		if (override == null) {
			for (Cookie cookie : request.getCookies()) {
				if (cookie.getName().equals("sp-instance-override")) {
					override = cookie.getValue();
				}
			}
		}
		if (override == null) {
			return null;
		} else if (override.equals(identifierService.instanceId)) {
			return override;
		} else {
			logger.warn("Received request for instanceId \"{}\" but current id is \"{}\"", override, identifierService.instanceId);
			return null;
		}
	}

	private String getPublicPath(String proxyId) {
		return getBasePublicPath() + proxyId + "/";
	}

	private String getBasePublicPath() {
		return getContextPath() + "app_proxy/";
	}

	/**
	 * Validates whether a proxy should be allowed to start.
	 */
	private boolean validateProxyStart(ProxySpec spec) {
		Integer maxInstances = shinyProxySpecProvider.getMaxInstancesForSpec(spec.getId());

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
		int currentAmountOfInstances = proxyService.getProxies(
				p -> p.getSpec().getId().equals(spec.getId())
						&& userService.isOwner(p),
				false).size();


		return currentAmountOfInstances < maxInstances;
	}

    private static class AppBody {
        private ProvidedParameters parameters;

        public ProvidedParameters getParameters() {
            return parameters;
        }

        public void setParameters(ProvidedParameters parameters) {
            this.parameters = parameters;
        }
    }

}
