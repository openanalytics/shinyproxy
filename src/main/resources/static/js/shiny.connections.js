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
        setTimeout(Shiny.connections.handleWebSocketError, 1); // execute async to not block other error handling code
    }

    res.addEventListener("error", function (event) {
        handler();
    });

    res.addEventListener("close", function (event) {
        if (!event.wasClean) {
            handler();
        }
    });

    Shiny.app.runtimeState.websocketConnections.push(res);

    return res;
}

ErrorHandlingWebSocket.prototype = WebSocket.prototype;

Shiny = window.Shiny || {};
Shiny.connections = {

    /**
     * Starts the process of sending heartbeats. This method is only used as fallback when we cannot piggy-back
     * heartbeats on either a websocket connection or AJAX requests of the Shiny app itself.
     * Therefore heartbeats are only sent when no WebSocket connection is open and when there were no AJAX requests
     * in the last `Shiny.heartBeatRate` milliseconds.
     */
    startHeartBeats: function () {
        setInterval(function () {
            if (!Shiny.connections._webSocketConnectionIsOpen()) {
                var lastHeartbeat = Date.now() - Shiny.app.runtimeState.lastHeartbeatTime;
                if (lastHeartbeat > Shiny.app.staticState.heartBeatRate && Shiny.app.staticState.proxyId !== null) {

                    // contextPath is guaranteed to end with a slash
                    $.post(Shiny.app.staticState.contextPath + "heartbeat/" + Shiny.app.staticState.proxyId);
                }
            }
        }, Shiny.app.staticState.heartBeatRate);
    },

    /**
     * Handles a WebSocket error (i.e. close).
     */
    handleWebSocketError: function () {
        if (Shiny.app.runtimeState.navigatingAway) {
            return;
        }
        if (Shiny.app.runtimeState.tryingToReconnect) {
            // ignore error
            return;
        }
        Shiny.app.runtimeState.tryingToReconnect = true;
        if (Shiny.app.staticState.webSocketReconnectionMode === "None") {
            // ignore error
            return;
        }
        if (Shiny.app.runtimeState.reloadDismissed) {
            // user already dismissed confirmation -> do not ask again
            return;
        }
        if (Shiny.app.runtimeState.appStopped) {
            // app has been stopped -> no need to reconnect
            return;
        }

        // Check if the app has been stopped by another tab
        Shiny.connections._checkAppHasBeenStopped(function (isStopped) {
            if (isStopped) {
                // app was stopped, show stopped screen
                Shiny.ui.showStoppedPage();
                return;
            }
            Shiny.ui.hideInstanceModal();
            if (Shiny.app.staticState.webSocketReconnectionMode === "Auto"
                || (Shiny.app.staticState.webSocketReconnectionMode === "Confirm"
                    && confirm("Connection to server lost, try to reconnect to the application?"))
            ) {
                Shiny.connections._reloadPage();
            } else {
                Shiny.app.runtimeState.reloadDismissed = true;
                Shiny.app.runtimeState.tryingToReconnect = false;
            }
        });
    },

    /**
     * Determines whether this is a Shiny app.
     */
    _isShiny: function () {
        try {
            var _shinyFrame = document.getElementById('shinyframe');
            if (typeof _shinyFrame.contentWindow.Shiny !== 'undefined' &&
                typeof _shinyFrame.contentWindow.Shiny.shinyapp !== 'undefined' &&
                typeof _shinyFrame.contentWindow.Shiny.shinyapp.reconnect === 'function') {

                if (Shiny.app.staticState.shinyForceFullReload) {
                    // this is a Shiny app, but the forceFullReload option is set -> handle it as a non-Shiny app.
                    return false;
                }

                return true;
            }
        } catch (error) {

        }
        return false;
    },


    /**
     * Effectively reloads the application.
     */
    _reloadPage: function () {

        if (Shiny.app.runtimeState.reloadAttempts === Shiny.app.staticState.maxReloadAttempts) {
            // reload full page
            if (confirm("Cannot restore connection server, reload full page?")) {
                window.location.reload();
                return;
            }
            Shiny.ui.showFailedToReloadPage(); // show app again
            return;  // give up
        }

        // Check if the app has been stopped by ShinyProxy server (because of the timeout)
        Shiny.connections._checkAppHasBeenStopped(function (isStopped) {
            if (isStopped) {
                Shiny.app.runtimeState.tryingToReconnect = false;
                Shiny.app.runtimeState.reloadAttempts = 0;
                // app was stopped, show stopped screen
                Shiny.ui.showStoppedPage();
                return;
            }

            var _shinyFrame = document.getElementById('shinyframe');
            Shiny.ui.showReconnecting();
            Shiny.app.runtimeState.reloadAttempts++;
            Shiny.ui.updateLoadingTxt();
            if (Shiny.connections._isShiny()) {
                setTimeout(() => {
                    if (!_shinyFrame.contentWindow.Shiny.shinyapp.isConnected()) {
                        _shinyFrame.contentWindow.Shiny.shinyapp.reconnect();
                    }
                    Shiny.connections._checkShinyReloadSucceeded();
                }, 50);
            } else {
                Shiny.ui.removeFrame();
                Shiny.ui.setupIframe();
                Shiny.connections._checkReloadSucceeded();
            }
        });
    },

    /**
     * Reloads the page, after X seconds. X is the current amount of reload attempts.
     * Therefore, there will be more time between each attempt to reload the page, creating a backoff mechanism.
     * If the Shiny.maxReloadAttempts is reached, the user will be asked whether they want to perform a full reload.
     */
    _reloadPageBackOff: function () {
        console.log("[Reload attempt " + Shiny.app.runtimeState.reloadAttempts + "/" + Shiny.app.staticState.maxReloadAttempts + "] Reload not succeeded, trying to reload again in " + Shiny.app.runtimeState.reloadAttempts + " seconds.");
        setTimeout(Shiny.connections._reloadPage, 1000 * Shiny.app.runtimeState.reloadAttempts);
    },

    /**
     * Check whether the iframe contains the message that ShinyProxy is starting up.
     * If this the case, the iframe looks like it has properly loaded, however, the iframe just contains a message
     * and not the appropriate app.
     */
    _checkIfIframeHasStartupMessage: function () {
        try {
            var _shinyFrame = document.getElementById('shinyframe');
            return (_shinyFrame.contentDocument.documentElement.textContent || _shinyFrame.contentDocument.documentElement.innerText).indexOf('ShinyProxy is starting up') > -1;
        } catch {
        }
        return false;
    },

    /**
     * Checks whether the reload of the application was a success.
     * This is checked 10 times with 250ms between each check.
     * If after 10 checks the app isn't loaded yet, the application is reloaded using Shiny.reloadPageBackOff().
     */
    _checkReloadSucceeded: function (checks = 0) {
        var completed = document.getElementById('shinyframe').contentDocument !== null
            && document.getElementById('shinyframe').contentDocument.readyState === "complete"
            && document.getElementById('shinyframe').contentDocument.baseURI !== "about:blank"
            && !Shiny.connections._checkIfIframeHasStartupMessage();

        if (completed) {
            // we're ok
            Shiny.ui.showFrame();
            Shiny.app.runtimeState.tryingToReconnect = false;
            Shiny.app.runtimeState.reloadAttempts = 0;
            return;
        }

        if (checks === 10) {
            Shiny.connections._reloadPageBackOff();
            return;
        }

        // re-check in 250 ms, in total the page has 2.5 seconds to complete reloading
        setTimeout(() => Shiny.connections._checkReloadSucceeded(checks + 1), 250);
    },

    /**
     * Checks whether the reload of the application was a success in case this is a Shiny app.
     * This is checked 19 times with 250ms between each check.
     * If after 10 checks the app isn't loaded yet, the application is reloaded using Shiny.reloadPageBackOff().
     */
    _checkShinyReloadSucceeded: function (checks = 0) {
        var _shinyFrame = document.getElementById('shinyframe');
        if (_shinyFrame.contentWindow.Shiny.shinyapp.$socket !== null && _shinyFrame.contentWindow.Shiny.shinyapp.$socket.readyState === WebSocket.OPEN) {
            // Shiny.ui.showFrame();
            $("#loading").hide();
            $("#reconnecting").hide();
            $('#shinyframe').show();
            Shiny.app.runtimeState.tryingToReconnect = false;
            Shiny.app.runtimeState.reloadAttempts = 0;
            return;
        }

        if (checks === 10) {
            Shiny.connections._reloadPageBackOff();
            return;
        }

        setTimeout(() => Shiny.connections._checkShinyReloadSucceeded(checks + 1), 250);
    },

    /**
     * Injects the ErrorHandlingWebSocket into the iframe.
     */
    _injectWebSocket: function () {
        var shinyWindow = document.getElementById('shinyframe').contentWindow;
        var shinySubFrames = shinyWindow.document.querySelectorAll("iframe");

        shinyWindow.WebSocket = ErrorHandlingWebSocket;
        shinySubFrames.forEach(d => d.contentWindow.document.WebSocket = ErrorHandlingWebSocket);

        Shiny.connections._replaceOpen(shinyWindow.XMLHttpRequest.prototype);
        shinySubFrames.forEach(d => Shiny.connections._replaceOpen(d.contentWindow.XMLHttpRequest.prototype));

        Shiny.connections._replaceFetch(shinyWindow);
        shinySubFrames.forEach(d => Shiny.connections._replaceFetch(d.contentWindow));
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
        if (Shiny.app.runtimeState.injectorIntervalId !== null) {
            Shiny.connections._stopInjector();
        }

        var replaced = false;
        Shiny.app.runtimeState.injectorIntervalId = setInterval(function () {
            try {
                var state = document.getElementById('shinyframe').contentWindow.document.readyState
            } catch {
                return;
            }
            if (replaced && state === "complete") {
                // ok
                Shiny.connections._stopInjector();
            } else if (state !== "complete") {
                Shiny.connections._injectWebSocket();
                replaced = true;
            }
        }, 50);
    },

    /**
     * Stops the Injector.
     */
    _stopInjector: function () {
        clearInterval(Shiny.app.runtimeState.injectorIntervalId);
    },


    _checkAppHasBeenStopped: function (cb) {
        $.ajax({
            method: 'POST',
            url: Shiny.app.staticState.contextPath + "heartbeat/" + Shiny.app.staticState.proxyId,
            timeout: 3000,
            success: function () {
                cb(false);
            },
            error: function (response) {
                try {
                    var res = JSON.parse(response.responseText);
                    if (res !== null && res.status === "error" && res.message === "app_stopped_or_non_existent") {
                        cb(true);
                        return;
                    }
                } catch (e) {
                    // carry-on
                }

                cb(false);
            }
        });

    },

    /**
     * @returns {boolean} whether at least one WebSocket connection is open
     */
    _webSocketConnectionIsOpen: function () {
        for (var idx = 0; idx < Shiny.app.runtimeState.websocketConnections.length; idx++) {
            if (Shiny.app.runtimeState.websocketConnections[idx].readyState === WebSocket.OPEN) {
                return true;
            }
        }

        return false;
    },

    /**
     * Replaces the `open` function on the `parent` object by a wrapper function that keeps tracks of the
     * Shiny.lastHeartbeatTime.
     * This can be called on window.XMLHttpRequest.prototype to update the Shiny.lastHeartbeatTime everytime an AJAX
     * request is sent.
     * Note: the only side-effect when a Shiny app would circumvents this function is that more heartbeats than
     * strictly needed are sent.
     * @param parent
     */
    _replaceOpen: function (parent) {
        var originalOpen = parent.open;

        parent.open = function () {
            this.addEventListener('load', function () {
                if (this.status === 410) {
                    var res = JSON.parse(this.responseText);
                    if (res !== null && res.status === "error" && res.message === "app_stopped_or_non_existent") {
                        // app stopped
                        Shiny.ui.showStoppedPage();
                    }
                }
            });
            Shiny.app.runtimeState.lastHeartbeatTime = Date.now();

            return originalOpen.apply(this, arguments);
        }
    },

    /**
     * Replaces the `fetch` function on the `parent` object by a wrapper function that keeps tracks of the
     * Shiny.lastHeartbeatTime.
     * This can be called on window.fetch to update the Shiny.lastHeartbeatTime everytime a fetch
     * request is sent.
     * Note: the only side-effect when a Shiny app would circumvents this function is that more heartbeats than
     * strictly needed are sent.
     * @param parent
     */
    _replaceFetch: function (parent) {
        var originalFetch = parent.fetch;

        parent.fetch = function () {
            Shiny.app.runtimeState.lastHeartbeatTime = Date.now();

            return new Promise((resolve, reject) => {
                originalFetch.apply(this, arguments)
                    .then((response) => {
                        if (response.status === 410) {
                            response.clone().json().then(function(clonedResponse) {
                                if (clonedResponse.status === "error" && clonedResponse.message === "app_stopped_or_non_existent") {
                                    Shiny.ui.showStoppedPage();
                                }
                            });
                        }
                        resolve(response);
                    })
                    .catch((error) => {
                        reject(error);
                    })
            });
        }
    },

};