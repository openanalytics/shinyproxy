/*
 * ShinyProxy
 *
 * Copyright (C) 2016-2020 Open Analytics
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

function ErrorHandlingWebSocket(url, protocols) {
    console.log("Called ErrorHandlingWebSocket");
    var res = new WebSocket(url, protocols);

    function handler() {
        console.log("Handling error of websocket connection.")
        setTimeout(Shiny.webSocketErrorHandler, 1); // execute async to not block other error handling code
    }

    res.addEventListener("error", function (event) {
        handler();
    });

    res.addEventListener("close", function (event) {
        if (!event.wasClean) {
            handler();
        }
    });

    return res;
}

ErrorHandlingWebSocket.prototype = WebSocket.prototype;

window.Shiny = {

    navigatingAway: false,
    reloaded: false,
    containerPath: "",
    injectorIntervalId: null,
    appWasLoaded: false,
    tryingToReconnect: false,
    reloadAttempts: 0,
    maxReloadAttempts: 10,

    isShiny: function () {
        try {
            var _shinyFrame = document.getElementById('shinyframe');
            if (typeof _shinyFrame.contentWindow.Shiny !== 'undefined' &&
                typeof _shinyFrame.contentWindow.Shiny.shinyapp !== 'undefined' &&
                typeof _shinyFrame.contentWindow.Shiny.shinyapp.reconnect === 'function') {
                return true;
            }
        } catch (error) {

        }
        return false;
    },


    webSocketErrorHandler: function () {
        if (Shiny.navigatingAway) {
            return;
        }
        if (Shiny.tryingToReconnect) {
            // ignore error
            return;
        }
        if (confirm("Connection to server lost, do you want to reload the application?")) {
            Shiny.reloadPage();
        }
    },

    reloadPage: function () {
        var _shinyFrame = document.getElementById('shinyframe');
        Shiny.tryingToReconnect = true;
        Shiny.showLoading();
        Shiny.reloadAttempts++;
        if (Shiny.isShiny()) {
            setTimeout(() => {
                if (!_shinyFrame.contentWindow.Shiny.shinyapp.isConnected()) {
                    _shinyFrame.contentWindow.Shiny.shinyapp.reconnect();
                }
                setTimeout(Shiny.checkShinyReloadSucceeded, 250);
            }, 50);
        } else {
            try {
                Shiny.startInjector();
                _shinyFrame.contentWindow.location.reload();
                Shiny.setShinyFrameHeight();
            } catch (error) {
                // page loaded failed very hard -> replace iframe
                $("#shinyframe").remove();
                Shiny.setupIframe();
            }
            setTimeout(Shiny.checkReloadSucceeded, 250);
        }
    },

    reloadPageBackOff: function () {
        console.log("[Reload attempt " + Shiny.reloadAttempts + "/" + Shiny.maxReloadAttempts + "] Reload not succeeded, trying to reload again");
        if (Shiny.reloadAttempts === Shiny.maxReloadAttempts) {
            // reload full page
            window.location.reload();
            return;
        }
        setTimeout(Shiny.reloadPage, 1000 * Shiny.reloadAttempts);
    },

    checkIfIframeHasStartupMessage() {
        try {
            var _shinyFrame = document.getElementById('shinyframe');
            return (_shinyFrame.contentDocument.documentElement.textContent || _shinyFrame.contentDocument.documentElement.innerText).indexOf('ShinyProxy is starting up') > -1;

        } catch (error) {

        }
        return false;
    },

    checkReloadSucceeded: function (checks = 0) {
        var completed = document.getElementById('shinyframe').contentDocument !== null
            && document.getElementById('shinyframe').contentDocument.readyState === "complete"
            && document.getElementById('shinyframe').contentDocument.baseURI !== "about:blank"
            && !Shiny.checkIfIframeHasStartupMessage();

        if (Shiny.reloaded && completed) {
            // we're ok
            Shiny.reloaded = false;
            Shiny.hideLoading();
            Shiny.tryingToReconnect = false;
            Shiny.reloadAttempts = 0;
            return;
        }

        if (!completed) {
            Shiny.reloaded = true;
        }

        if (checks === 4) {
            Shiny.reloaded = false;
            Shiny.reloadPageBackOff();
            return;
        }

        // re-check in 50 ms
        setTimeout(() => Shiny.checkReloadSucceeded(checks + 1), 250);
    },

    checkShinyReloadSucceeded: function (checks = 0) {
        var _shinyFrame = document.getElementById('shinyframe');
        if (_shinyFrame.contentWindow.Shiny.shinyapp.$socket !== null && _shinyFrame.contentWindow.Shiny.shinyapp.$socket.readyState === WebSocket.OPEN) {
            Shiny.hideLoading();
            Shiny.tryingToReconnect = false;
            Shiny.reloadAttempts = 0;
            return;
        }

        if (checks === 4) {
            Shiny.reloadPageBackOff();
            return;
        }

        setTimeout(() => Shiny.checkShinyReloadSucceeded(checks + 1), 250);
    },

    replace: function (text) {
        document.getElementById('shinyframe').contentWindow.WebSocket = ErrorHandlingWebSocket;
        document.getElementById('shinyframe').contentWindow.document.querySelectorAll("iframe").forEach(d => d.contentWindow.document.WebSocket = ErrorHandlingWebSocket);
    },

    startInjector: function () {
        if (Shiny.injectorIntervalId !== null) {
            Shiny.stopInjector();
        }

        var replaced = false;
        Shiny.injectorIntervalId = setInterval(function () {
            try {
                var state = document.getElementById('shinyframe').contentWindow.document.readyState
            } catch {
                return;
            }
            if (replaced && state === "complete") {
                // ok
                Shiny.stopInjector();
            } else if (state !== "complete") {
                Shiny.replace("setInterval");
                replaced = true;
            }
        }, 50);
    },

    stopInjector: function () {
        clearInterval(Shiny.injectorIntervalId);
    },

    setupIframe: function () {
        var $iframe = $('<iframe id="shinyframe" width="100%" style="display:none;" frameBorder="0"></iframe>')
        Shiny.startInjector();
        $iframe.attr("src", Shiny.containerPath);
        $('#loading').before($iframe);
        Shiny.setShinyFrameHeight();
    },

    showLoading: function () {
        $('#shinyframe').hide();
        if (!Shiny.appWasLoaded) {
            $("#loading").show();
        } else {
            $("#reconnecting").show();
        }
    },

    hideLoading: function () {
        $('#shinyframe').show();
        if (!Shiny.appWasLoaded) {
            $("#loading").fadeOut("slow");
        } else {
            $("#reconnecting").fadeOut("slow");
        }
    },

    start: function () {
        if (Shiny.containerPath === "") {
            Shiny.showLoading();
            $.post(window.location.pathname + window.location.search, function (response) {
                Shiny.containerPath = response.containerPath;
                Shiny.setupIframe();
                Shiny.hideLoading();
                Shiny.appWasLoaded = true;
            }).fail(function (request) {
                var newDoc = document.open("text/html", "replace");
                newDoc.write(request.responseText);
                newDoc.close();
            });
        } else {
            Shiny.setupIframe();
            Shiny.hideLoading();
            Shiny.appWasLoaded = true;
        }
    },

    setShinyFrameHeight: function () {
        $('#shinyframe').css('height', ($(window).height()) + 'px');
    }

};

window.onbeforeunload = function (e) {
    window.Shiny.navigatingAway = true;
};
window.addEventListener("load", Shiny.setShinyFrameHeight);
window.addEventListener("resize", Shiny.setShinyFrameHeight);
