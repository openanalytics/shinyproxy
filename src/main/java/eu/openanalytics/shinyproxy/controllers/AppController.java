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

import com.fasterxml.jackson.annotation.JsonView;
import eu.openanalytics.containerproxy.api.dto.ApiResponse;
import eu.openanalytics.containerproxy.model.Views;
import eu.openanalytics.containerproxy.model.runtime.AllowedParametersForUser;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.DisplayNameKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.AsyncProxyService;
import eu.openanalytics.containerproxy.service.InvalidParametersException;
import eu.openanalytics.containerproxy.service.ParametersService;
import eu.openanalytics.containerproxy.util.BadRequestException;
import eu.openanalytics.containerproxy.util.ProxyMappingManager;
import eu.openanalytics.shinyproxy.AppRequestInfo;
import eu.openanalytics.shinyproxy.runtimevalues.AppInstanceKey;
import eu.openanalytics.shinyproxy.runtimevalues.PublicPathKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.ExpressionContext;
import org.thymeleaf.spring5.dialect.SpringStandardDialect;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import javax.inject.Inject;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Controller
public class AppController extends BaseController {

	private static int PROXY_ID_LENGTH = 36;

	@Inject
	private ProxyMappingManager mappingManager;

	@Inject
	private AsyncProxyService asyncProxyService;

    @Inject
    private ParametersService parameterService;

	private final Logger logger = LogManager.getLogger(getClass());

	@RequestMapping(value={"/app_i/*/**", "/app/**"}, method=RequestMethod.GET)
	public ModelAndView app(ModelMap map, HttpServletRequest request) {
		AppRequestInfo appRequestInfo = AppRequestInfo.fromRequestOrException(request);
		Proxy proxy = findUserProxy(appRequestInfo);

		ProxySpec spec = proxyService.getProxySpec(appRequestInfo.getAppName());
		Optional<RedirectView> redirect = createRedirectIfRequired(request, appRequestInfo, proxy, spec);
		if (redirect.isPresent()) {
			return new ModelAndView(redirect.get());
		}

		prepareMap(map, request);
		map.put("heartbeatRate", getHeartbeatRate());
		map.put("page", "app");
		map.put("appName", appRequestInfo.getAppName());
		map.put("appInstance", appRequestInfo.getAppInstance());
		map.put("appInstanceDisplayName", appRequestInfo.getAppInstanceDisplayName());
		map.put("containerSubPath", buildContainerSubPath(request, appRequestInfo));
		if (proxy == null || proxy.getRuntimeObjectOrNull(DisplayNameKey.inst) == null) {
			if (spec.getDisplayName() == null || spec.getDisplayName().isEmpty()) {
				map.put("appTitle", spec.getId());
			} else {
				map.put("appTitle", spec.getDisplayName());
			}
			map.put("proxy", null);
		} else {
			map.put("appTitle", proxy.getRuntimeValue(DisplayNameKey.inst));
		}
		map.put("proxy", proxy);
		if (spec.getParameters() != null) {
			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			AllowedParametersForUser allowedParametersForUser = parameterService.calculateAllowedParametersForUser(auth, spec);
			map.put("parameterAllowedCombinations", allowedParametersForUser.getAllowedCombinations());
			map.put("parameterValues", allowedParametersForUser.getValues());
			map.put("parameterDefaults", allowedParametersForUser.getDefaultValue());
			map.put("parameterDefinitions", spec.getParameters().getDefinitions());
			map.put("parameterIds", spec.getParameters().getIds());

			if (spec.getParameters().getTemplate() != null) {
				map.put("parameterFragment", renderParameterTemplate(spec.getParameters().getTemplate(), map));
			} else {
				map.put("parameterFragment", null);
			}
		} else {
			map.put("parameterValues", null);
			map.put("parameterDefaults", null);
			map.put("parameterDefinitions", null);
			map.put("parameterIds", null);
			map.put("parameterFragment", null);
		}
		// operator specific
		if (operatorService.isEnabled()) {
			map.put("isSpOverrideActive", getIsSpOverrideActive(request));
			map.put("resourceSuffix", "?sp_instance_override=" + identifierService.instanceId);
			map.put("operatorShowTransferMessage", operatorService.showTransferMessageOnAppPage());
		} else {
			map.put("isSpOverrideActive", false);
			map.put("resourceSuffix", "");
			map.put("operatorShowTransferMessage", false);
		}

		return new ModelAndView("app", map);
	}

