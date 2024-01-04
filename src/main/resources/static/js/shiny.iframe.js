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
var shinyProxy = null;
if (window.parent.Shiny !== undefined
    && window.parent.Shiny.connections !== undefined) {
    shinyProxy = window.parent.Shiny;

    var oldWebsocket = window.WebSocket;

    function ErrorHandlingWebSocket(url, protocols) {
        console.log("Called ErrorHandlingWebSocket");
        console.log(url);
        const newUrl = new URL(url); // url is always an absolute URL (starting with ws:// or wss://)
        newUrl.searchParams.append("sp_proxy_id", shinyProxy.app.runtimeState.proxy.id );

        var res = new oldWebsocket(newUrl, protocols);

        function handler() {
            console.log("Handling error of websocket connection.")
            setTimeout(shinyProxy.connections.handleWebSocketError, 1); // execute async to not block other error handling code
        }

        res.addEventListener("error", function () {
            handler();
        });

        res.addEventListener("close", function (event) {
            if (!event.wasClean) {
                handler();
            }
        });

        shinyProxy.app.runtimeState.websocketConnections.push(res);

        return res;
    }

    ErrorHandlingWebSocket.prototype = oldWebsocket.prototype;
    ErrorHandlingWebSocket.CONNECTING = oldWebsocket.CONNECTING;
    ErrorHandlingWebSocket.OPEN = oldWebsocket.OPEN;
    ErrorHandlingWebSocket.CLOSING = oldWebsocket.CLOSING;
    ErrorHandlingWebSocket.CLOSED = oldWebsocket.CLOSED;

    window.WebSocket = ErrorHandlingWebSocket;

    /**
     * Replaces the `fetch` function on the `parent` object by a wrapper function that keeps tracks of the
     * Shiny.lastHeartbeatTime.
     * This can be called on window.fetch to update the Shiny.lastHeartbeatTime everytime a fetch
     * request is sent.
     * Note: the only side effect when a Shiny app would circumvent this function is that more heartbeats than
     * strictly needed are sent.
     * @param parent
     */
    var _replaceFetch = function (parent) {
        var originalFetch = parent.fetch;

        parent.fetch = function () {
            shinyProxy.app.runtimeState.lastHeartbeatTime = Date.now();

            return new Promise((resolve, reject) => {
                originalFetch.apply(this, arguments)
                    .then((response) => {
                        if (response.status === 410 || response.status === 401 || response.status === 503) {
                            response.clone().json().then(function (clonedResponse) {
                                if (clonedResponse.status === "fail" && clonedResponse.data === "app_stopped_or_non_existent") {
                                    shinyProxy.ui.showStoppedPage();
                                } else if (clonedResponse.status === "fail" && clonedResponse.data === "shinyproxy_authentication_required") {
                                    shinyProxy.ui.showLoggedOutPage();
                                } else if (clonedResponse.status === "fail" && clonedResponse.data === "app_crashed") {
                                    shinyProxy.ui.showCrashedPage();
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
    };


    /**
     * Replaces the `open` function on the `parent` object by a wrapper function that keeps tracks of the
     * Shiny.lastHeartbeatTime.
     * This can be called on window.XMLHttpRequest.prototype to update the Shiny.lastHeartbeatTime everytime an AJAX
     * request is sent.
     * Note: the only side effect when a Shiny app would circumvent this function is that more heartbeats than
     * strictly needed are sent.
     * @param parent
     */
    var _replaceOpen = function (parent) {
        var originalOpen = parent.open;

        parent.open = function () {
            this.addEventListener('load', function () {
                if (this.status === 410 || this.status === 401 || this.status === 503) {
                    var res = JSON.parse(this.responseText);
                    if (res !== null && res.status === "fail" && res.data === "app_stopped_or_non_existent") {
                        // app stopped
                        shinyProxy.ui.showStoppedPage();
                    } else if (res !== null && res.status === "fail" && res.data === "shinyproxy_authentication_required") {
                        // app stopped
                        shinyProxy.ui.showLoggedOutPage();
                    } else if (res !== null && res.status === "fail" && res.data === "app_crashed") {
                        shinyProxy.ui.showCrashedPage();
                    }
                }
            });
            shinyProxy.app.runtimeState.lastHeartbeatTime = Date.now();

            return originalOpen.apply(this, arguments);
        }
    };

    _replaceFetch(window);
    _replaceOpen(window.XMLHttpRequest.prototype);

    // update the url when the page changes, e.g. plain HTTP apps
    window.addEventListener('load', function () {
        shinyProxy.connections._updateIframeUrl(window.location.toString());
    });

    // update the url for SPA apps
    var originalReplaceState = window.history.replaceState;
    window.history.replaceState = function (data, title, url) {
        originalReplaceState.call(window.history, data, title, url);
        shinyProxy.connections._updateIframeUrl(window.location.toString());
    };

    // update the url for SPA apps
    var originalPushState = window.history.pushState;
    window.history.pushState = function (data, title, url) {
        originalPushState.call(window.history, data, title, url);
        shinyProxy.connections._updateIframeUrl(window.location.toString());
    };

    // required for some type of applications (e.g. Angular 1: apache zeppelin)
    // note: this event doesn't get triggered for calls to history.replaceState and
    // history.pushState.
    window.addEventListener('popstate', (event) => {
        setTimeout(() => {
            shinyProxy.connections._updateIframeUrl(window.location.toString());
        });
    });

}
