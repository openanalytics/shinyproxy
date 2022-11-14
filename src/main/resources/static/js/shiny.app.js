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
        appName: null,
        appInstanceName: null,
        maxReloadAttempts: 10,
        heartBeatRate: null,
        maxInstances: null,
        isSpOverrideActive: null,
        parameters: {
            allowedCombinations: null,
            names: null,
            ids: null
        },
        containerSubPath: null,
    },

    runtimeState: {
        /**
         * @type {?{id: string, status: string, runtimeValues: {SHINYPROXY_PUBLIC_PATH: string, SHINYPROXY_FORCE_FULL_RELOAD: boolean}}}
         */
        proxy: null,
        containerPath: null,
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
     * @param proxy
     * @param heartBeatRate
     * @param appName
     * @param appInstanceName
     * @param isSpOverrideActive
     * @param parameterAllowedCombinations
     * @param parameterDefinitions
     * @param parametersIds
     */
    start: async function (proxy, heartBeatRate, appName, appInstanceName, isSpOverrideActive, parameterAllowedCombinations, parameterDefinitions, parametersIds, containerSubPath) {
        Shiny.app.staticState.heartBeatRate = heartBeatRate;
        Shiny.app.staticState.appName = appName;
        Shiny.app.staticState.appInstanceName = appInstanceName;
        Shiny.app.staticState.containerSubPath = containerSubPath;
        Shiny.app.staticState.isSpOverrideActive = isSpOverrideActive;
        Shiny.app.staticState.parameters.allowedCombinations = parameterAllowedCombinations;
        Shiny.app.staticState.parameters.names = parameterDefinitions;
        Shiny.app.staticState.parameters.ids = parametersIds;
        Shiny.app.runtimeState.proxy = proxy;
        Shiny.app.loadApp();
    },
    async loadApp() {
        if (Shiny.app.runtimeState.proxy === null) {
            // TODO operator
            // TODO is new app message still shown on app page?
            // if (Shiny.operator === undefined || await Shiny.operator.start()) {
            // if (Shiny.app.staticState.isSpOverrideActive) { // TODO
            //     // do not start new apps on old SP instances -> redirect to same page but without override
            //     const overrideUrl = new URL(window.location);
            //     overrideUrl.searchParams.delete("sp_instance_override");
            //     window.location = overrideUrl;
            //     return;
            // }
            if (Shiny.app.staticState.parameters.names !== null) {
                Shiny.ui.showParameterForm();
            } else {
                Shiny.app.startAppWithParameters(null);
            }
        } else if (Shiny.app.runtimeState.proxy.status === "New"
            || Shiny.app.runtimeState.proxy.status === "Resuming") {
            Shiny.ui.setShinyFrameHeight();
            Shiny.ui.showLoading();
            await Shiny.app.waitForAppStart();
        } else if (Shiny.app.runtimeState.proxy.status === "Paused") {
            if (Shiny.app.staticState.parameters.names !== null) {
                Shiny.ui.showParameterForm();
            } else {
                Shiny.app.resumeApp(null);
            }
        } else if (Shiny.app.runtimeState.proxy.status === "Up") {
            Shiny.app.runtimeState.containerPath = Shiny.app.runtimeState.proxy.runtimeValues.SHINYPROXY_PUBLIC_PATH + Shiny.app.staticState.containerSubPath;
            Shiny.ui.setupIframe();
            Shiny.ui.showFrame();
            Shiny.connections.startHeartBeats();
            Shiny.app.setUpOverride();
        } else if (Shiny.app.runtimeState.proxy.status === "Stopping") {
            Shiny.ui.showStoppingPage();
            Shiny.app.runtimeState.proxy = await Shiny.api.waitForStatusChange(Shiny.app.runtimeState.proxy.id, Shiny.common.staticState.spInstance);
            if (Shiny.app.runtimeState.proxy !== null) {
                Shiny.ui.showStoppedPage();
            }
        } else if (Shiny.app.runtimeState.proxy.status === "Pausing") {
            Shiny.ui.showPausingPage();
            Shiny.app.runtimeState.proxy = await Shiny.api.waitForStatusChange(Shiny.app.runtimeState.proxy.id, Shiny.common.staticState.spInstance);
            if (Shiny.app.runtimeState.proxy !== null) {
                Shiny.ui.showPausedAppPage();
            }
        } else {
            Shiny.app.startupFailed();
        }
    },
    async waitForAppStart() {
        const proxy = await Shiny.api.waitForStatusChange(Shiny.app.runtimeState.proxy.id, Shiny.common.staticState.spInstance);
        Shiny.app.runtimeState.proxy = proxy;
        if (proxy === null || proxy.status === "Stopped") {
            Shiny.app.startupFailed();
        } else {
            Shiny.app.loadApp();
        }
    },
    submitParameters(parameters) {
        if (Shiny.app.runtimeState.proxy === null) {
            Shiny.app.startAppWithParameters(parameters);
        } else if (Shiny.app.runtimeState.proxy.status === "Paused") {
            Shiny.app.resumeApp(parameters);
        }
    },
    async resumeApp(parameters) {
        Shiny.ui.setShinyFrameHeight();
        Shiny.ui.showResumingPage();
        await Shiny.api.changeProxyStatus(Shiny.app.runtimeState.proxy.id, Shiny.common.staticState.spInstance, 'Resuming', parameters)
        await Shiny.app.waitForAppStart();
    },
    async startAppWithParameters(parameters) {
        Shiny.ui.setShinyFrameHeight();
        Shiny.ui.showLoading();
        if (parameters === null) {
            parameters = {}
        }
        let url = Shiny.api.buildURL('app_i/' + Shiny.app.staticState.appName + '/' + Shiny.app.staticState.appInstanceName);
        let response = await fetch(url, {
            method: 'POST',
            body:  JSON.stringify({parameters}),
            headers: {
                'Content-Type': 'application/json'
            },
        });
        if (response.status !== 200) {
            Shiny.app.startupFailed();
            return;
        }
        response = await response.json();
        if (response.status !== "success") {
            Shiny.app.startupFailed();
            return;
        }
        Shiny.app.runtimeState.proxy = response.data;
        await Shiny.app.waitForAppStart();
    },
    startupFailed() {
        if (!Shiny.app.runtimeState.appStopped && !Shiny.app.runtimeState.navigatingAway) {
            Shiny.ui.showStartFailedPage();
        }
    },
    setUpOverride() {
        if (Shiny.common.staticState.operatorEnabled) {
            const baseURL = new URL(Shiny.common.staticState.contextPath, window.location.origin);
            const url = new URL("app_proxy/" + Shiny.app.runtimeState.proxy.id + "/", baseURL);
            Cookies.set('sp-instance-override', Shiny.common.staticState.spInstance, {path: url.pathname});
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

    $('.app-link').click(function(e) {
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
