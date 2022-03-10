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
        shinyForceFullReload: null,
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
     * @param maxInstances
     * @param shinyForceFullReload
     */
    start: function (containerPath, webSocketReconnectionMode, proxyId, heartBeatRate, appName, appInstanceName, maxInstances, shinyForceFullReload) {
        Shiny.app.staticState.heartBeatRate = heartBeatRate;
        Shiny.app.staticState.appName = appName;
        Shiny.app.staticState.appInstanceName = appInstanceName;
        Shiny.app.staticState.maxInstances = parseInt(maxInstances, 10);
        Shiny.app.staticState.shinyForceFullReload = shinyForceFullReload;
        Shiny.instances._template = Handlebars.templates.switch_instances;

        function internalStart() {
            if (containerPath === "") {
                Shiny.ui.setShinyFrameHeight();
                Shiny.ui.showLoading();
                $.post(window.location.pathname + window.location.search, function (response) {
                    Shiny.app.staticState.containerPath = response.containerPath;
                    Shiny.app.staticState.webSocketReconnectionMode = response.webSocketReconnectionMode;
                    Shiny.app.staticState.proxyId = response.proxyId;
                    Shiny.ui.setupIframe();
                    Shiny.ui.showFrame();
                    Shiny.connections.startHeartBeats();
                }).fail(function (request) {
                    if (!Shiny.app.runtimeState.navigatingAway) {
                        var newDoc = document.open("text/html", "replace");
                        newDoc.write(request.responseText);
                        newDoc.close();
                    }
                });
            } else {
                Shiny.app.staticState.containerPath = containerPath;
                Shiny.app.staticState.webSocketReconnectionMode = webSocketReconnectionMode;
                Shiny.app.staticState.proxyId = proxyId;
                Shiny.ui.setupIframe();
                Shiny.ui.showFrame();
                Shiny.connections.startHeartBeats();
            }
        }

        if (Shiny.operator !== undefined) {
            Shiny.operator.start(function() {
                internalStart();
            });
        } else {
            internalStart();
        }
    },

}


window.onbeforeunload = function () {
    window.Shiny.app.runtimeState.navigatingAway = true;
};

window.addEventListener("resize", function () {
    Shiny.ui.setShinyFrameHeight();
});

$(window).on('load', function () {
    Shiny.ui.setShinyFrameHeight();

    $('#switchInstancesModal-btn').click(function () {
        Shiny.instances.eventHandlers.onShow();
    });

    $('#newInstanceForm').submit(function (e) {
        e.preventDefault();
        Shiny.instances.eventHandlers.onNewInstance();
    });

    $('#switchInstancesModal').on('shown.bs.modal', function () {
        setTimeout(function () {
            $("#instanceNameField").focus();
        }, 10);
    });
    
    $('#switchInstancesModal').on('hide.bs.modal', function () {
        Shiny.instances.eventHandlers.onClose();
    });
});