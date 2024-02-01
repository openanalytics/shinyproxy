/*
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
Shiny = window.Shiny || {};
Shiny.api = {
    _proxiesCache: null,
    /**
     * @return {Promise.<Array.<Shiny.Proxy>>}
     */
    getProxies: async function () {
        const resp = await fetch(Shiny.api.buildURL("api/proxy"));
        const json = await Shiny.api._getResponseJson(resp);
        if (json === null) {
            return [];
        }
        return json.data;
    },
    async changeProxyStatus(proxyId, status, parameters) {
        if (parameters === null) {
            parameters = {};
        }
        const resp = await fetch(Shiny.api.buildURL("api/proxy/" + proxyId + '/status'), {
            method: 'PUT',
            body: JSON.stringify({"status": status, "parameters": parameters}),
            headers: {
                'Content-Type': 'application/json'
            },
        });
        const json = await Shiny.api._getResponseJson(resp);
        return json !== null;
    },
    async waitForStatusChange(proxyId) {
        let networkErrors = 0;
        while (true) {
            const url = Shiny.api.buildURL('api/proxy/' + proxyId + "/status?watch=true&timeout=10");
            try {
                const resp = await fetch(url);
                const json = await Shiny.api._getResponseJson(resp);
                if (json === null) {
                    return null;
                }
                if (json.data.status === "Up" || json.data.status === "Stopped" || json.data.status === "Paused") {
                    console.log("App status changed, id: " + json.data.id + " status: " + json.data.status);
                    return json.data;
                }
            } catch (e) {
                // retry the status request up to 10 times in case of network issues.
                console.log(e);
                networkErrors++;
                if (networkErrors >= 10) {
                    console.log("Reached more than 10 NetworkErrors, stopping attempt");
                    return null;
                }
            }
        }
    },
    async changeProxyUserid(proxyId, newUserId) {
        const resp = await fetch(Shiny.api.buildURL("api/proxy/" + proxyId + '/userId'), {
            method: 'PUT',
            body: JSON.stringify({"userId": newUserId}),
            headers: {
                'Content-Type': 'application/json'
            },
        });
        const json = await Shiny.api._getResponseJson(resp);
        return json !== null;
    },
    getProxyById: async function (proxyId) {
        const resp = await fetch(Shiny.api.buildURL("api/proxy/" + proxyId));
        const json = await Shiny.api._getResponseJson(resp);
        return json.data;
    },
    getProxyByIdFromCache: async function (proxyId) {
        if (Shiny.api._proxiesCache === null) {
            return await Shiny.api.getProxyById(proxyId);
        }
        for (const instance of Shiny.api._proxiesCache) {
            if (instance.id === proxyId) {
                return instance;
            }
        }
        return null;
    },
    getProxiesAsTemplateData: async function () {
        const proxies = await Shiny.api.getProxies();
        Shiny.api._proxiesCache = proxies;
        let templateData = {'apps': {}};

        for (const instance of proxies) {
            let displayName = null;
            if (instance.hasOwnProperty('specId') &&
                instance.hasOwnProperty('runtimeValues') &&
                instance.runtimeValues.hasOwnProperty('SHINYPROXY_APP_INSTANCE')) {

                let appInstance = instance.runtimeValues.SHINYPROXY_APP_INSTANCE;

                displayName = instance.displayName;
                let instanceName = Shiny.instances._toAppDisplayName(appInstance);

                let uptime = null;
                if (instance.status === "Up" && instance.hasOwnProperty("startupTimestamp") && instance.startupTimestamp > 0) {
                    uptime = Shiny.ui.formatSeconds((Date.now() - instance.startupTimestamp) / 1000);
                }

                let spInstance = null;
                if (instance.runtimeValues.hasOwnProperty('SHINYPROXY_INSTANCE')) {
                    spInstance = instance.runtimeValues.SHINYPROXY_INSTANCE;
                }

                const url = Shiny.api._buildURLForApp(instance);
                if (!templateData.apps.hasOwnProperty(instance.specId)) {
                    templateData.apps[instance.specId] = [];
                }
                templateData.apps[instance.specId].push({
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
        }

        for (const appName of Object.keys(templateData.apps)) {
            templateData.apps[appName] = {
                instances: templateData.apps[appName].sort(function (a, b) {
                    return a.instanceName.toLowerCase() > b.instanceName.toLowerCase() ? 1 : -1
                }),
                displayName: appName
            };
        }

        return templateData;
    },
    async getAdminData() {
        const resp = await fetch(Shiny.api.buildURL("admin/data"))
        const json = await Shiny.api._getResponseJson(resp);
        if (json === null) {
            return;
        }
        const apps = [];
        json.data.forEach(app => {
            if (app.spInstance === Shiny.common.staticState.spInstance) {
                app['server'] = "This server";
                apps.unshift(app); // ensure "This server" is front of the list
            } else {
                app['server'] = app.spInstance;
                apps.push(app);
            }
        });
        return apps;
    },
    getHeartBeatInfo: async function (proxyId) {
        const resp = await fetch(Shiny.api.buildURL("heartbeat/" + proxyId))
        const json = await Shiny.api._getResponseJson(resp);
        if (json === null) {
            return null;
        }
        return json.data;
    },
    reportIssue: async function(message) {
        let proxyId = null;
        if (Shiny.app.runtimeState.proxy && Shiny.app.runtimeState.proxy.status !== "Stopped" && Shiny.app.runtimeState.proxy.status !== "Stopping") {
            proxyId = Shiny.app.runtimeState.proxy.id;
        }

        const body = {
            message: message,
            currentLocation: window.location.href,
            proxyId: proxyId
        }
        let resp = await fetch(Shiny.api.buildURL("issue"), {
            method: 'POST',
            body: JSON.stringify(body),
            headers: {
                'Content-Type': 'application/json'
            },
        });
        const json = await Shiny.api._getResponseJson(resp);
        return json !== null;

    },
    buildURL(location) {
        const baseURL = new URL(Shiny.common.staticState.contextPath, window.location.origin);
        return new URL(location, baseURL);
    },
    _buildURLForApp: function (app) {
        const appName = app.specId;
        const appInstance = app.runtimeValues.SHINYPROXY_APP_INSTANCE;
        return Shiny.common.staticState.contextPath + "app_i/" + appName + "/" + appInstance + "/";
    },
    _getResponseJson: async function (response) {
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
