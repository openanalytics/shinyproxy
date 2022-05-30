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
    },
    _refreshIntervalId: null,

    init: function (contextPath, applicationName, spInstance) {
        Shiny.common.staticState.contextPath = contextPath;
        Shiny.common.staticState.applicationName = applicationName;
        Shiny.common.staticState.spInstance = spInstance;
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

    _refreshModal: async function () {
        const proxies = await Shiny.api.getProxiesOnAllSpInstances();
        let templateData = {'apps': {}};

        for (const proxy of proxies) {
            if (proxy.hasOwnProperty('spec') && proxy.spec.hasOwnProperty('id') &&
                proxy.hasOwnProperty('runtimeValues') &&
                proxy.runtimeValues.hasOwnProperty('SHINYPROXY_APP_INSTANCE') &&
                proxy.runtimeValues.hasOwnProperty('SHINYPROXY_INSTANCE')
            ) {

                let appInstance = proxy.runtimeValues.SHINYPROXY_APP_INSTANCE;

                if (proxy.status !== "Up" && proxy.status !== "Starting" && proxy.status !== "New") {
                    continue;
                }

                let appName = proxy.spec.id;
                if (proxy.spec.displayName !== "") {
                    appName = proxy.spec.displayName;
                }

                let instanceName = "";
                if (appInstance === "_") {
                    instanceName = "Default";
                } else {
                    instanceName = appInstance;
                }

                let uptime = "N/A";
                if (proxy.hasOwnProperty("startupTimestamp") && proxy.startupTimestamp > 0) {
                    const uptimeSec = (Date.now() - proxy.startupTimestamp) / 1000;
                    const hours = Math.floor(uptimeSec / 3600);
                    const minutes = Math.floor((uptimeSec % 3600) / 60).toString().padStart(2, '0');
                    const seconds = Math.floor(uptimeSec % 60).toString().padStart(2, '0');
                    uptime = `${hours}:${minutes}:${seconds}`
                }

                const url = Shiny.instances._createUrlForProxy(proxy);

                if (!templateData['apps'].hasOwnProperty(appName)) {
                    templateData['apps'][appName] = [];
                }

                templateData['apps'][appName].push({
                    instanceName: instanceName,
                    appName: appName,
                    url: url,
                    spInstance: proxy.runtimeValues.SHINYPROXY_INSTANCE,
                    proxyId: proxy.id,
                    uptime: uptime
                });
            } else {
                console.log("Received invalid proxy object from server.");
            }
        }

        document.getElementById('myApps').innerHTML = Handlebars.templates.my_apps(templateData);
    },

}

$(window).on('load', function () {
    $('#myAppsModal-btn').click(function () {
        Shiny.common.onShowMyApps();
    });
});
