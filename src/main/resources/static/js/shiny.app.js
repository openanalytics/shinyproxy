// noinspection ES6ConvertVarToLetConst

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
Shiny.app = {

    staticState: {
        proxyId: null,
        appName: null,
        appInstanceName: null,
        containerPath: null,
        webSocketReconnectionMode: null,
        maxReloadAttempts: 10,
        heartBeatRate: null,
        maxInstances: null,
        isSpOverrideActive: null,
        parameters: {
            allowedCombinations: null,
            names: null,
            ids: null
        }
    },

    runtimeState: {
        navigatingAway: false,
        reloaded: false,
        injectorIntervalId: null,
        tryingToReconnect: false,
        reloadAttempts: 0,
        reloadDismissed: false,
        updateSecondsIntervalId: null,
        websocketConnections: [],
        lastHeartbeatTime: null,
        appStopped: false,
    },

    /**
     * Start the Shiny Application.
     * @param containerPath
     * @param webSocketReconnectionMode
     * @param proxyId
     * @param heartBeatRate
     * @param appName
     * @param appInstanceName
     * @param shinyForceFullReload
     * @param isSpOverrideActive
     * @param parameterAllowedCombinations
     * @param parameterDefinitions
     * @param parametersIds
     */
    start: async function (containerPath, webSocketReconnectionMode, proxyId, heartBeatRate, appName, appInstanceName, shinyForceFullReload, isSpOverrideActive, parameterAllowedCombinations, parameterDefinitions, parametersIds) {
        Shiny.app.staticState.heartBeatRate = heartBeatRate;
        Shiny.app.staticState.appName = appName;
        Shiny.app.staticState.appInstanceName = appInstanceName;
        Shiny.app.staticState.shinyForceFullReload = shinyForceFullReload;
        Shiny.app.staticState.isSpOverrideActive = isSpOverrideActive;
        Shiny.app.staticState.parameters.allowedCombinations = parameterAllowedCombinations;
        Shiny.app.staticState.parameters.names = parameterDefinitions;
        Shiny.app.staticState.parameters.ids = parametersIds;

        if (containerPath !== "") {
            Shiny.app.staticState.containerPath = containerPath;
            Shiny.app.staticState.webSocketReconnectionMode = webSocketReconnectionMode;
            Shiny.app.staticState.proxyId = proxyId;
            Shiny.ui.setupIframe();
            Shiny.ui.showFrame();
            Shiny.connections.startHeartBeats();
            Shiny.app.setUpOverride();
        } else {
            if (Shiny.operator === undefined || await Shiny.operator.start()) {
                if (Shiny.app.staticState.isSpOverrideActive) {
                    // do not start new apps on old SP instances -> redirect to same page but without override
                    const overrideUrl = new URL(window.location);
                    overrideUrl.searchParams.delete("sp_instance_override");
                    window.location = overrideUrl;
                    return;
                }
                if (parameterDefinitions !== null) {
                    Shiny.ui.showParameterForm();
                } else {
                    Shiny.app.startAppWithParameters(null);
                }
            }
        }
    },
    async startAppWithParameters(parameters) {
        Shiny.ui.setShinyFrameHeight();
        Shiny.ui.showLoading();
        let response;
        if (parameters !== null) {
            response = await fetch(window.location.pathname + window.location.search, {
                method: 'POST',
                body:  JSON.stringify({parameters}),
                headers: {
                    'Content-Type': 'application/json'
                },
            });
        } else {
            response = await fetch(window.location.pathname + window.location.search, {
                method: 'POST'
            });
        }
        if (response.status !== 200) {
            if (!Shiny.app.runtimeState.navigatingAway && !Shiny.app.runtimeState.appStopped) {
                var newDoc = document.open("text/html", "replace");
                newDoc.write(await response.text());
                newDoc.close();
                return;
            }
        }
        response = await response.json();
        Shiny.app.staticState.containerPath = response.containerPath;
        Shiny.app.staticState.webSocketReconnectionMode = response.webSocketReconnectionMode;
        Shiny.app.staticState.proxyId = response.proxyId;
        Shiny.ui.setupIframe();
        Shiny.ui.showFrame();
        Shiny.connections.startHeartBeats();
        Shiny.app.setUpOverride();
    },
    setUpOverride() {
        if (Shiny.common.staticState.operatorEnabled) {
            var parsedUrl = new URL(Shiny.app.staticState.containerPath, window.location.origin);
            Cookies.set('sp-instance-override', Shiny.common.staticState.spInstance, {path: parsedUrl.pathname});

            const overrideUrl = new URL(window.location);
            overrideUrl.searchParams.set("sp_instance_override", Shiny.common.staticState.spInstance);
            history.replaceState(null, '', overrideUrl);
        }
    }
}


window.onbeforeunload = function () {
    window.Shiny.app.runtimeState.navigatingAway = true;
};


$(window).on('load', function () {
    Shiny.ui.setShinyFrameHeight();

    $('#switchInstancesModal-btn').click(function () {
        Shiny.ui.showInstanceModal();
        Shiny.instances.eventHandlers.onShow(null);
    });

    $('#appDetails-btn').click(function () {
        Shiny.instances.eventHandlers.showAppDetails();
    });

    $('.app-link').on('click auxclick', function(e) {
        const appId = $(e.target).data("app-id");
        Shiny.ui.showInstanceModal();
        Shiny.instances.eventHandlers.onShow(appId);
    });

    $('#newInstanceForm').submit(function (e) {
        e.preventDefault();
        Shiny.instances.eventHandlers.onNewInstance();
    });

    $('#myAppsModal-btn').click(function () {
        Shiny.ui.showMyAppsModal();
        Shiny.common.onShowMyApps();
    });


    $('#parameterForm form').on('submit', function (e) {
        e.preventDefault();
        Shiny.ui.submitParameterForm();
    });

    $('#parameterForm select').on('change', function (e) {
        Shiny.ui.selectChange(e.target);
    });
});
