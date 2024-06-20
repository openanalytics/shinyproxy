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

import eu.openanalytics.containerproxy.api.dto.ApiResponse;
import eu.openanalytics.containerproxy.event.RemoveDelegateProxiesEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.inject.Inject;

@Controller
public class DelegateProxyAdminController extends BaseController {

    @Inject
    private ApplicationEventPublisher applicationEventPublisher;

    @Operation(summary = "Stops DelegateProxies. Can only be used by admins. If no parameters are specified, all DelegateProxies (of all specs) are stopped. " +
        "DelegateProxies that have claimed seats will be stopped as soon as all seats are released. " +
        "New DelegateProxies are automatically created to meet the minimum number of seats.",
        tags = "ShinyProxy"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "The DelegateProxies are being stopped.",
            content = {
                @Content(
                    mediaType = "application/json",
                    examples = {
                        @ExampleObject(value = "{\"status\":\"success\", \"data\": null}")
                    }
                )
            }),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request, no DelegateProxies are being stopped.",
            content = {
                @Content(
                    mediaType = "application/json",
                    examples = {
                        @ExampleObject(name = "Both id and specId are specified, provide only a single parameter.", value = "{\"status\":\"fail\",\"data\":\"Id and specId cannot be specified at the same time\"}"),
                    }
                )
            }),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "403",
            description = "Forbidden, you are not an admin user.",
            content = {
                @Content(
                    mediaType = "application/json",
                    examples = {@ExampleObject(value = "{\"status\": \"fail\", \"data\": \"forbidden\"}")}
                )
            }),
    })

    @RequestMapping(value = "/admin/delegate-proxy", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.DELETE)
    @ResponseBody
    public ResponseEntity<ApiResponse<Object>> stopDelegateProxies(
        @Parameter(description = "If specified stops the DelegateProxy with this id") @RequestParam(required = false) String id,
        @Parameter(description = "If specified stops all DelegateProxies of this specId ") @RequestParam(required = false) String specId
    ) {

        if (id != null && specId != null) {
            return ApiResponse.fail("Id and specId cannot be specified at the same time");
        }

        applicationEventPublisher.publishEvent(new RemoveDelegateProxiesEvent(id, specId));

        return ApiResponse.success();
    }

}
