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
Shiny.operator = {

    staticState: {
        forceTransfer: null,
        forceTransferWithActiveApps: null,
        showTransferMessage: null,
    },

    /**
     * Start the Shiny Application.
     * @param forceTransfer whether to force transferring the user to the latest instance if no apps running
     * @param showTransferMessage whether a message/popup should be shown when the user is using an old server and they
     * have at least one app running.
     */
    init: function (forceTransfer, forceTransferWithActiveApps, showTransferMessage) {
        Shiny.operator.staticState.forceTransfer = forceTransfer;
        Shiny.operator.staticState.forceTransferWithActiveApps = forceTransferWithActiveApps;
        Shiny.operator.staticState.showTransferMessage = showTransferMessage;
        Shiny.common.staticState.operatorEnabled = true;
    },

    start: async function () {
        document.getElementById('new-version-btn').addEventListener("click", async function () {
            const proxies = await Shiny.api.getProxies();
            Shiny.operator.hideMessage();
            if (proxies.length > 0) {
                if (confirm("Warning: you have " + proxies.length + " apps running, your existing session(s) will be closed once you switch to the new version.")) {
                    Shiny.operator.transferToNewInstance();
                }
            } else {
                Shiny.operator.transferToNewInstance();
            }
        });
        if (Shiny.operator.newInstanceAvailable()) {
            if (Shiny.app !== undefined && Shiny.app.staticState.spInstanceOverride) {
                return true;
            }
            // check amount of apps running
            if (Shiny.operator.staticState.forceTransfer) {
                try {
                    const proxies = await Shiny.api.getProxies();
                    if (proxies.length === 0 || Shiny.operator.staticState.forceTransferWithActiveApps) {
                        // force transfer
                        Shiny.operator.transferToNewInstance();
                        return false;
                    }
                } catch (e) {
                    console.log(e);
                }
            }
            // display message
            Shiny.operator.displayMessage();
        }
        return true;
    },

    newInstanceAvailable: function () {
        var spInstanceCookie = Cookies.get('sp-instance');
        var spLatestInstanceCookie = Cookies.get('sp-latest-instance');

        return typeof spInstanceCookie !== 'undefined' && typeof spLatestInstanceCookie !== 'undefined' && spInstanceCookie !== spLatestInstanceCookie;
    },

    displayMessage: function () {
        // only show the message if the option is enabled
        if (Shiny.operator.staticState.showTransferMessage) {
            document.getElementById('new-version-banner').style.display = "block";
            document.getElementById('loading').style.top = "200px";
        }
    },

    hideMessage: function () {
        document.getElementById('new-version-banner').style.display = "none";
    },

    transferToNewInstance: function () {
        $('#loading,#reconnecting,#reloadFailed,#appStopped,#shinyframe').remove();

        $('#applist,#iframeinsert').replaceWith(
            "<div id='server-transfer-message' class='container'>" +
            "<h2>Transferring you to the latest version of " + Shiny.common.staticState.applicationName + " ...</h2>" +
            "</div>");

        Cookies.set('sp-instance', Cookies.get('sp-latest-instance'), {path: Shiny.common.staticState.contextPath});
        location.reload();
    },

}
