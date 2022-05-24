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
    getProxies: async function () {
        let resp  = await fetch(Shiny.api.buildURL("api/proxy?only_owned_proxies=true", false));
        return await resp.json();
    },
    getAllSpInstances: async function () {
        const resp = await fetch(Shiny.api.buildURL("operator/metadata", false));
        const json = await resp.json();
        return json.instances.map(i => i.hashOfSpec);
    },
    getProxiesOnAllSpInstances:  async function () {
        if (!Shiny.app.staticState.operatorEnabled) {
            return await Shiny.api.getProxies();
        }
        const instances = await Shiny.api.getAllSpInstances();
        const requests = [];
        for (const instance of instances) {
            requests.push(fetch(Shiny.api.buildURL("api/proxy?only_owned_proxies=true&sp_instance_override=" + instance, false)));
        }
        const responses = await Promise.all(requests);
        let proxies = [];
        let handled = [];
        for (const response of responses) {
            try {
                const json = await response.json();
                for (const proxy of json) {
                    if (!handled.includes(proxy.id)) {
                        handled.push(proxy.id);
                        proxies.push(proxy);
                    }
                }
            } catch (e) {
                console.log(e);
            }
        }
        return proxies;
    },
    deleteProxyById: function (id, cb, cb_fail) {
        $.ajax({
            url: Shiny.api.buildURL("api/proxy/" + id),
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
        $.get(Shiny.api.buildURL("api/proxy/" + id), function (proxy) {
            cb(true, proxy);
        }).fail(function (response) {
            if (response.status === 404) {
                cb(false, null);
                return;
            }
            cb_fail(response);
        });
    },
    buildURL(location, allowSpInstanceOverride= true) {
        var baseURL = new URL(Shiny.common.staticState.contextPath, window.location.origin);
        var url = new URL(location, baseURL);
        if (!allowSpInstanceOverride || Shiny.app.staticState.spInstanceOverride === null) {
            return url;
        }
        url.searchParams.set("sp_instance_override", Shiny.app.staticState.spInstanceOverride);
        return url;
    }
};