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

import eu.openanalytics.containerproxy.ContainerProxyException;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.runtime.ProxyStatus;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValue;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.InvalidParametersException;
import eu.openanalytics.containerproxy.util.BadRequestException;
import eu.openanalytics.containerproxy.util.ProxyMappingManager;
import eu.openanalytics.shinyproxy.AppRequestInfo;
import eu.openanalytics.shinyproxy.runtimevalues.AppInstanceKey;
import eu.openanalytics.shinyproxy.runtimevalues.PublicPathKey;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.UUID;

@Controller
public class AppDirectController extends BaseController {

    @Inject
    private ProxyMappingManager mappingManager;

    @RequestMapping(value = {"/app_direct_i/**", "/app_direct/**"})
    public void appDirect(HttpServletRequest request, HttpServletResponse response) throws InvalidParametersException {
        // note: app_direct does not support parameters and resume
        AppRequestInfo appRequestInfo = AppRequestInfo.fromRequestOrException(request);

        if (appRequestInfo.getSubPath() == null) {
            try {
                response.sendRedirect(request.getRequestURI() + "/");
            } catch (Exception e) {
                throw new RuntimeException("Error redirecting proxy request", e);
            }
            return;
        }

        Proxy proxy = getOrStart(appRequestInfo);
        String mapping = getProxyEndpoint(proxy);

        try {
            mappingManager.dispatchAsync(mapping + appRequestInfo.getSubPath(), request, response);
        } catch (Exception e) {
            throw new RuntimeException("Error routing proxy request", e);
        }
    }

    private Proxy getOrStart(AppRequestInfo appRequestInfo) throws InvalidParametersException {
        Proxy proxy = findUserProxy(appRequestInfo);
        if (proxy == null) {
            ProxySpec spec = proxyService.getProxySpec(appRequestInfo.getAppName());

            if (spec == null) throw new BadRequestException("Unknown proxy spec: " + appRequestInfo.getAppName());

            if (!validateProxyStart(spec)) {
                throw new BadRequestException("Cannot start new proxy because the maximum amount of instances of this proxy has been reached");
            }

            List<RuntimeValue> runtimeValues = shinyProxySpecProvider.getRuntimeValues(spec);
            String id = UUID.randomUUID().toString();
            runtimeValues.add(new RuntimeValue(PublicPathKey.inst, getPublicPath(appRequestInfo)));
            runtimeValues.add(new RuntimeValue(AppInstanceKey.inst, appRequestInfo.getAppInstance()));

            proxyService.startProxy(userService.getCurrentAuth(), spec, runtimeValues, id, null).run();
            proxy = proxyService.getProxy(id);
        }
        if (proxy.getStatus() == ProxyStatus.Up) {
            return proxy;
        } else if (proxy.getStatus() == ProxyStatus.New) {
            // maximum wait 10 minutes for the app to startup
            for (int i = 0; i < 600; i++ ) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    throw new ContainerProxyException("Proxy failed to start");
                }
                Proxy result = proxyService.getProxy(proxy.getId());
                if (result == null) {
                    throw new ContainerProxyException("Proxy failed to start");
                }
                if (result.getStatus().equals(ProxyStatus.Up)) {
                    return result;
                }
                if (!result.getStatus().equals(ProxyStatus.New)) {
                    throw new ContainerProxyException("Proxy failed to start");
                }
            }
        }
        throw new ContainerProxyException("Proxy failed to start");
    }

    private String getPublicPath(AppRequestInfo appRequestInfo) {
        return getContextPath() + "app_direct_i/" + appRequestInfo.getAppName() + "/" + appRequestInfo.getAppInstance();
    }

}
