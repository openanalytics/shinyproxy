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
    },

    /**
     * Start the Shiny Application.
     * @param contextPath
     * @param forceTransfer whether to force transferring the user to the latest instance if no apps running
     * @param cb optional callback after checks are done
     */
    init: function (forceTransfer) {
        Shiny.operator.staticState.forceTransfer = forceTransfer;
    },

    start: function(cb=null) {
        document.getElementById('new-version-btn').addEventListener("click", function() {
            Shiny.api.getProxies(function (proxies) {
                Shiny.operator.hideMessage();
                if (proxies.length > 0) {
                    if (confirm("Warning: you have " + proxies.length + " apps running, your existing session(s) will be closed once you switch to the new version.")) {
                        Shiny.operator.transferToNewInstance();
                    }
                } else {
                    Shiny.operator.transferToNewInstance();
                }
            });
        });
        if (Shiny.operator.newInstanceAvailable()) {
            // check amount of apps running
            if (Shiny.operator.staticState.forceTransfer) {
                Shiny.api.getProxies(function (proxies) {
                    if (proxies.length === 0) {
                        // force transfer
                        Shiny.operator.transferToNewInstance();
                    } else {
                        // display message
                        Shiny.operator.displayMessage();
                        if (cb !== null) cb();
                    }
                }, function() {
                    // failure -> display message
                    Shiny.operator.displayMessage();
                    if (cb !== null) cb();
                });
            } else {
                // display message
                Shiny.operator.displayMessage();
                if (cb !== null) cb();
            }
        } else {
            if (cb !== null) cb();
        }
    },

    newInstanceAvailable: function() {
        var spInstanceCookie = Cookies.get('sp-instance');
        var spLatestInstanceCookie = Cookies.get('sp-latest-instance');

        return typeof spInstanceCookie !== 'undefined' && typeof spLatestInstanceCookie !== 'undefined' && spInstanceCookie !== spLatestInstanceCookie;
    },

    displayMessage: function() {
        document.getElementById('new-version-banner').style.display = "block";
    },

    hideMessage: function() {
        document.getElementById('new-version-banner').style.display = "none";
    },

    transferToNewInstance: function() {
        $('#loading,#reconnecting,#reloadFailed,#appStopped,#shinyframe').remove();

        $('#applist,#iframeinsert').replaceWith(
            "<div id='server-transfer-message' class='container'>" +
                "<h2>Transferring you to the latest version of " + Shiny.common.staticState.applicationName + " ...</h2>" +
            "</div>");

        Cookies.set('sp-instance', Cookies.get('sp-latest-instance'),  {path: Shiny.common.staticState.contextPath});
        location.reload();
    }

}

