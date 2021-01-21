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
function ErrorHandlingWebSocket(url, protocols) {
    console.log("Called ErrorHandlingWebSocket");
    var res = new WebSocket(url, protocols);

    function handler() {
        console.log("Handling error of websocket connection.")
        setTimeout(Shiny.handleWebSocketError, 1); // execute async to not block other error handling code
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
    containerPath: null,
    webSocketReconnectionMode: null,
    injectorIntervalId: null,
    appWasLoaded: false,
    tryingToReconnect: false,
    reloadAttempts: 0,
    maxReloadAttempts: 10,
    reloadDismissed: false,

    /**
     * Determines whether this is a Shiny app.
     */
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

    /**
     * Handles a WebSocket error (i.e. close).
     */
    handleWebSocketError: function () {
        if (Shiny.navigatingAway) {
            return;
        }
        if (Shiny.tryingToReconnect) {
            // ignore error
            return;
        }
        if (Shiny.webSocketReconnectionMode === "None") {
            // ignore error
            return;
        }
        if (Shiny.reloadDismissed) {
            // user already dismissed confirmation -> do not ask again
            return;
        }

        if (Shiny.webSocketReconnectionMode === "Auto"
            || (Shiny.webSocketReconnectionMode === "Confirm"
                && confirm("Connection to server lost, try to reconnect to the application?"))
                ) {
            Shiny.reloadPage();
        } else {
            Shiny.reloadDismissed = true;
        }
    },

    /**
     * Effectively reloads the application.
     */
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
                Shiny.checkShinyReloadSucceeded();
            }, 50);
        } else {
            $("#shinyframe").remove();
            Shiny.setupIframe();
            Shiny.setShinyFrameHeight();
            Shiny.checkReloadSucceeded();
        }
    },

    /**
     * Reloads the page, after X seconds. X is the current amount of reload attempts.
     * Therefore, there will be more time between each attempt to reload the page, creating a backoff mechanism.
     * If the Shiny.maxReloadAttempts is reached, the user will be asked whether they want to perform a full reload.
     */
    reloadPageBackOff: function () {
        console.log("[Reload attempt " + Shiny.reloadAttempts + "/" + Shiny.maxReloadAttempts + "] Reload not succeeded, trying to reload again in " + Shiny.reloadAttempts + " seconds.");
        if (Shiny.reloadAttempts === Shiny.maxReloadAttempts) {
            // reload full page
            if (confirm("Cannot restore connection server, reload full page?")) {
                window.location.reload();
                return;
            }
            return;  // give up
        }
        setTimeout(Shiny.reloadPage, 1000 * Shiny.reloadAttempts);
    },

    /**
     * Check whether the iframe contains the message that ShinyProxy is starting up.
     * If this the case, the iframe looks like it has properly loaded, however, the iframe just contains a message
     * and not the appropriate app.
     */
    checkIfIframeHasStartupMessage: function() {
        try {
            var _shinyFrame = document.getElementById('shinyframe');
            return (_shinyFrame.contentDocument.documentElement.textContent || _shinyFrame.contentDocument.documentElement.innerText).indexOf('ShinyProxy is starting up') > -1;
        } catch {
        }
        return false;
    },

    /**
     * Checks whether the reload of the application was a success.
     * This is checked 4 times with 250ms between each check.
     * If after 5 checks the app isn't loaded yet, the application is reloaded using Shiny.reloadPageBackOff().
     */
    checkReloadSucceeded: function (checks = 0) {
        var completed = document.getElementById('shinyframe').contentDocument !== null
            && document.getElementById('shinyframe').contentDocument.readyState === "complete"
            && document.getElementById('shinyframe').contentDocument.baseURI !== "about:blank"
            && !Shiny.checkIfIframeHasStartupMessage();

        if (completed) {
            // we're ok
            Shiny.hideLoading();
            Shiny.tryingToReconnect = false;
            Shiny.reloadAttempts = 0;
            return;
        }

        if (checks === 4) {
            Shiny.reloadPageBackOff();
            return;
        }

        // re-check in 50 ms
        setTimeout(() => Shiny.checkReloadSucceeded(checks + 1), 200);
    },

    /**
     * Checks whether the reload of the application was a success in case this is a Shiny app.
     * This is checked 4 times with 250ms between each check.
     * If after 4 checks the app isn't loaded yet, the application is reloaded using Shiny.reloadPageBackOff().
     */
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

    /**
     * Injects the ErrorHandlingWebSocket into the iframe.
     */
    injectWebSocket: function () {
        document.getElementById('shinyframe').contentWindow.WebSocket = ErrorHandlingWebSocket;
        document.getElementById('shinyframe').contentWindow.document.querySelectorAll("iframe").forEach(d => d.contentWindow.document.WebSocket = ErrorHandlingWebSocket);
    },

    /**
     * Starts the injection of the ErrorHandlingWebSocket into the iframe.
     * The idea is to poll the iframe every 50ms for its readyState. If the readyState has changed from something
     * different than `complete` to `complete` we know that the injection was successful.
     *
     * The goal is to inject between the `interactive` and `DOMContentLoaded` states.
     * Unfortunately, there is no event generated by the browser which we could use. Therefore we have to fallback to
     * a polling approach.
     *
     * See: https://developer.mozilla.org/en-US/docs/Web/API/Document/readystatechange_event
     * See: https://developer.mozilla.org/en-US/docs/Web/API/Document/readyState
     */
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
                Shiny.injectWebSocket("setInterval");
                replaced = true;
            }
        }, 50);
    },

    /**
     * Stops the Injector.
     */
    stopInjector: function () {
        clearInterval(Shiny.injectorIntervalId);
    },

    /***
     * Setups the iframe of the application.
     */
    setupIframe: function () {
        var $iframe = $('<iframe id="shinyframe" width="100%" style="display:none;" frameBorder="0"></iframe>')
        // IMPORTANT: start the injector before setting the `src` property of the iframe
        // This is required to ensure that the polling catches all events and therefore the injector works properly.
        Shiny.startInjector();
        $iframe.attr("src", Shiny.containerPath);
        $('#iframeinsert').before($iframe); // insert the iframe into the HTML.
        Shiny.setShinyFrameHeight();
    },

    /**
     * Shows the loading page.
     * If the application already has loaded, the message will contains `Reconnecting` instead of `Launching`.
     */
    showLoading: function () {
        $('#shinyframe').hide();
        if (!Shiny.appWasLoaded) {
            $("#loading").show();
        } else {
            $("#reconnecting").show();
        }
    },

    /**
     *  Hides the loading page.
     */
    hideLoading: function () {
        $('#shinyframe').show();
        if (!Shiny.appWasLoaded) {
            $("#loading").fadeOut("slow");
        } else {
            $("#reconnecting").fadeOut("slow");
        }
    },

    /**
     * Start the Shiny Application.
     * @param containerPath
     * @param webSocketReconnectionMode
     */
    start: function (containerPath, webSocketReconnectionMode) {
        if (containerPath === "") {
            Shiny.setShinyFrameHeight();
            Shiny.showLoading();
            $.post(window.location.pathname + window.location.search, function (response) {
                Shiny.containerPath = response.containerPath;
                Shiny.webSocketReconnectionMode = response.webSocketReconnectionMode;
                Shiny.setupIframe();
                Shiny.hideLoading();
                Shiny.appWasLoaded = true;
            }).fail(function (request) {
                if (!Shiny.navigatingAway) {
                    var newDoc = document.open("text/html", "replace");
                    newDoc.write(request.responseText);
                    newDoc.close();
                }
            });
        } else {
            Shiny.containerPath = containerPath;
            Shiny.webSocketReconnectionMode = webSocketReconnectionMode;
            Shiny.setupIframe();
            Shiny.hideLoading();
            Shiny.appWasLoaded = true;
        }
    },

    /**
     * Update the frame height.
     */
    setShinyFrameHeight: function () {
        $('#shinyframe').css('height', ($(window).height()) + 'px');
    }

};

window.onbeforeunload = function (e) {
    window.Shiny.navigatingAway = true;
};
window.addEventListener("load", Shiny.setShinyFrameHeight);
window.addEventListener("resize", Shiny.setShinyFrameHeight);
