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
        $iframe.attr("src", Shiny.app.runtimeState.containerPath);
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
    showReconnecting: function () {
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
        $("#resumingApp").fadeOut("slow");
        $("#reconnecting").fadeOut("slow");
    },

    /**
     * Update the frame height.
     */
    setShinyFrameHeight: function () {
        // note: we use JS here instead of CSS in order to support custom navbars using any possible height.
        let navbarHeight = $('.navbar-height').height();
        if (navbarHeight === undefined) {
            navbarHeight = 0; // when navbar is hidden
        }
        let height = $(window).height() - navbarHeight;
        $('#shinyframe').css('height', height + 'px');
        $('body').css('padding-top', navbarHeight + 'px');
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

    showResumingPage: function () {
        $('.loading').hide();
        $('#shinyframe').hide();
        $("#resumingApp").show();
    },

    showStoppingPage: function () {
        $('.loading').hide();
        $('#shinyframe').hide();
        $('#modal').modal('hide')
        $("#stoppingApp").show();
    },

    showPausingPage: function () {
        $('.loading').hide();
        $('#shinyframe').hide();
        $('#modal').modal('hide')
        $("#pausingApp").show();
    },

    showPausedAppPage: function () {
        $('#shinyframe').remove();
        $('.loading').hide();
        $('#modal').modal('hide')
        $('#appPaused').show();
    },

    showFailedToReloadPage: function () {
        $('#shinyframe').remove();
        $('.loading').hide();
        $("#reloadFailed").show();
    },

    showStartFailedPage: function () {
        $('#shinyframe').hide();
        $('.loading').hide();
        $("#startFailed").show();
    },

    showStoppedPage: function () {
        Shiny.app.runtimeState.appStopped = true;
        $('#shinyframe').remove();
        $('.loading').hide();
        $('#modal').modal('hide')
        $('#appStopped').show();
    },

    showLoggedOutPage: function () {
        Shiny.app.runtimeState.appStopped = true;
        if (!Shiny.app.runtimeState.navigatingAway) {
            // only show it when not navigating away, e.g. when logging out in the current tab
            $('#shinyframe').remove();
            $("#loading").hide();
            $("#reconnecting").hide();
            $('#modal').modal('hide')
            $("#navbar").hide();
            $('#userLoggedOut').show();
        }
    },

    showParameterForm: function () {
        $('#parameterForm').show();
    },

    showInstanceModal: function () {
        $('#switchInstancesModal').show();
        $('#modal').modal('show');
        setTimeout(function () {
            $("#instanceNameField").focus();
        }, 10);
    },

    showMyAppsModal: function () {
        $('#myAppsModal').show();
        $('#modal').modal('show');
    },

    hideModal: function () {
        $('#modal .modal-dialog').hide();
        $('#modal').modal('hide');
    },

    showAppDetailsModal: function (currentModal) {
        if (currentModal === undefined) {
            $('#appDetailsModal').show();
            $('#modal').modal('show');
            $('#appDetailsModal .close-button').one('click', function (e) {
                $('#modal').modal('hide');
                $('#appDetailsModal').hide();
                Shiny.common.closeAppDetails();
            });
        } else {
            $(currentModal).hide();
            $('#appDetailsModal').show();
            $('#appDetailsModal .close-button').one('click', function (e) {
                $('#appDetailsModal').hide();
                $(currentModal).show();
                Shiny.common.closeAppDetails();
            });
        }
    },

    removeFrame() {
        $('#shinyframe').remove();
    },

    validateParameterForm() {
        for (let i = 0; i < Shiny.app.staticState.parameters.ids.length; i++) {
            const keyName = Shiny.app.staticState.parameters.ids[i];
            let selected = $('select[name=' + keyName + ']').prop('selectedIndex');
            if (selected === 0) {
                $('#selectAllWarning').show();
                return false;
            }
        }
        $('#selectAllWarning').hide();
        return true;
    },

    submitParameterForm() {
        if (!Shiny.ui.validateParameterForm()) {
            return;
        }
        const data = $('#parameterForm form').serializeArray();
        const json = {};
        for (const element of data) {
            json[element.name] = element.value;
        }
        $('#parameterForm').hide();
        Shiny.app.submitParameters(json);
    },

    selectChange(target) {
        $('#selectAllWarning').hide();
        const equals = (a, b) =>
            a.length === b.length &&
            a.every((v, i) => v === b[i]);
        const selectedValues = [];
        const selectedIndex = target.selectedIndex;
        const changedKey = $(target).prop('name');
        const changedOptionIndex = Shiny.app.staticState.parameters.ids.indexOf(changedKey);
        for (let i = 0; i < Shiny.app.staticState.parameters.ids.length; i++) {
            const keyName = Shiny.app.staticState.parameters.ids[i];
            if (i <= changedOptionIndex) {
                let selected = $('select[name=' + keyName + ']').prop('selectedIndex');
                selectedValues.push(selected);
            } else {
                if (i === changedOptionIndex + 1 && selectedIndex !== 0) {
                    $('select[name=' + keyName + ']').prop("disabled", false);
                } else {
                    $('select[name=' + keyName + ']').prop("disabled", true);
                }
                const nextOptions = $('select[name=' + keyName + '] option');
                nextOptions.first().prop("selected", true);
            }
        }

        const allowedNextValues = [];
        for (const allowedValue of Shiny.app.staticState.parameters.allowedCombinations) {
            if (equals(allowedValue.slice(0, selectedValues.length), selectedValues)) {
                allowedNextValues.push(allowedValue[selectedValues.length]);
            }
        }

        const nextKey = Shiny.app.staticState.parameters.ids[selectedValues.length];
        const nextOptions = $('select[name=' + nextKey + '] option');
        for (const nextOption of nextOptions) {
            if (nextOption.index === 0) {
                continue;
            }
            if (allowedNextValues.includes(nextOption.index)) {
                $(nextOption).show();
            } else {
                $(nextOption).hide();
            }
        }
    },

    formatSeconds(time) {
        const hours = Math.floor(time / 3600);
        const minutes = Math.floor((time % 3600) / 60).toString().padStart(2, '0');
        const seconds = Math.floor(time % 60).toString().padStart(2, '0');
        return `${hours}:${minutes}:${seconds}`
    },

    formatStatus(status) {
        if (status === "Up") {
            return `<span class="label status-label label-success">Up</span>`;
        }
        if (status === "New") {
            return `<span class="label status-label label-warning">New</span>`;
        }
        if (status === "Resuming") {
            return `<span class="label status-label label-warning">Resuming</span>`;
        }
        if (status === "Pausing") {
            return `<span class="label status-label label-warning">Pausing</span>`;
        }
        if (status === "Paused") {
            return `<span class="label status-label label-default">Paused</span>`;
        }
        if (status === "Stopping") {
            return `<span class="label status-label label-danger">Stopping</span>`;
        }
        if (status === "Stopped") {
            return `<span class="label status-label label-danger">Stopped</span>`;
        }
        return "";
    }
}

window.addEventListener("resize", function () {
    Shiny.ui.setShinyFrameHeight();
});

$(window).on('load', function () {
    Shiny.ui.setShinyFrameHeight();

    $('#modal').on('hide.bs.modal', function () {
        Shiny.instances.eventHandlers.onClose();
        Shiny.common.onCloseMyApps();
        Shiny.common.closeAppDetails();
        $('#modal .modal-dialog').hide();
    });
});

Handlebars.registerHelper('formatStatus', function (status) {
    return Shiny.ui.formatStatus(status);
});
