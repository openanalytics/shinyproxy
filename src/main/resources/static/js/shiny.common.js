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
Shiny.common = {

    staticState: {
        contextPath: null,
        applicationName: null,
        spInstance: null,
        appMaxInstances: null, // max instances per app
        myAppsMode: null,
        pauseSupported: null,
    },
    runtimeState: {
        switchInstanceApp: null,
    },
    _refreshIntervalId: null,
    _detailsRefreshIntervalId: null,

    init: function (contextPath, applicationName, spInstance, appMaxInstances, myAppsMode, pauseSupported) {
        Shiny.common.staticState.contextPath = contextPath;
        Shiny.common.staticState.applicationName = applicationName;
        Shiny.common.staticState.spInstance = spInstance;
        Shiny.common.staticState.appMaxInstances = appMaxInstances;
        Shiny.common.staticState.myAppsMode = myAppsMode;
        Shiny.common.staticState.pauseSupported = pauseSupported;
    },

    sleep: function (ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    },

    onShowMyApps: function () {
        Shiny.common._refreshModal();
        clearInterval(Shiny.common._refreshIntervalId);
        Shiny.common._refreshIntervalId = setInterval(async function () {
            if (!document.hidden) {
                await Shiny.common._refreshModal();
            }
        }, 2500);
    },

    onCloseMyApps: function () {
        if (Shiny.common.staticState.myAppsMode === 'Modal') {
            clearInterval(Shiny.common._refreshIntervalId);
        }
    },

    showAppDetails: function (event, appName, appInstanceName, proxyId) {
        event.preventDefault();
        if (Shiny.common.staticState.myAppsMode === 'Modal') {
            Shiny.ui.showAppDetailsModal($('#myAppsModal'));
        } else {
            Shiny.ui.showAppDetailsModal();
        }
        Shiny.common.loadAppDetails(appName, appInstanceName, proxyId);
    },

    closeAppDetails: function() {
        clearInterval(Shiny.common._detailsRefreshIntervalId);
        if (Shiny.admin !== undefined) {
            clearInterval(Shiny.admin._detailsRefreshIntervalId);
        }
    },

    loadAppDetails(appName, appInstanceName, proxyId) {
        async function refresh() {
            const proxy = await Shiny.api.getProxyByIdFromCache(proxyId);
            const heartbeatInfo = await Shiny.api.getHeartBeatInfo(proxyId);
            if (proxy === null ||  proxy.status === "Stopped" || proxy.status === "Stopping") {
                const templateData = {
                    appName: appName,
                    proxyId: proxyId,
                    status: "Stopped",
                    instanceName: appInstanceName,
                }
                document.getElementById('appDetails').innerHTML = Handlebars.templates.app_details(templateData);
                Shiny.common.closeAppDetails();
                return;
            }

            let uptime = "N/A";
            let heartbeatTimeout = null;
            let heartbeatTimeoutRemaining = null;
            let isInUse = "N/A";
            let maxLifetime = null;
            let maxLifetimeRemaining = null;

            if (proxy.status === "Up" && proxy.startupTimestamp > 0 && heartbeatInfo !== null) {
                const uptimeSec = (Date.now() - proxy.startupTimestamp) / 1000;
                uptime = Shiny.ui.formatSeconds(uptimeSec);

                const timeoutMs = parseInt(proxy.runtimeValues.SHINYPROXY_HEARTBEAT_TIMEOUT, 10);
                if (timeoutMs !== -1) {
                    heartbeatTimeout = Shiny.ui.formatSeconds(timeoutMs / 1000);
                }

                const timeSinceLastHeartbeat = (Date.now() - heartbeatInfo.lastHeartbeat)
                if (timeSinceLastHeartbeat <= (heartbeatInfo.heartbeatRate * 2)) {
                    isInUse = "Yes";
                } else {
                    isInUse = "No";
                    const remaining = Math.max(0, (timeoutMs - timeSinceLastHeartbeat) / 1000);
                    heartbeatTimeoutRemaining = Shiny.ui.formatSeconds(remaining);
                }

                const maxLifetimeSec = parseInt(proxy.runtimeValues.SHINYPROXY_MAX_LIFETIME, 10) * 60;
                if (maxLifetimeSec > 0) {
                    maxLifetime = Shiny.ui.formatSeconds(maxLifetimeSec);
                    const remaining = Math.max(0, maxLifetimeSec - uptimeSec);
                    maxLifetimeRemaining = Shiny.ui.formatSeconds(remaining);
                }
            }

            let parameters = null;
            if (proxy.runtimeValues.hasOwnProperty("SHINYPROXY_PARAMETER_NAMES")) {
                parameters = proxy.runtimeValues.SHINYPROXY_PARAMETER_NAMES;
            }

            const templateData = {
                appName: proxy.specId,
                proxyId: proxy.id,
                status: proxy.status,
                instanceName: appInstanceName,
                uptime: uptime,
                heartbeatTimeout: heartbeatTimeout,
                maxLifetime: maxLifetime,
                parameters: parameters,
                isInUse: isInUse,
                heartbeatTimeoutRemaining: heartbeatTimeoutRemaining,
                maxLifetimeRemaining: maxLifetimeRemaining
            }
            document.getElementById('appDetails').innerHTML = Handlebars.templates.app_details(templateData);
        }
        refresh();
        Shiny.common._detailsRefreshIntervalId = setInterval(function() {
            if (!document.hidden) {
                refresh();
            }
        }, 2500);
    },

    async onStopAllApps() {
        if (confirm("Are you sure you want to stop all your apps?")) {
            $('#stop-all-apps-btn').hide();
            $('#stopping-all-apps-btn').show();
            const proxies = await Shiny.api.getProxies()
            const proxyIds = [];
            for (const proxy of proxies) {
                Shiny.api.changeProxyStatus(proxy.id, 'Stopping');
                proxyIds.push(proxy.id);
            }
            // wait for all proxies to be stopped
            while (!await Shiny.common._areAllProxiesDeleted(proxyIds)) {
                await Shiny.common.sleep(500);
            }
            await Shiny.common._refreshModal();
            $('#stopping-all-apps-btn').hide();
        }
    },

    async _areAllProxiesDeleted(proxyIds) {
        const proxies = await Shiny.api.getProxies()
        for (const proxy of proxies) {
            if (proxyIds.includes(proxy.id)) {
                return false;
            }
        }
        return true;
    },

    _refreshModal: async function () {
        const templateData = await Shiny.api.getProxiesAsTemplateData();
        templateData.apps = Object.values(templateData.apps);
        templateData.apps.sort(function (a, b) {
            return a.displayName.toLowerCase() > b.displayName.toLowerCase() ? 1 : -1
        });
        templateData['pauseSupported'] = Shiny.common.staticState.pauseSupported;
        document.getElementById('myApps').innerHTML = Handlebars.templates.my_apps(templateData);
        if (templateData.apps.length === 0 ) {
            $('#stop-all-apps-btn').hide();
        } else if ($("#stopping-all-apps-btn").is(":hidden")) {
            // only show it if we are not stopping all apps
            $('#stop-all-apps-btn').show();
        }
    },
    async startIndex() {
        if (Shiny.common.staticState.myAppsMode === 'Inline') {
            Shiny.common.onShowMyApps();
        }
    },
}
