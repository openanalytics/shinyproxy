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
package eu.openanalytics.shinyproxy.controllers;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.openanalytics.containerproxy.ProxyStartValidationException;
import eu.openanalytics.containerproxy.api.dto.ApiResponse;
import eu.openanalytics.containerproxy.api.dto.SwaggerDto;
import eu.openanalytics.containerproxy.auth.impl.OpenIDAuthenticationBackend;
import eu.openanalytics.containerproxy.backend.strategy.impl.DefaultTargetMappingStrategy;
import eu.openanalytics.containerproxy.model.Views;
import eu.openanalytics.containerproxy.model.runtime.AllowedParametersForUser;
import eu.openanalytics.containerproxy.model.runtime.ParameterValues;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.DisplayNameKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.ParameterValuesKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.PortMappingsKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.PublicPathKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.AsyncProxyService;
import eu.openanalytics.containerproxy.service.InvalidParametersException;
import eu.openanalytics.containerproxy.service.ParametersService;
import eu.openanalytics.containerproxy.util.ProxyMappingManager;
import eu.openanalytics.shinyproxy.ShinyProxyIframeScriptInjector;
import eu.openanalytics.shinyproxy.controllers.dto.ShinyProxyApiResponse;
import eu.openanalytics.shinyproxy.external.ExternalAppSpecExtension;
import eu.openanalytics.shinyproxy.runtimevalues.AppInstanceKey;
import eu.openanalytics.shinyproxy.runtimevalues.UserTimeZoneKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.undertow.util.HttpString;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.MultiValueMap;
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
import org.thymeleaf.spring6.dialect.SpringStandardDialect;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@Controller
public class AppController extends BaseController {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpString acceptEncodingHeader = new HttpString("Accept-Encoding");
    @Inject
    private ProxyMappingManager mappingManager;
    @Inject
    private AsyncProxyService asyncProxyService;
    @Inject
    private ParametersService parameterService;

    private int pathPrefixLength = 0;

    public AppController() {
        objectMapper.setConfig(objectMapper.getSerializationConfig().withView(Views.UserApi.class));
    }

    @PostConstruct
    public void init() {
        // +1 to include last slash
        pathPrefixLength = getBasePublicPath().length() + DefaultTargetMappingStrategy.TARGET_ID_LENGTH + 1;
    }

    @RequestMapping(value = "/app/{appName}/{*subPath}", method = GET)
    public ModelAndView app(ModelMap map, HttpServletRequest request, @PathVariable String appName, @PathVariable String subPath) {
        return app(map, request, appName, "_", "/app/" + appName, subPath);
    }

    @RequestMapping(value = "/app_i/{appName}/{appInstance}/{*subPath}", method = GET)
    public ModelAndView app(ModelMap map, HttpServletRequest request, @PathVariable String appName, @PathVariable String appInstance, @PathVariable String subPath) {
        return app(map, request, appName, appInstance, "/app_i/" + appName + "/" + appInstance, subPath);
    }

    private ModelAndView app(ModelMap map, HttpServletRequest request, String appName, String appInstance, String appPath, String subPath) {
        Proxy proxy = findUserProxy(appName, appInstance);

        ProxySpec spec = proxyService.getUserSpec(appName);
        if (proxy == null && spec == null) {
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.FORBIDDEN.value());
            return new ModelAndView("forward:/error");
        }

        Optional<RedirectView> redirect = createRedirectIfRequired(request, subPath, spec, proxy);
        if (redirect.isPresent()) {
            return new ModelAndView(redirect.get());
        }

        // if the proxy exists, the proxy object is non-null and the spec might be null (if the spec no longer exists or the user no longer has access to the spec)
        // if the proxy does not exists, the proxy object is null and the spec is non-null

        prepareMap(map, request);
        map.put("heartbeatRate", heartbeatRate);
        map.put("page", "app");
        map.put("appName", appName);
        map.put("appInstance", appInstance);
        map.put("appInstanceDisplayName", getAppInstanceDisplayName(appInstance));
        map.put("appPath", appPath);
        map.put("containerSubPath", buildContainerSubPath(request, subPath));
        map.put("refreshOpenidEnabled", authenticationBackend.getName().equals(OpenIDAuthenticationBackend.NAME));
        ParameterValues previousParameters = null;
        if (proxy == null || proxy.getRuntimeObjectOrNull(DisplayNameKey.inst) == null) {
            if (spec == null) {
                // this should only happen if the spec is removed while ShinyProxy is starting this spec
                map.put("appTitle", "ShinyProxy");
            } else if (spec.getDisplayName() == null || spec.getDisplayName().isEmpty()) {
                map.put("appTitle", spec.getId());
            } else {
                map.put("appTitle", spec.getDisplayName());
            }
        } else {
            map.put("appTitle", proxy.getRuntimeValue(DisplayNameKey.inst));
            previousParameters = proxy.getRuntimeObjectOrNull(ParameterValuesKey.inst);
        }
        map.put("proxy", secureProxy(proxy));
        if (spec != null && spec.getParameters() != null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            AllowedParametersForUser allowedParametersForUser = parameterService.calculateAllowedParametersForUser(auth, spec, previousParameters);
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
        return new ModelAndView("app", map);
    }

