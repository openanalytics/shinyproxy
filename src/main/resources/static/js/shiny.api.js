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
    getProxies: function (cb, cb_fail) {
        $.get(Shiny.common.staticState.contextPath + "api/proxy?only_owned_proxies=true", function (proxies) {
            cb(proxies);
        }).fail(function (response) {
            cb_fail(response);
        });
    },
    deleteProxyById: function (id, cb, cb_fail) {
        $.ajax({
            url: Shiny.common.staticState.contextPath + "api/proxy/" + id,
            type: 'DELETE',
            success: cb,
            error: function (result) {
                cb_fail(result);
            }
        });
    },
    getProxyId: function (appName, instanceName, cb, cb_fail) {
        Shiny.api.getProxies(function (proxies) {
            for (var i = 0; i < proxies.length; i++) {
                var proxy = proxies[i];
                if (proxy.hasOwnProperty('spec') && proxy.spec.hasOwnProperty('id') &&
                    proxy.hasOwnProperty('runtimeValues') && proxy.runtimeValues.hasOwnProperty('SHINYPROXY_APP_INSTANCE')
                    && proxy.spec.id === appName && proxy.runtimeValues.SHINYPROXY_APP_INSTANCE === instanceName) {
                    cb(proxies[i].id);
                    return;
                }
            }
            cb(null);
        }, cb_fail);
    },
    getProxyById: function(proxyId, cb, cb_fail) {
        $.get(Shiny.common.staticState.contextPath + "api/proxy/" + proxyId, function (proxy) {
            cb(true, proxy);
        }).fail(function (response) {
            if (response.status === 404) {
                cb(false, null);
                return;
            }
            cb_fail(response);
        });
    }
};