	@ResponseBody
	@JsonView(Views.UserApi.class)
	@RequestMapping(value = "/app_i/{specId}/{appInstanceName}", method = RequestMethod.POST)
	public ResponseEntity<ApiResponse<Proxy>> startApp(@PathVariable String specId, @PathVariable String appInstanceName, @RequestBody(required = false) AppBody appBody) {
		ProxySpec spec = proxyService.getProxySpec(specId);
		if (!userService.canAccess(spec)) {
			throw new AccessDeniedException(String.format("Cannot start proxy %s: access denied", spec.getId()));
		}
		Proxy proxy = findUserProxy(specId, appInstanceName);
		if (proxy != null) {
			return ApiResponse.fail("You already have an instance of this app with the given name");
		}

		if (!validateProxyStart(spec)) {
			throw new BadRequestException("Cannot start new proxy because the maximum amount of instances of this proxy has been reached");
		}

		List<RuntimeValue> runtimeValues = shinyProxySpecProvider.getRuntimeValues(spec);
		String id = UUID.randomUUID().toString();
		runtimeValues.add(new RuntimeValue(PublicPathKey.inst, getPublicPath(id)));
		runtimeValues.add(new RuntimeValue(AppInstanceKey.inst, appInstanceName));

		try {
			return ApiResponse.success(asyncProxyService.startProxy(spec, runtimeValues, id, (appBody != null) ? appBody.getParameters() : null));
		} catch (InvalidParametersException ex) {
			return ApiResponse.fail(ex.getMessage());
		}
	}


	@RequestMapping(value="/app_proxy/**")
	public void appProxy(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String requestUrl = request.getRequestURI().substring(getBasePublicPath().length()); // TODO cache
		// in ShinyProxy, proxy ids are used in the urls and these have a fixed length
		// therefore we can simply extract it from the URL
		String proxyId = requestUrl.substring(0, PROXY_ID_LENGTH);

		if (proxyId.length() != 36) {
			response.setStatus(400);
			response.getWriter().write("{\"status\":\"error\", \"message\":\"invalid_request\"}");
			return;
		}

		Proxy proxy = proxyService.getProxy(proxyId);
		if (proxy == null || proxy.getStatus().isUnavailable()) {
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

	private String buildContainerSubPath(HttpServletRequest request, AppRequestInfo appRequestInfo) {
		String queryString = ServletUriComponentsBuilder.fromRequest(request)
				.replaceQueryParam("sp_hide_navbar")
				.replaceQueryParam("sp_instance_override")
				.build().getQuery();

		String res = UriComponentsBuilder
				.fromPath(appRequestInfo.getSubPath())
				.query(queryString)
				.toUriString();

		if (res.startsWith("/")) {
			return res.substring(1);
		}
		return res;
	}

	private boolean getIsSpOverrideActive(HttpServletRequest request) {
		String override = request.getParameter("sp_instance_override");
		if (override != null) {
			return true;
		}
		for (Cookie cookie : request.getCookies()) {
			if (cookie.getName().equals("sp-instance-override")) {
				return true;
			}
		}
		return false;
	}

	private String getPublicPath(String proxyId) {
		return getBasePublicPath() + proxyId + "/";
	}

	private String getBasePublicPath() {
		return getContextPath() + "app_proxy/";
	}


	private Optional<RedirectView> createRedirectIfRequired(HttpServletRequest request, AppRequestInfo appRequestInfo, Proxy proxy, ProxySpec spec) {
		// if sub-path is empty -> no ending slash -> no ending slash and redirect required
		if (appRequestInfo.getSubPath() == null || appRequestInfo.getSubPath().equals("/")) {
			return Optional.empty();
		}

		if (proxy == null) {
			// sub-path is non-empty, but proxy does not yet exist -> redirect to root path
			String uri = ServletUriComponentsBuilder.fromRequest(request)
					.replacePath(appRequestInfo.getAppPath())
					.query(null)
					.build()
					.toUriString();
			return Optional.of(new RedirectView(uri));
		}

		// if sub-path is just the mapping -> no ending slash and redirect required
		String mapping = appRequestInfo.getSubPath().substring(1);
		if (mapping.contains("/")) {
			return Optional.empty();
		}

		boolean mappingWithoutSlash = spec.getContainerSpecs().get(0)
				.getPortMapping()
				.stream()
				.anyMatch(it -> it.getName().equals(mapping));
		if (mappingWithoutSlash) {
			String uri = ServletUriComponentsBuilder.fromRequest(request)
					.path("/")
					.build()
					.toUriString();
			return Optional.of(new RedirectView(uri));
		}

		return Optional.empty();
	}

	private String renderParameterTemplate(String template, ModelMap map) {
		TemplateEngine templateEngine = new TemplateEngine();
		StringTemplateResolver stringTemplateResolver = new StringTemplateResolver();
		stringTemplateResolver.setTemplateMode(TemplateMode.HTML);
		stringTemplateResolver.setCacheable(false);

		templateEngine.setTemplateResolver(stringTemplateResolver);
		templateEngine.setDialect(new SpringStandardDialect());

		ExpressionContext context = new ExpressionContext(templateEngine.getConfiguration(), null, map);
		return templateEngine.process(template, context);
	}

    private static class AppBody {
        private Map<String, String> parameters;

        public Map<String, String> getParameters() {
            return parameters;
        }

        public void setParameters(Map<String, String> parameters) {
            this.parameters = parameters;
        }
    }

}
