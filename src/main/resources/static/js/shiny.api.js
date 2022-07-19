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
        let resp = await fetch(Shiny.api.buildURL("api/proxy?only_owned_proxies=true", false));
        return await resp.json();
    },
    getAllSpInstances: async function () {
        const resp = await fetch(Shiny.api.buildURL("operator/metadata", false));
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
            requests.push(fetch(Shiny.api.buildURL("api/proxy?only_owned_proxies=true&sp_instance_override=" + instance, false))
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
    getProxyById: async function (proxyId, spInstance) {
        return await fetch(Shiny.api.buildURLForInstance("api/proxy/" + proxyId, spInstance))
            .then(response => {
                if (response.status === 200) {
                    return response;
                }
                return null;
            });
    },
    _groupByApp: function (proxies) {
        let handled = [];
        let result = {};
        for (const proxy of proxies) {
            if (proxy.hasOwnProperty('spec') && proxy.spec.hasOwnProperty('id')) {
                if (!handled.includes(proxy.id)) {
                    handled.push(proxy.id);
                    proxies.push(proxy);
                    if (!result.hasOwnProperty(proxy.spec.id)) {
                        result[proxy.spec.id] = []
                    }
                    result[proxy.spec.id].push(proxy);
                }
            } else {
                console.log("Received invalid proxy object from server.", proxy);
            }
        }
        return result;
    },
    getProxiesAsTemplateData: async function () {
        const proxies = await Shiny.api.getProxiesOnAllSpInstances();
        let templateData = {'apps': {}};

        for (const [appName, instances] of Object.entries(proxies)) {
            let displayName = null;
            let processedInstances = instances.reduce( (res, instance) => {
                if (instance.hasOwnProperty('spec') && instance.hasOwnProperty('id') &&
                    instance.hasOwnProperty('runtimeValues') &&
                    instance.runtimeValues.hasOwnProperty('SHINYPROXY_APP_INSTANCE') &&
                    instance.runtimeValues.hasOwnProperty('SHINYPROXY_INSTANCE')
                ) {

                    let appInstance = instance.runtimeValues.SHINYPROXY_APP_INSTANCE;

                    if (instance.status !== "Up" && instance.status !== "Starting" && instance.status !== "New") {
                        return res;
                    }

                    if (displayName == null) {
                        displayName = instance.spec.id;
                        if (instance.spec.displayName !== null && instance.spec.displayName !== "") {
                            displayName = instance.spec.displayName;
                        }
                    }

                    let instanceName = Shiny.instances._toAppDisplayName(appInstance);

                    let uptime = "N/A";
                    if (instance.hasOwnProperty("startupTimestamp") && instance.startupTimestamp > 0) {
                        const uptimeSec = (Date.now() - instance.startupTimestamp) / 1000;
                        const hours = Math.floor(uptimeSec / 3600);
                        const minutes = Math.floor((uptimeSec % 3600) / 60).toString().padStart(2, '0');
                        const seconds = Math.floor(uptimeSec % 60).toString().padStart(2, '0');
                        uptime = `${hours}:${minutes}:${seconds}`
                    }

                    const url = Shiny.instances._createUrlForProxy(instance);
                    res.push({
                        appName: instance.spec.id,
                        instanceName: instanceName,
                        displayName: displayName,
                        url: url,
                        spInstance: instance.runtimeValues.SHINYPROXY_INSTANCE,
                        proxyId: instance.id,
                        uptime: uptime
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
        const requests = {};
        for (const instance of instances) {
            requests[instance] = fetch(Shiny.api.buildURL("admin/data?sp_instance_override=" + instance, false))
                .then(response => response.json())
                .then(response => response.apps);
        }
        const res = [];
        for (const [instance, request] of Object.entries(requests)) {
            const response = await request;
            if (instance === Shiny.common.staticState.spInstance) {
                res.push({
                    displayName: "This instance",
                    apps: response
                });
            } else {
                res.push({
                    displayName: instance,
                    apps: response
                });
            }
        }
        return {"instances": res};
    },
    buildURL(location, allowSpInstanceOverride = true) {
        const baseURL = new URL(Shiny.common.staticState.contextPath, window.location.origin);
        const url = new URL(location, baseURL);
        if (!allowSpInstanceOverride || Shiny.app.staticState.spInstanceOverride === null) {
            return url;
        }
        url.searchParams.set("sp_instance_override", Shiny.app.staticState.spInstanceOverride);
        return url;
    },
    buildURLForInstance(location, spInstance) {
        const baseURL = new URL(Shiny.common.staticState.contextPath, window.location.origin);
        const url = new URL(location, baseURL);
        if (spInstance === Shiny.common.staticState.spInstance && Shiny.app.staticState.spInstanceOverride === null) {
            // we are targeting the current instance, and we are not using the override system -> no need to include the override in the URL
            return url;
        }
        url.searchParams.set("sp_instance_override", spInstance);
        return url;
    }
};