    @Operation(summary = "Start an app.", tags = "ShinyProxy",
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = AppBody.class),
                examples = {
                    @ExampleObject(name = "With parameters", value = "{\"parameters\":{\"resources\":\"2 CPU cores - 8G RAM\",\"other_parameter\":\"example\"}}"),
                    @ExampleObject(name = "With timezone", value = "{\"timezone\":\"Europe/Brussels\"}")
                }
            )
        )
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "The proxy has been created.",
            content = {
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = SwaggerDto.ProxyResponse.class),
                    examples = {
                        @ExampleObject(value = "{\"status\":\"success\",\"data\":{\"id\":\"cdaa8056-4f96-428e-91e8-bc13518d8987\",\"status\":\"New\",\"startupTimestamp\":0,\"createdTimestamp\":1671707875757," +
                            "\"userId\":\"jack\",\"specId\":\"01_hello\",\"displayName\":\"Hello Application\",\"containers\":[],\"runtimeValues\":{\"SHINYPROXY_FORCE_FULL_RELOAD\":false," +
                            "\"SHINYPROXY_WEBSOCKET_RECONNECTION_MODE\":\"None\",\"SHINYPROXY_MAX_INSTANCES\":100,\"SHINYPROXY_PUBLIC_PATH\":\"/app_proxy/cdaa8056-4f96-428e-91e8-bc13518d8987/\"," +
                            "\"SHINYPROXY_APP_INSTANCE\":\"default\"}}}\n")
                    }
                )
            }),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request, app not started.",
            content = {
                @Content(
                    mediaType = "application/json",
                    examples = {
                        @ExampleObject(name = "Max instances reached", value = "{\"status\":\"fail\",\"data\":\"Cannot start new proxy because the maximum amount of instances of this proxy has been reached\"}"),
                        @ExampleObject(name = "Instance already exists", value = "{\"status\":\"fail\",\"data\":\"You already have an instance of this app with the given name\"}"),
                        @ExampleObject(name = "Parameters required", value = "{\"status\":\"fail\",\"data\":\"No parameters provided, but proxy spec expects parameters\"}"),
                        @ExampleObject(name = "Missing parameter", value = "{\"status\":\"fail\",\"data\":\"Missing value for parameter example\"}"),
                        @ExampleObject(name = "Invalid parameter value", value = "{\"status\":\"fail\",\"data\":\"Provided parameter values are not allowed\"}")
                    }
                )
            }),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Proxy spec not found or no permission to use this proxy spec.",
            content = {
                @Content(
                    mediaType = "application/json",
                    examples = {@ExampleObject(value = "{\"status\": \"fail\", \"data\": \"forbidden\"}")}
                )
            }),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Failed to start proxy.",
            content = {
                @Content(
                    mediaType = "application/json",
                    examples = {@ExampleObject(value = "{\"status\": \"fail\", \"data\": \"Failed to start proxy\"}")}
                )
            }),
    })
    @ResponseBody
    @JsonView(Views.UserApi.class)
    @RequestMapping(value = "/app_i/{specId}/{appInstanceName}", method = RequestMethod.POST)
    public ResponseEntity<ApiResponse<Proxy>> startApp(@PathVariable String specId, @PathVariable String appInstanceName, @RequestBody(required = false) AppBody appBody) {
        ProxySpec spec = proxyService.getUserSpec(specId);
        if (!userService.canAccess(spec)) {
            return ApiResponse.failForbidden();
        }
        Proxy proxy = findUserProxy(specId, appInstanceName);
        if (proxy != null) {
            return ApiResponse.fail("You already have an instance of this app with the given name");
        }

        if (!validateMaxInstances(spec)) {
            Integer maxInstances = shinyProxySpecProvider.getMaxInstancesForSpec(spec);
            return ApiResponse.fail(String.format("Cannot start this app because you are using the maximum number of instances (%s) of this app.", maxInstances));
        }

        List<RuntimeValue> runtimeValues = shinyProxySpecProvider.getRuntimeValues(spec);
        String id = UUID.randomUUID().toString();
        runtimeValues.add(new RuntimeValue(PublicPathKey.inst, getPublicPath(id)));
        runtimeValues.add(new RuntimeValue(AppInstanceKey.inst, appInstanceName));
        if (appBody != null && appBody.getTimezone() != null) {
            runtimeValues.add(new RuntimeValue(UserTimeZoneKey.inst, appBody.getTimezone()));
        }

        try {
            return ApiResponse.success(asyncProxyService.startProxy(spec, runtimeValues, id, (appBody != null) ? appBody.getParameters() : null));
        } catch (ProxyStartValidationException | InvalidParametersException ex) {
            return ApiResponse.fail(ex.getMessage());
        } catch (Throwable t) {
            return ApiResponse.error("Failed to start proxy");
        }
    }

    @Operation(summary = "Proxy request to app. This endpoint is used to serve the iframe, hence it makes some assumptions. Do not use it directly or for embedding.", tags = "ShinyProxy")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "User is not authenticated.",
            content = {
                @Content(
                    mediaType = "application/json",
                    examples = {
                        @ExampleObject(value = "{\"message\":\"shinyproxy_authentication_required\",\"status\":\"fail\"}")
                    }
                )
            }),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "410",
            description = "App has been stopped or the app never existed or the user has no access to the app.",
            content = {
                @Content(
                    mediaType = "application/json",
                    examples = {
                        @ExampleObject(value = "{\"message\":\"app_stopped_or_non_existent\",\"status\":\"fail\"}")
                    }
                )
            }),
    })
    @RequestMapping(value = {"/app_proxy/{targetId}/**"})
    public void appProxy(@PathVariable String targetId, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String subPath = extractSubPath(targetId, request);
        if (subPath == null) {
            ShinyProxyApiResponse.appStoppedOrNonExistent(response);
            return;
        }

        Proxy proxy;
        String proxyId = extractQueryParameter(request, "sp_proxy_id");
        if (proxyId != null) {
            proxy = proxyService.getUserProxy(proxyId);
        } else {
            proxy = userAndTargetIdProxyIndex.getProxy(userService.getCurrentUserId(), targetId);
        }
        if (proxy == null || proxy.getStatus().isUnavailable() || !userService.isOwner(proxy)) {
            ShinyProxyApiResponse.appStoppedOrNonExistent(response);
            return;
        }

        try {
            mappingManager.dispatchAsync(proxy, subPath, request, response);
        } catch (Exception e) {
            throw new RuntimeException("Error routing proxy request", e);
        }
    }

    /**
     * Special handler for HTML requests that inject the ShinyProxy iframe javascript.
     */
    @RequestMapping(value = {"/app_proxy/{targetId}/**"}, produces = "text/html", method = GET)
    public void appProxyHtml(@PathVariable String targetId, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String subPath = extractSubPath(targetId, request);
        if (subPath == null) {
            ShinyProxyApiResponse.appStoppedOrNonExistent(response);
            return;
        }

        Proxy proxy = userAndTargetIdProxyIndex.getProxy(userService.getCurrentUserId(), targetId);
        if (proxy == null || proxy.getStatus().isUnavailable() || !userService.isOwner(proxy)) {
            ShinyProxyApiResponse.appStoppedOrNonExistent(response);
            return;
        }

        String secFetchMode = request.getHeader("Sec-Fetch-Mode");
        if (secFetchMode != null && !secFetchMode.equals("navigate")) {
            // do not inject script since this isn't a navigate request (it's e.g. an ajax/fetch request)
            // note: the header is relatively new and therefore the script is injected if the header is not present
            // see: #30809
            try {
                mappingManager.dispatchAsync(proxy, subPath, request, response);
                return;
            } catch (Exception e) {
                throw new RuntimeException("Error routing proxy request", e);
            }
        }

        try {
            String scriptPath = contextPathHelper.withEndingSlash() + identifierService.instanceId + "/js/shiny.iframe.js";
            mappingManager.dispatchAsync(proxy, subPath, request, response, (exchange) -> {
                exchange.getRequestHeaders().put(acceptEncodingHeader, "identity"); // ensure no encoding is used
                exchange.addResponseWrapper((factory, exchange1) -> new ShinyProxyIframeScriptInjector(factory.create(), exchange1, scriptPath));
            });
        } catch (Exception e) {
            throw new RuntimeException("Error routing proxy request", e);
        }
    }

    /**
     * Extract a query parameter without reading the body of the request.
     * This must be used for proxied requests, as otherwise the body cannot be sent.
     * @param request the request
     * @param name the name of the parameter
     * @return the (first) value or null
     */
    private String extractQueryParameter(HttpServletRequest request, String name) {
        MultiValueMap<String, String> params = ServletUriComponentsBuilder.fromRequest(request)
            .build().getQueryParams();
        if (!params.containsKey(name)) {
            return null;
        }
        return params.getFirst(name);
    }

    private String buildContainerSubPath(HttpServletRequest request, String subPath) {
        String queryString = ServletUriComponentsBuilder.fromRequest(request)
            .replaceQueryParam("sp_hide_navbar")
            .replaceQueryParam("sp_automatic_reload")
            .build().getQuery();

        String res = UriComponentsBuilder
            .fromPath(subPath)
            .query(queryString)
            .build(false) // #30932: queryString is not yet encoded
            .toUriString();

        if (res.startsWith("/")) {
            return res.substring(1);
        }
        return res;
    }

    private String extractSubPath(String targetId, HttpServletRequest request) {
        if (targetId.length() != DefaultTargetMappingStrategy.TARGET_ID_LENGTH) {
            return null;
        }

        return request.getRequestURI().substring(pathPrefixLength);
    }

    private String getBasePublicPath() {
        return contextPathHelper.withEndingSlash() + "app_proxy/";
    }

    private String getPublicPath(String targetId) {
        return getBasePublicPath() + targetId + "/";
    }

    /**
     * Checks if a redirect is required before we can handle the request.
     * <p>
     * ShinyProxy supports proxying to multiple targets. When proxying to a target (without a sub-path for that specific target), the URL must end with a slash.
     * However, when the sub-path does not point to a specific target, it's not required that the URL ends with a slash.
     * </p>
     * <p>
     * Assume an app called `myapp` has an additional-port-mapping named `abc`:
     * - /app/myapp -> no redirect required (getPublicPath() always add a slash)
     * - /app/myapp/test123 -> no redirect required
     * - /app/myapp/abc -> redirect to /app/myapp/abc/
     * - /app/myapp/abc/ -> no redirect required
     * - /app/myapp/abc/test -> no redirect required
     * </p>
     *
     * @param request the current request
     * @param spec    the spec of the current app
     * @param proxy
     * @return a RedirectView if a redirect is needed
     */
    private Optional<RedirectView> createRedirectIfRequired(HttpServletRequest request, String subPath, ProxySpec spec, Proxy proxy) {
        // if it's an external app -> redirect
        if (spec != null) {
            String externalUrl = spec.getSpecExtension(ExternalAppSpecExtension.class).getExternalUrl();
            if (externalUrl != null) {
                return Optional.of(new RedirectView(externalUrl));
            }
        }

        // if sub-path is empty or it's a slash -> no redirect required
        if (subPath.isEmpty() || subPath.equals("/")) {
            return Optional.empty();
        }

        // sub-path always starts with a slash -> get part without the slash
        // this contains the mapping and any additional paths
        String trimmedSubPath = subPath.substring(1);

        // if the trimmedSubPath contains a slash -> no redirect required
        // e.g. /app/myapp/mapping/
        // e.g. /app/myapp/mapping/some_path
        //                 ^^^^^^^^^^^^^^^^^^ -> this is the subpath (without initial slash)
        if (trimmedSubPath.contains("/")) {
            return Optional.empty();
        }

        // the provided subpath does not contain a slash (i.e. it's a single "directory" name)
        // -> we have to check whether the provided subpath is a configured mapping (and thus point to a specific port on the app)
        // or whether it's just a subpath
        boolean isMappingWithoutSlash;
        if (proxy != null) {
            isMappingWithoutSlash = proxy.getContainer(0).getRuntimeObject(PortMappingsKey.inst).getPortMappings()
                .stream()
                .anyMatch(it -> it.getName().equals(trimmedSubPath));
        } else if (spec != null) {
            isMappingWithoutSlash = spec.getContainerSpecs().get(0)
                .getPortMapping()
                .stream()
                .anyMatch(it -> it.getName().equals(trimmedSubPath));
        } else {
            isMappingWithoutSlash = false;
        }
        if (isMappingWithoutSlash) {
            // the provided subpath is a configured mapping -> redirect so it ends with a slash
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

    /**
     * Converts a proxy into an Object using {@link Views.UserApi} view, in order to hide security sensitive values.
     *
     * @return the secured proxy
     */
    private Object secureProxy(Proxy proxy) {
        return objectMapper.convertValue(proxy, Object.class);
    }

    private String getAppInstanceDisplayName(String appInstance) {
        if (appInstance.equals("_")) {
            return "Default";
        }
        return appInstance;
    }

    private static class AppBody {
        private Map<String, String> parameters;
        private String timezone;

        @Schema(description = "Map of parameters for the app.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        public Map<String, String> getParameters() {
            return parameters;
        }

        public void setParameters(Map<String, String> parameters) {
            this.parameters = parameters;
        }

        @Schema(description = "The timezone of the user in TZ format.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        public String getTimezone() {
            return timezone;
        }

        public void setTimezone(String timezone) {
            this.timezone = timezone;
        }
    }

}
