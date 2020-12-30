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

window.navigatingAway = false;

window.onbeforeunload = function (e) {
    window.navigatingAway = true;
};


function ErrorHandlingWebSocket(url, protocols) {
    console.log("Called ErrorHandlingWebSocket");
    var res = new WebSocket(url, protocols);

    function handler() {
        if (window.navigatingAway) {
            return;
        }
        console.log("Handling error of websocket connection.")
        if (confirm("Connection to server lost, do you want to reload the application?")) {
            reloadPage();
        }
    }

    res.addEventListener("error", function(event) {
        console.log(event);
        handler();
    });

    res.addEventListener("close", function(event) {
        console.log(event);
        if (!event.wasClean) {
            handler();
        }
    });

    return res;
}

ErrorHandlingWebSocket.prototype = WebSocket.prototype;

function reloadPage() {
    var _shinyFrame = document.getElementById('shinyframe');
    if (typeof _shinyFrame.contentWindow.Shiny !== 'undefined' &&
        typeof _shinyFrame.contentWindow.Shiny.shinyapp !== 'undefined' &&
        typeof _shinyFrame.contentWindow.Shiny.shinyapp.reconnect === 'function') {
        setTimeout(() => _shinyFrame.contentWindow.Shiny.shinyapp.reconnect(), 50);
    } else {
        window.reloaded = true;
        _shinyFrame.contentWindow.location.reload();
        setTimeout(checkReloadSucceeded, 50);
    }
}

function checkReloadSucceeded(checks = 0) {
    if (document.getElementById('shinyframe').contentDocument !== null
        && document.getElementById('shinyframe').contentDocument.readyState === "complete") {
        // we're ok
        console.log("Reload succeeded, checks:", checks);
        if (checks === 2) {
            console.log("Reload succeeded");
            return;
        }
    } else {
        console.log("Reload not yet succeeded, checks:", checks);
        if (checks !== 5) {
            console.log("Reload not succeeded after 5 checks, trying to reload again");
            reloadPage();
            return;
        }
    }
    // re-check in 50 ms
    setTimeout(() => checkReloadSucceeded(checks  + 1), 50);
}
