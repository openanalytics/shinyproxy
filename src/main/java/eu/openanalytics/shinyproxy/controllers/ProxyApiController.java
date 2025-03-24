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

import eu.openanalytics.containerproxy.api.dto.ApiResponse;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.model.store.IProxyStore;
import eu.openanalytics.containerproxy.service.ProxyService;
import eu.openanalytics.containerproxy.service.StructuredLogger;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionContext;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import eu.openanalytics.shinyproxy.controllers.dto.ChangeProxyUserIdDto;
import eu.openanalytics.shinyproxy.controllers.dto.ShinyProxyApiResponse;
import eu.openanalytics.shinyproxy.controllers.dto.SwaggerDto;
import eu.openanalytics.shinyproxy.runtimevalues.AppInstanceKey;
import eu.openanalytics.shinyproxy.runtimevalues.CustomAppDetail;
import eu.openanalytics.shinyproxy.runtimevalues.CustomAppDetails;
import eu.openanalytics.shinyproxy.runtimevalues.CustomAppDetailsKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;


/**
 * Controller with additional API endpoints.
 */
@RestController
public class ProxyApiController extends BaseController {

    @Inject
    private IProxyStore proxyStore;

    @Inject
    private ProxyService proxyService;

    @Inject
    private SpecExpressionResolver specExpressionResolver;

    private final StructuredLogger slogger = StructuredLogger.create(getClass());

    @Operation(summary = "Transfer an app to another user.", tags = "ShinyProxy")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "App transferred.",
            content = {
                @Content(
                    mediaType = "application/json",
                    examples = {
                        @ExampleObject(value = "{\"status\":\"success\", \"data\": null}")
                    }
                )
            }),
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
            responseCode = "403",
            description = "No permission to transfer app.",
            content = {
                @Content(
                    mediaType = "application/json",
                    examples = {@ExampleObject(value = "{\"status\": \"fail\", \"data\": \"forbidden\"}")}
                )
            }),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Cannot transfer app because no userId is provided in the request",
            content = {
                @Content(
                    mediaType = "application/json",
                    examples = {@ExampleObject(value = "{\"status\": \"fail\", \"data\": \"Cannot transfer app because no userId is provided in the request\"}")}
                )
            }),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Cannot transfer app because the proxy is already owned by this user",
            content = {
                @Content(
                    mediaType = "application/json",
                    examples = {@ExampleObject(value = "{\"status\": \"fail\", \"data\": \"Cannot transfer app because the proxy is already owned by this user\"}")}
                )
            }),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Cannot transfer app because it is not in Up status (status is Stopping)",
            content = {
                @Content(
                    mediaType = "application/json",
                    examples = {@ExampleObject(value = "{\"status\": \"fail\", \"data\": \"Cannot transfer app because it is not in Up status (status is Stopping)\"}")}
                )
            }),
    })
    @RequestMapping(value = "/api/proxy/{proxyId}/userId", method = RequestMethod.PUT, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Proxy>> changeProxyUserId(@PathVariable String proxyId, @RequestBody ChangeProxyUserIdDto changeProxyUserIdDto) {
        if (!allowTransferApp) {
            return ApiResponse.failForbidden();
        }

        Proxy proxy = proxyService.getProxy(proxyId);
        if (proxy == null || !userService.isOwner(proxy)) {
            // check ownership before doing validation, to not leak whether the proxy exists
            return ApiResponse.failForbidden();
        }

        if (StringUtils.isBlank(changeProxyUserIdDto.getUserId())) {
            return ApiResponse.fail("Cannot transfer app because no userId is provided in the request");
        }

        if (!proxy.getStatus().equals(ProxyStatus.Up)) {
            return ApiResponse.fail(String.format("Cannot transfer app because it is not in Up status (status is %s)", proxy.getStatus()));
        }

        if (proxy.getUserId().equalsIgnoreCase(changeProxyUserIdDto.getUserId())) {
            return ApiResponse.fail("Cannot transfer app because the proxy is already owned by this user");
        }

        try {
            String instanceName = proxy.getRuntimeValue(AppInstanceKey.inst);
            if (instanceName.equals("_")) {
                instanceName = "Default";
            }
            instanceName = StringUtils.left(proxy.getUserId() + "-" + instanceName, 64);

            proxyStore.removeProxy(proxy); // required to clear all caches
            proxy = proxy.toBuilder()
                .userId(changeProxyUserIdDto.getUserId())
                .addRuntimeValue(new RuntimeValue(AppInstanceKey.inst, instanceName), true)
                .build();
            proxyStore.addProxy(proxy);
        } catch (AccessDeniedException ex) {
            return ApiResponse.failForbidden();
        }
        return ApiResponse.success();
    }

    /**
     * Get the custom app details for an app.
     */
    @Operation(summary = "Get the custom app details for an app.", tags = "ShinyProxy")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Custom app details returned.",
            content = {
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = SwaggerDto.CustomAppDetailsResponse.class),
                    examples = {
                        @ExampleObject(value = "{\"status\": \"success\", \"data\": [{\"name\": \"example_name\", \"description\": \"example_description\", \"value\": \"example_value\"}]}")
                    }
                )
            }),
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
    @RequestMapping(value = "/api/proxy/{proxyId}/details", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<List<CustomAppDetail>>> proxyDetails(@PathVariable String proxyId, Authentication authentication) {
        Proxy proxy = proxyService.getUserProxy(proxyId);

        if (proxy == null || !userService.isOwner(proxy)) {
            return ShinyProxyApiResponse.appStoppedOrNonExistent();
        }

        CustomAppDetails customAppDetails = proxy.getRuntimeObjectOrNull(CustomAppDetailsKey.inst);
        if (customAppDetails == null || customAppDetails.isEmpty()) {
            return ApiResponse.success(List.of());
        }

        ProxySpec spec = proxyService.getUserSpec(proxy.getSpecId());

        SpecExpressionContext context = SpecExpressionContext.create(
            proxy,
            spec,
            authentication,
            authentication.getPrincipal(),
            authentication.getCredentials());

        List<CustomAppDetail> result = new ArrayList<>();

        for (CustomAppDetail customAppDetail : customAppDetails) {
            try {
                result.add(customAppDetail.toBuilder().value(specExpressionResolver.evaluateToString(customAppDetail.getValue(), context)).build());
            } catch (Exception e) {
                slogger.warn(proxy, e, String.format("Error while resolving CustomAppDetail expression '%s'", customAppDetail.getName()));
            }
        }

        return ApiResponse.success(result);
    }

}
