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
Shiny = window.Shiny || {};
Shiny.connections = {

    /**
     * Starts the process of sending heartbeats. This method is only used as fallback when we cannot piggy-back
     * heartbeats on either a websocket connection or AJAX requests of the Shiny app itself.
     * Therefore heartbeats are only sent when no WebSocket connection is open and when there were no AJAX requests
     * in the last `Shiny.heartBeatRate` milliseconds.
     */
    startHeartBeats: function () {
        Shiny.connections.sendHeartBeat(); // send heartbeat right after loading app to validate the app is working
        setInterval(function () {
            if (Shiny.app.runtimeState.appStopped) {
                return;
            }
            if (!Shiny.connections._webSocketConnectionIsOpen()) {
                var lastHeartbeat = Date.now() - Shiny.app.runtimeState.lastHeartbeatTime;
                if (lastHeartbeat > Shiny.app.staticState.heartBeatRate && Shiny.app.runtimeState.proxy !== null) {
                    Shiny.connections.sendHeartBeat();
                }
            }
        }, Shiny.app.staticState.heartBeatRate);
    },

    /**
     * Send heartbeat and process the result.
     */
    sendHeartBeat: function() {
        // contextPath is guaranteed to end with a slash
        $.post(Shiny.api.buildURL("heartbeat/" + Shiny.app.runtimeState.proxy.id), function() {})
            .fail(function (response) {
                if (Shiny.app.runtimeState.appStopped) {
                    // if stopped in meantime -> ignore
                    return;
                }
                if (response.status === 401) {
                    Shiny.ui.showLoggedOutPage();
                    return;
                }
                try {
                    var res = JSON.parse(response.responseText);
                    if (res !== null && res.status === "fail") {
                        if (res.data === "app_stopped_or_non_existent") {
                            Shiny.ui.showStoppedPage();
                        } else if (res.data === "shinyproxy_authentication_required") {
                            Shiny.ui.showLoggedOutPage();
                        }
                    }
                } catch (error) {
                    // server or connection crashed, let app reconnect
                    // ignore JSON parsing error
                }
            });
    },

    startOpenidRefresh: function() {
        setInterval(function() {
            if (Shiny.app.runtimeState.proxy && Shiny.app.runtimeState.proxy.status === "Stopped") {
                console.log("no openid refresh");
                return;
            }
            $.post(Shiny.api.buildURL("/refresh-openid"));
        }, Shiny.app.staticState.openIdRefreshRate);
    },

    /**
     * Handles a WebSocket error (i.e. close).
     */
    handleWebSocketError: function () {
        const reconnectionMode = Shiny.app.runtimeState.proxy.runtimeValues.SHINYPROXY_WEBSOCKET_RECONNECTION_MODE || "None";
        if (Shiny.app.runtimeState.navigatingAway) {
            return;
        }
        if (Shiny.app.runtimeState.tryingToReconnect) {
            // ignore error
            return;
        }
        Shiny.app.runtimeState.tryingToReconnect = true;
        if (reconnectionMode === "None") {
            // check if app has been stopped but ignore the error (i.e. don't try to reconnect)
            Shiny.connections._checkAppHasBeenStopped(function (isStopped) {
                if (isStopped) {
                    // app was stopped, show stopped screen
                    Shiny.ui.showStoppedPage();
                }
            });
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
            Shiny.ui.hideModal();
            if (reconnectionMode === "Auto"
                || (reconnectionMode === "Confirm"
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

                if (Shiny.app.runtimeState.proxy.runtimeValues.SHINYPROXY_FORCE_FULL_RELOAD) {
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
            return (_shinyFrame.contentDocument.documentElement.textContent || _shinyFrame.contentDocument.documentElement.innerText).indexOf('is starting up, check back in a few seconds.') > -1;
        } catch (error) {
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

    _checkAppHasBeenStopped: function (cb) {
        $.ajax({
            method: 'POST',
            url: Shiny.api.buildURL("heartbeat/" + Shiny.app.runtimeState.proxy.id),
            timeout: 3000,
            success: function () {
                cb(false);
            },
            error: function (response) {
                try {
                    var res = JSON.parse(response.responseText);
                    if (res !== null && res.status === "fail") {
                        if (res.data === "app_stopped_or_non_existent") {
                            cb(true);
                            return;
                        } else if (res.data === "shinyproxy_authentication_required") {
                            Shiny.ui.showLoggedOutPage();
                            // never call call-back, but just redirect to login page
                            return;
                        }
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

    _updateIframeUrl: function(url) {
        if (!Shiny.app.runtimeState.proxy.runtimeValues.SHINYPROXY_TRACK_APP_URL) {
            return;
        }
        if (Shiny.app.runtimeState.navigatingAway || Shiny.app.runtimeState.appStopped) {
            return;
        }
        if (url === undefined || url === null) {
            return;
        }
        if (url.startsWith(Shiny.app.runtimeState.baseFrameUrl)) {
            const newUrl = url.replace(Shiny.app.runtimeState.baseFrameUrl, Shiny.app.runtimeState.parentFrameUrl);
            window.history.replaceState(null, null, newUrl);
        } else if (url.startsWith(Shiny.app.runtimeState.proxy.runtimeValues.SHINYPROXY_PUBLIC_PATH)) {
            const newUrl = url.replace(Shiny.app.runtimeState.proxy.runtimeValues.SHINYPROXY_PUBLIC_PATH, Shiny.app.runtimeState.parentFrameUrl);
            window.history.replaceState(null, null, newUrl);
        }
    },

};
