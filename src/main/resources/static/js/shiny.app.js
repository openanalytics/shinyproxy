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

/**
 * @typedef {Object} Shiny.RuntimeValues
 * @property {string} SHINYPROXY_PUBLIC_PATH
 * @property {string} SHINYPROXY_WEBSOCKET_RECONNECTION_MODE
 * @property {string} SHINYPROXY_FORCE_FULL_RELOAD
 * @property {string} SHINYPROXY_TRACK_APP_URL
 * @property {string} SHINYPROXY_APP_INSTANCE
 */
/**
 * A number, or a string containing a number.
 * @typedef {Object} Shiny.Proxy
 * @property {string} id
 * @property {string} specId
 * @property {string} status
 * @property {Shiny.RuntimeValues} runtimeValues
 */

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
        wasAutomaticReloaded: false
    },

    runtimeState: {
        /**
         * @type {Shiny.Proxy}
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
        Shiny.app.checkWasAutomaticReload();
        Shiny.app.loadApp();
        if (Shiny.app.runtimeState.proxy.status === "Up") {
            Shiny.app.checkAppCrashedOrStopped();
        }
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
            Shiny.ui.setupIframe();
            Shiny.ui.showFrame();
            Shiny.connections.startHeartBeats();

            const baseURL = new URL(Shiny.common.staticState.contextPath, window.location.origin);
            let parentUrl = new URL(Shiny.app.staticState.appPath, baseURL).toString();
            if (!parentUrl.endsWith("/")) {
                parentUrl = parentUrl + "/";
            }
            Shiny.app.runtimeState.parentFrameUrl = parentUrl;
            let baseFrameUrl = new URL(Shiny.app.runtimeState.proxy.runtimeValues.SHINYPROXY_PUBLIC_PATH, baseURL).toString();
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
            if (!Shiny.app.staticState.wasAutomaticReloaded) {
                Shiny.app.checkAppCrashedOrStopped(false).then((appCrashedOrStopped) => {
                    if (appCrashedOrStopped) {
                        Shiny.ui.showLoading();
                        const url = new URL(window.location);
                        url.searchParams.append("sp_automatic_reload", "true");
                        window.location = url;
                    }
                });
            } else {
                Shiny.app.checkAppCrashedOrStopped();
            }
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
            body: JSON.stringify(body),
            headers: {
                'Content-Type': 'application/json'
            },
        });
        try {
            const json = await response.json();
            if (response.status !== 200) {
                if (json.status === "fail" && json.data !== null) {
                    Shiny.app.startupFailed(json.data);
                } else {
                    Shiny.app.startupFailed();
                }
                return;
            }
            if (json.status !== "success") {
                Shiny.app.startupFailed();
                return;
            }
            Shiny.app.runtimeState.proxy = json.data;
            await Shiny.app.waitForAppStart();
        } catch {
            Shiny.app.startupFailed();
        }
    },
    startupFailed(errorMessage) {
        if (!Shiny.app.runtimeState.appStopped && !Shiny.app.runtimeState.navigatingAway) {
            Shiny.ui.showStartFailedPage(errorMessage);
        }
    },
    async checkAppCrashedOrStopped(showError = true) {
        // check that the app endpoint is still accessible
        try {
            const response = await fetch(Shiny.app.runtimeState.containerPath);
            if (response.status !== 503 && response.status !== 410) {
                return false;
            }
            const json = await response.json();
            if (json.status === "fail" && json.data === "app_stopped_or_non_existent") {
                if (showError) {
                    Shiny.ui.showStoppedPage();
                }
                return true;
            }
            if (json.status === "fail" && json.data === "app_crashed") {
                if (showError) {
                    Shiny.ui.showCrashedPage();
                }
                return true;
            }
        } catch (e) {
            // ignore, server might not be reachable now, so app may still be running
        }
        return false;
    },
    checkWasAutomaticReload() {
        // If the app crashes immediately after starting it, ShinyProxy will reload the page a single time.
        // A flag in the URL indicates that the page was automatically reloaded and thus a second reload should not
        // be attempted. After reload this flag is removed from the URL.
        const url = new URL(window.location);
        if (url.searchParams.has("sp_automatic_reload")) {
            Shiny.app.staticState.wasAutomaticReloaded = true;
            url.searchParams.delete("sp_automatic_reload");
            window.history.replaceState(null, null, url);
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

    $('.app-link').on('click auxclick', function (e) {
        e.preventDefault();
        const appId = $(this).data("app-id");
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


    $('#parameterForm .default-parameter-form').on('submit', function (e) {
        e.preventDefault();
        Shiny.ui.submitParameterForm();
    });

    $('#parameterForm select').on('change', function (e) {
        Shiny.ui.selectChange(e.target);
    });
});
