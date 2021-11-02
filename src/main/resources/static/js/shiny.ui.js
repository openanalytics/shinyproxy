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
Shiny.ui = {
    /***
     * Setups the iframe of the application.
     */
    setupIframe: function () {
        var $iframe = $('<iframe id="shinyframe" width="100%" style="display:none;" frameBorder="0"></iframe>')
        // IMPORTANT: start the injector before setting the `src` property of the iframe
        // This is required to ensure that the polling catches all events and therefore the injector works properly.
        Shiny.connections.startInjector();
        $iframe.attr("src", Shiny.app.staticState.containerPath);
        $('#iframeinsert').before($iframe); // insert the iframe into the HTML.
        Shiny.ui.setShinyFrameHeight();
    },

    /**
     * Shows the loading page.
     */
    showLoading: function () {
        $('#appStopped').hide();
        $('#shinyframe').hide();
        $("#loading").show();
    },

    /**
     * Shows the reconnecting page.
     */
    showReconnecting: function() {
        $('#appStopped').hide();
        $('#shinyframe').hide();
        $("#reconnecting").show();
    },

    /**
     *  Hides the loading pages and shows the iframe;
     */
    showFrame: function () {
        $('#shinyframe').show();
        $("#loading").fadeOut("slow");
        $("#reconnecting").fadeOut("slow");
    },

    /**
     * Update the frame height.
     */
    setShinyFrameHeight: function () {
        $('#shinyframe').css('height', ($(window).height()) + 'px');
    },

    updateLoadingTxt: function () {
        if (Shiny.app.runtimeState.updateSecondsIntervalId !== null) {
            clearInterval(Shiny.app.runtimeState.updateSecondsIntervalId);
        }

        function updateSeconds(seconds) {
            if (seconds < 0) {
                clearInterval(Shiny.app.runtimeState.updateSecondsIntervalId);
                return;
            }
            if (seconds === 0) {
                $('#retryInXSeconds').hide();
                $('#retryNow').show();
            } else {
                $('#retryNow').hide();
                $('#retryInXSeconds').show();
                $('.retrySeconds').text(seconds);
            }
        }

        $('.reloadAttempts').text(Shiny.app.runtimeState.reloadAttempts);
        $('.maxReloadAttempts').text(Shiny.app.staticState.maxReloadAttempts);
        updateSeconds(Shiny.app.runtimeState.reloadAttempts);

        var currentSeconds = Shiny.app.runtimeState.reloadAttempts;
        Shiny.app.runtimeState.updateSecondsIntervalId = setInterval(() => {
            currentSeconds--;
            updateSeconds(currentSeconds);
        }, 1000);
    },

    showFailedToReloadPage: function () {
        $('#shinyframe').hide();
        $("#loading").hide();
        $("#reconnecting").hide();
        $("#reloadFailed").show();
    },

    showStoppedPage: function() {
        $('#shinyframe').remove();
        $("#reconnecting").hide();
        $('#switchInstancesModal').modal('hide')
        $('#appStopped').show();
    },

    redirectToLogin: function() {
        if (!Shiny.app.runtimeState.navigatingAway) {
            // only redirect to login when not navigating away, e.g. when logging out
            window.location.href = Shiny.common.staticState.contextPath;
        }
    },

    hideInstanceModal: function() {
        $('#switchInstancesModal').modal('hide');
    },

    removeFrame() {
        $('#shinyframe').remove();
    }
}