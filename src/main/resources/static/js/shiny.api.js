/*
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
Shiny = window.Shiny || {};
Shiny.api = {
    getProxies: function (cb) {
        $.get(Shiny.app.staticState.contextPath + "api/proxy", function (proxies) {
            cb(proxies);
        }).fail(function (request) {
            // TODO
        });
    },
    deleteProxyById: function (id, cb) {
        $.ajax({
            url: Shiny.app.staticState.contextPath + "api/proxy/" + id,
            type: 'DELETE',
            success: cb,
            error: function (result) {
                // TODO
            }
        });
    },
    getProxyId: function (appName, instanceName, cb) {
        Shiny.api.getProxies(function (proxies) {
            for (var i = 0; i < proxies.length; i++) {
                // TODO check if properties exists
                if (proxies[i].spec.id === appName && proxies[i].runtimeValues.SHINYPROXY_APP_INSTANCE === instanceName) {
                    cb(proxies[i].id);
                    return;
                }
            }
            cb(null);
        });
    },
    getProxyById: function(proxyId, cb) {
        $.get(Shiny.app.staticState.contextPath + "api/proxy/" + proxyId, function (proxy) {
            cb(true, proxy);
        }).fail(function (request) {
            if (request.status === 404) {
                cb(false, null);
            }
            // TODO
        });
    }
};