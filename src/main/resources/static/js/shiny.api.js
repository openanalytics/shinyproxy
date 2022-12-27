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
        const resp = await fetch(Shiny.api.buildURL("api/proxy"));
        const json = await Shiny.api._getResponseJson(resp);
        if (json === null) {
            return [];
        }
        return json.data;
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
            requests.push(fetch(Shiny.api.buildURLForInstance("api/proxy", instance))
                .then(resp => Shiny.api._getResponseJson(resp))
                .then(json =>  {
                    if (json === null) {
                        return [];
                    }
                    return json.data;
                }));
        }
        const responses = await Promise.all(requests);
        return Shiny.api._groupByApp(responses.flat());
    },
    async changeProxyStatus(proxyId, spInstance, desiredState, parameters) {
        if (parameters === null) {
            parameters = {};
        }
        const resp = await fetch(Shiny.api.buildURLForInstance("api/" + proxyId + '/status', spInstance), {
            method: 'PUT',
            body:  JSON.stringify({"desiredState": desiredState, "parameters": parameters}),
            headers: {
                'Content-Type': 'application/json'
            },
        });
        const json = await Shiny.api._getResponseJson(resp);
        return json !== null;
    },
    async waitForStatusChange(proxyId, spInstance) {
        while (true) {
            const url = Shiny.api.buildURLForInstance('api/' + proxyId + "/status?watch=true&timeout=10", spInstance);
            try {
                const resp = await fetch(url);
                const json = await Shiny.api._getResponseJson(resp);
                if (json === null) {
                    return null;
                }
                if (json.data.status === "Up" || json.data.status === "Stopped" || json.data.status === "Paused" ) {
                    return json.data;
                }
            } catch (e) {
                console.log(e);
                return null; // error -> return
            }
        }
    },
    getProxyById: async function (proxyId, spInstance) {
        const resp = await fetch(Shiny.api.buildURLForInstance("api/proxy/" + proxyId, spInstance));
        const json = await Shiny.api._getResponseJson(resp);
        return json.data;
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
                    instance.runtimeValues.hasOwnProperty('SHINYPROXY_APP_INSTANCE')) {

                    let appInstance = instance.runtimeValues.SHINYPROXY_APP_INSTANCE;

                    displayName = instance.displayName;
                    let instanceName = Shiny.instances._toAppDisplayName(appInstance);

                    let uptime = "N/A";
                    if (instance.hasOwnProperty("startupTimestamp") && instance.startupTimestamp > 0) {
                        uptime = Shiny.ui.formatSeconds((Date.now() - instance.startupTimestamp) / 1000);
                    }

                    let spInstance = null;
                    if (instance.runtimeValues.hasOwnProperty('SHINYPROXY_INSTANCE')) {
                        spInstance = instance.runtimeValues.SHINYPROXY_INSTANCE;
                    }

                    const url = Shiny.api._buildURLForApp(instance);
                    res.push({
                        appName: instance.specId,
                        instanceName: instanceName,
                        displayName: displayName,
                        url: url,
                        spInstance: spInstance,
                        proxyId: instance.id,
                        uptime: uptime,
                        status: instance.status
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
                .then(resp => Shiny.api._getResponseJson(resp))
                .then(json =>  {
                    if (json === null) {
                        return;
                    }
                    json.data.forEach(app => {
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
        const resp = await fetch(Shiny.api.buildURLForInstance("heartbeat/" + proxyId, spInstance))
        const json = await Shiny.api._getResponseJson(resp);
        if (json === null) {
            return null;
        }
        return json.data;
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
        return Shiny.common.staticState.contextPath + "app_i/" + appName + "/" + appInstance + "/";
    },
    _getResponseJson: async function(response) {
        if (response.status !== 200) {
            console.log("Received invalid response (not 200 OK) ", response);
            return null;
        }
        let json = await response.json();
        if (!json.hasOwnProperty("status")) {
            console.log("Received invalid response (missing status) ", json);
            return null;
        }
        if (json.status !== "success") {
            console.log("Received invalid response (status is not success) ", json);
            return null;
        }
        if (!json.hasOwnProperty("data")) {
            console.log("Received invalid response (missing data) ", json);
            return null;
        }
        return json;
    }
};
