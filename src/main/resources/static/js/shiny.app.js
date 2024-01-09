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
// noinspection ES6ConvertVarToLetConst

Shiny = window.Shiny || {};
Shiny.app = {

    staticState: {
        appName: null,
        appInstanceName: null,
        maxReloadAttempts: 10,
        heartBeatRate: null,
        openIdRefreshRate: 30000,
        maxInstances: null,
        parameters: {
            allowedCombinations: null,
            names: null,
            ids: null
        },
        appPath: null,
        containerSubPath: null,
    },

    runtimeState: {
        /**
         * @type {?{id: string, status: string, runtimeValues: {SHINYPROXY_PUBLIC_PATH: string, SHINYPROXY_WEBSOCKET_RECONNECTION_MODE: string, SHINYPROXY_FORCE_FULL_RELOAD: boolean, SHINYPROXY_TRACK_APP_URL: boolean}}}
         */
        proxy: null,
        containerPath: null,
        navigatingAway: false,
        reloaded: false,
        tryingToReconnect: false,
        reloadAttempts: 0,
        reloadDismissed: false,
        updateSecondsIntervalId: null,
        websocketConnections: [],
        lastHeartbeatTime: null,
        appStopped: false,
        parentFrameUrl: null, // the current url of the shinyproxy page, i.e. the location of the browser (e.g. http://localhost:8080/app/01_hello); guaranteed to end with /
        baseFrameUrl: null, // the base url of the app iframe (i.e. without any subpath, query parameters, hash location etc.); guaranteed to end with /
    },

    /**
     * Start the Shiny Application.
     * @param proxy
     * @param heartBeatRate
     * @param appName
     * @param appInstanceName
     * @param parameterAllowedCombinations
     * @param parameterDefinitions
     * @param parametersIds
     * @param appPath
     * @param containerSubPath
     */
    start: async function (proxy, heartBeatRate, appName, appInstanceName, parameterAllowedCombinations, parameterDefinitions, parametersIds, appPath, containerSubPath) {
        Shiny.app.staticState.heartBeatRate = heartBeatRate;
        Shiny.app.staticState.appName = appName;
        Shiny.app.staticState.appInstanceName = appInstanceName;
        Shiny.app.staticState.appPath = appPath;
        Shiny.app.staticState.containerSubPath = containerSubPath;
        Shiny.app.staticState.parameters.allowedCombinations = parameterAllowedCombinations;
        Shiny.app.staticState.parameters.names = parameterDefinitions;
        Shiny.app.staticState.parameters.ids = parametersIds;
        Shiny.app.runtimeState.proxy = proxy;
        Shiny.app.loadApp();
    },
    async loadApp() {
        if (Shiny.app.runtimeState.proxy === null) {
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
            Shiny.app.runtimeState.containerPath = Shiny.app.runtimeState.proxy.runtimeValues.SHINYPROXY_PUBLIC_PATH + Shiny.app.staticState.containerSubPath + window.location.hash;
            if (!(await Shiny.app.checkAppHealth())) {
                return;
            }
            Shiny.ui.setupIframe();
            Shiny.ui.showFrame();
            Shiny.connections.startHeartBeats();

            const baseURL = new URL(Shiny.common.staticState.contextPath, window.location.origin);
            let parentUrl = new URL(Shiny.app.staticState.appPath , baseURL).toString();
            if (!parentUrl.endsWith("/")) {
                parentUrl = parentUrl + "/";
            }
            Shiny.app.runtimeState.parentFrameUrl = parentUrl;
            let baseFrameUrl = new URL(Shiny.app.runtimeState.proxy.runtimeValues.SHINYPROXY_PUBLIC_PATH , baseURL).toString();
            if (!baseFrameUrl.endsWith("/")) {
                baseFrameUrl = parentUrl + "/";
            }
            Shiny.app.runtimeState.baseFrameUrl = baseFrameUrl;
        } else if (Shiny.app.runtimeState.proxy.status === "Stopping") {
            Shiny.ui.showStoppingPage();
            // re-send stop request in case previous stop is stuck
            await Shiny.api.changeProxyStatus(Shiny.app.runtimeState.proxy.id, 'Stopping')
            Shiny.app.runtimeState.proxy = await Shiny.api.waitForStatusChange(Shiny.app.runtimeState.proxy.id);
            if (Shiny.app.runtimeState.proxy !== null && !Shiny.app.runtimeState.navigatingAway) {
                Shiny.ui.showStoppedPage();
            }
        } else if (Shiny.app.runtimeState.proxy.status === "Pausing") {
            Shiny.ui.showPausingPage();
            Shiny.app.runtimeState.proxy = await Shiny.api.waitForStatusChange(Shiny.app.runtimeState.proxy.id);
            if (Shiny.app.runtimeState.proxy !== null && !Shiny.app.runtimeState.navigatingAway) {
                Shiny.ui.showPausedAppPage();
            }
        } else {
            Shiny.app.startupFailed();
        }
    },
    async waitForAppStart() {
        const proxy = await Shiny.api.waitForStatusChange(Shiny.app.runtimeState.proxy.id);
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
        await Shiny.api.changeProxyStatus(Shiny.app.runtimeState.proxy.id, 'Resuming', parameters)
        await Shiny.app.waitForAppStart();
    },
    async startAppWithParameters(parameters) {
        Shiny.ui.setShinyFrameHeight();
        Shiny.ui.showLoading();
        if (parameters === null) {
            parameters = {}
        }
        const body = {parameters, timezone: Shiny.ui.getTimeZone()};
        let url = Shiny.api.buildURL('app_i/' + Shiny.app.staticState.appName + '/' + Shiny.app.staticState.appInstanceName);
        let response = await fetch(url, {
            method: 'POST',
            body:  JSON.stringify(body),
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
    async checkAppHealth() {
        // check that the app endpoint is still accessible
        const response = await fetch(Shiny.app.runtimeState.containerPath);
        if (response.status !== 503) {
            return true;
        }
        const json = await response.json();
        if (json.status === "error" && json.message === "app_stopped_or_non_existent") {
            Shiny.ui.showStoppedPage();
            return false;
        }
        if (json.status === "error" && json.message === "app_crashed") {
            Shiny.ui.showCrashedPage();
            return false;
        }
        return true;
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
        e.preventDefault();
        const appId = $(this).data("app-id");
        Shiny.ui.showInstanceModal();
        Shiny.instances.eventHandlers.onShow(appId);
    });

    $('#newInstanceForm').submit(function (e) {
        e.preventDefault();
        Shiny.instances.eventHandlers.onNewInstance();
    });

    $('#changeUserIdForm').submit(function (e) {
        e.preventDefault();
        Shiny.common.onChangeUserId();
    });

    $('#myAppsModal-btn').click(function () {
        Shiny.ui.showMyAppsModal();
        Shiny.common.onShowMyApps();
    });


    $('#parameterForm .default-parameter-form').on('submit', function (e) {
        e.preventDefault();
        Shiny.ui.submitParameterForm();
    });

    $('#parameterForm select').on('change', function (e) {
        Shiny.ui.selectChange(e.target);
    });
});
