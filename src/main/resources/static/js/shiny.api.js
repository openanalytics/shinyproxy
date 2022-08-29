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
    _proxiesCache: null,
    getProxies: async function () {
        let resp = await fetch(Shiny.api.buildURL("api/proxy?only_owned_proxies=true"));
        return await resp.json();
    },
    getAllSpInstances: async function () {
        const baseURL = new URL(Shiny.common.staticState.contextPath, window.location.origin);
        const url = new URL("operator/metadata", baseURL);
        const resp = await fetch(url);
        const json = await resp.json();
        return json.instances.map(i => i.hashOfSpec);
    },
    getProxiesOnAllSpInstances: async function () {
        if (!Shiny.common.staticState.operatorEnabled) {
            return Shiny.api._groupByApp(await Shiny.api.getProxies());
        }
        let instances;
        try {
            instances = await Shiny.api.getAllSpInstances();
        } catch (e) {
            console.log("Failure when getting operator metadata, limiting to current instance");
            instances = [Shiny.common.staticState.spInstance];
        }
        const requests = [];
        for (const instance of instances) {
            requests.push(fetch(Shiny.api.buildURLForInstance("api/proxy?only_owned_proxies=true", instance))
                .then(response => response.json()));
        }
        const responses = await Promise.all(requests);
        return Shiny.api._groupByApp(responses.flat());
    },
    deleteProxyById: async function (proxyId, spInstance) {
        await fetch(Shiny.api.buildURLForInstance("api/proxy/" + proxyId, spInstance), {
            method: 'DELETE',
        });
    },
    async pauseProxyById(proxyId, spInstance) {
        await fetch(Shiny.api.buildURLForInstance("api/proxy/" + proxyId + "/pause", spInstance), {
            method: 'POST',
        });
    },
    getProxyById: async function (proxyId, spInstance) {
        return await fetch(Shiny.api.buildURLForInstance("api/proxy/" + proxyId, spInstance))
            .then(async response => {
                if (response.status === 200) {
                    return await response.json();
                }
                return null;
            });
    },
    getProxyByIdFromCache: async function (proxyId, spInstance) {
        if (Shiny.api._proxiesCache === null) {
            return await Shiny.api.getProxyById(proxyId, spInstance);
        }
        for (const [appName, instances] of Object.entries(Shiny.api._proxiesCache)) {
            for (const instance of instances) {
                if (instance.id === proxyId) {
                    return instance;
                }
            }
        }
        return null;
    },
    _groupByApp: function (proxies) {
        let handled = [];
        let result = {};
        for (const proxy of proxies) {
            if (proxy.hasOwnProperty('specId')) {
                if (!handled.includes(proxy.id)) {
                    handled.push(proxy.id);
                    proxies.push(proxy);
                    if (!result.hasOwnProperty(proxy.specId)) {
                        result[proxy.specId] = []
                    }
                    result[proxy.specId].push(proxy);
                }
            } else {
                console.log("Received invalid proxy object from server.", proxy);
            }
        }
        return result;
    },
    getProxiesAsTemplateData: async function () {
        const proxies = await Shiny.api.getProxiesOnAllSpInstances();
        Shiny.api._proxiesCache = proxies;
        let templateData = {'apps': {}};

        for (const [appName, instances] of Object.entries(proxies)) {
            let displayName = null;
            let processedInstances = instances.reduce( (res, instance) => {
                if (instance.hasOwnProperty('specId') &&
                    instance.hasOwnProperty('runtimeValues') &&
                    instance.runtimeValues.hasOwnProperty('SHINYPROXY_APP_INSTANCE') &&
                    instance.runtimeValues.hasOwnProperty('SHINYPROXY_INSTANCE')
                ) {

                    let appInstance = instance.runtimeValues.SHINYPROXY_APP_INSTANCE;

                    if (instance.status !== "Up" && instance.status !== "Starting" && instance.status !== "New") {
                        return res;
                    }

                    displayName = instance.displayName;
                    let instanceName = Shiny.instances._toAppDisplayName(appInstance);

                    let uptime = "N/A";
                    if (instance.hasOwnProperty("startupTimestamp") && instance.startupTimestamp > 0) {
                        uptime = Shiny.ui.formatSeconds((Date.now() - instance.startupTimestamp) / 1000);
                    }

                    const url = Shiny.api._buildURLForApp(instance);
                    res.push({
                        appName: instance.specId,
                        instanceName: instanceName,
                        displayName: displayName,
                        url: url,
                        spInstance: instance.runtimeValues.SHINYPROXY_INSTANCE,
                        proxyId: instance.id,
                        uptime: uptime,
                    });
                } else {
                    console.log("Received invalid instance object from server.", instance);
                }
                return res;
            }, []);

            if (processedInstances.length > 0) {
                processedInstances.sort(function (a, b) {
                    return a.instanceName.toLowerCase() > b.instanceName.toLowerCase() ? 1 : -1
                });

                templateData.apps[appName] = {'instances': processedInstances, displayName: displayName};
            }
        }

        return templateData;
    },
    async getAdminData() {
        let instances;
        if (!Shiny.common.staticState.operatorEnabled) {
            instances = [Shiny.common.staticState.spInstance];
        } else {
            try {
                instances = await Shiny.api.getAllSpInstances();
            } catch (e) {
                console.log("Failure when getting operator metadata, limiting to current instance");
                instances = [Shiny.common.staticState.spInstance];
            }
        }
        let apps = [];
        let handled = []; // handled apps, required for de-duplication

        await Promise.all(instances.map(instance =>
            fetch(Shiny.api.buildURLForInstance("admin/data", instance))
                .then(response => response.json())
                .then(response => {
                    response.apps.forEach(app => {
                        if (!handled.includes(app.proxyId)) {
                            if (!app.hasOwnProperty("spInstance")) { // TODO can be removed before release
                                app.spInstance = instance;
                            }
                            if (app.spInstance === Shiny.common.staticState.spInstance) {
                                app['server'] = "This server";
                                apps.unshift(app); // ensure "This server" is front of the list
                            } else {
                                app['server'] = instance;
                                apps.push(app);
                            }
                            handled.push(app.proxyId);
                        }
                    });
                })
                .catch(e => console.log("Failed to get admin data for instances: ", instance, e))));

        return apps;
    },
    getHeartBeatInfo: async function (proxyId, spInstance) {
        return await fetch(Shiny.api.buildURLForInstance("heartbeat/" + proxyId, spInstance))
            .then(async response => {
                if (response.status === 200) {
                    return await response.json();
                }
                return null;
            });
    },
    buildURL(location) {
        const baseURL = new URL(Shiny.common.staticState.contextPath, window.location.origin);
        const url = new URL(location, baseURL);

        if (!Shiny.common.staticState.operatorEnabled) {
            return url;
        }

        url.searchParams.set("sp_instance_override", Shiny.common.staticState.spInstance);
        return url;
    },
    buildURLForInstance(location, spInstance) {
        const baseURL = new URL(Shiny.common.staticState.contextPath, window.location.origin);
        const url = new URL(location, baseURL);

        if (!Shiny.common.staticState.operatorEnabled) {
            return url;
        }

        // always include the override instance, even if the page was loaded without override active or if we are targeting the current instance
        // in the meantime an override could have become active
        url.searchParams.set("sp_instance_override", spInstance);
        return url;
    },
    _buildURLForApp: function (app) {
        const appName = app.specId;
        const appInstance = app.runtimeValues.SHINYPROXY_APP_INSTANCE;
        const appSpInstance = app.runtimeValues.SHINYPROXY_INSTANCE;
        if (Shiny.common.staticState.operatorEnabled) {
            return Shiny.common.staticState.contextPath + "app_i/" + appName + "/" + appInstance + "/?sp_instance_override=" + appSpInstance;
        } else {
            return Shiny.common.staticState.contextPath + "app_i/" + appName + "/" + appInstance + "/";
        }
    },
};
