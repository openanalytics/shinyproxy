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

import eu.openanalytics.containerproxy.ContainerProxyException;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.PublicPathKey;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.InvalidParametersException;
import eu.openanalytics.containerproxy.util.ProxyMappingManager;
import eu.openanalytics.shinyproxy.AppRequestInfo;
import eu.openanalytics.shinyproxy.runtimevalues.AppInstanceKey;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Controller
public class AppDirectController extends BaseController {

    @Inject
    private ProxyMappingManager mappingManager;

    @Operation(summary = "Proxy request to app. Starts the app if it does not yet exists. Can be used directly or for embedding.", tags = "ShinyProxy")
    @RequestMapping(value = {"/app_direct_i/**", "/app_direct/**"})
    public void appDirect(HttpServletRequest request, HttpServletResponse response) throws InvalidParametersException, ServletException, IOException {
        // note: app_direct does not support parameters and resume
        AppRequestInfo appRequestInfo = AppRequestInfo.fromRequestOrNull(request);
        if (appRequestInfo == null) {
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.FORBIDDEN.value());
            request.getRequestDispatcher("/error").forward(request, response);
            return;
        }

        if (appRequestInfo.getSubPath() == null) {
            try {
                response.sendRedirect(request.getRequestURI() + "/");
            } catch (Exception e) {
                throw new RuntimeException("Error redirecting proxy request", e);
            }
            return;
        }

        Proxy proxy = getOrStart(appRequestInfo, request, response);
        if (proxy == null) {
            return;
        }

        try {
            mappingManager.dispatchAsync(proxy, appRequestInfo.getSubPath(), request, response);
        } catch (Exception e) {
            throw new RuntimeException("Error routing proxy request", e);
        }
    }

    private Proxy getOrStart(AppRequestInfo appRequestInfo, HttpServletRequest request, HttpServletResponse response) throws InvalidParametersException, ServletException, IOException {
        Proxy proxy = findUserProxy(appRequestInfo);
        if (proxy == null) {
            ProxySpec spec = proxyService.getUserSpec(appRequestInfo.getAppName());

            if (spec == null) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                request.getRequestDispatcher("/error").forward(request, response);
                return null;
            }

            if (!validateMaxInstances(spec)) {
                throw new ContainerProxyException("Cannot start new proxy because the maximum amount of instances of this proxy has been reached");
            }

            List<RuntimeValue> runtimeValues = shinyProxySpecProvider.getRuntimeValues(spec);
            String id = UUID.randomUUID().toString();
            runtimeValues.add(new RuntimeValue(PublicPathKey.inst, getPublicPath(appRequestInfo)));
            runtimeValues.add(new RuntimeValue(AppInstanceKey.inst, appRequestInfo.getAppInstance()));

            try {
                proxyService.startProxy(userService.getCurrentAuth(), spec, runtimeValues, id, null).run();
            } catch (Throwable t) {
                throw new ContainerProxyException("Failed to start app " + appRequestInfo.getAppName(), t);
            }
            proxy = proxyService.getUserProxy(id);
        }
        if (proxy.getStatus() == ProxyStatus.Up) {
            return proxy;
        } else if (proxy.getStatus() == ProxyStatus.New) {
            // maximum wait 10 minutes for the app to startup
            for (int i = 0; i < 600; i++) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    throw new ContainerProxyException("Failed to start app " + appRequestInfo.getAppName());
                }
                Proxy result = proxyService.getProxy(proxy.getId());
                if (result == null) {
                    throw new ContainerProxyException("Failed to start app " + appRequestInfo.getAppName());
                }
                if (result.getStatus().equals(ProxyStatus.Up)) {
                    return result;
                }
                if (!result.getStatus().equals(ProxyStatus.New)) {
                    throw new ContainerProxyException("Failed to start app " + appRequestInfo.getAppName());
                }
            }
        }
        throw new ContainerProxyException("Failed to start app " + appRequestInfo.getAppName());
    }

    private String getPublicPath(AppRequestInfo appRequestInfo) {
        return contextPathHelper.withEndingSlash() + "app_direct_i/" + appRequestInfo.getAppName() + "/" + appRequestInfo.getAppInstance();
    }

}
