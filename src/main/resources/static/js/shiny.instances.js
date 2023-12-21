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
Shiny.instances = {

    _template: null,
    _nameRegex: new RegExp('^[a-zA-Z0-9_.-]*$'),
    _refreshIntervalId: null,

    eventHandlers: {
        onShow: function (appName) {
            if (appName === null) {
                Shiny.common.runtimeState.switchInstanceApp = {
                    appName: Shiny.app.staticState.appName,
                    maxInstances: Shiny.common.staticState.appMaxInstances[Shiny.app.staticState.appName],
                    newTab: true,
                }
            } else {
                Shiny.common.runtimeState.switchInstanceApp = {
                    appName: appName,
                    maxInstances: Shiny.common.staticState.appMaxInstances[appName],
                    newTab: false,
                }
            }

            Shiny.instances._refreshModal();
            clearInterval(Shiny.instances._refreshIntervalId);
            Shiny.instances._refreshIntervalId = setInterval(async function () {
                if (!document.hidden) {
                    await Shiny.instances._refreshModal();
                }
            }, 2500);
        },
        onClose: function () {
            clearInterval(Shiny.instances._refreshIntervalId);
            clearInterval(Shiny.instances._detailsRefreshIntervalId); // just to be sure
        },
        showAppDetails: function (event, appName, appInstanceName, proxyId) {
            if (event) {
                event.preventDefault();
            }
            if (appInstanceName === undefined) {
                // when no arguments provided -> show the current app
                appName = Shiny.app.staticState.appName;
                appInstanceName = Shiny.instances._toAppDisplayName(Shiny.app.staticState.appInstanceName);
                proxyId = Shiny.app.runtimeState.proxy.id;
                Shiny.ui.showAppDetailsModal();
            } else {
                Shiny.ui.showAppDetailsModal($('#switchInstancesModal'));
            }
            Shiny.common.loadAppDetails(appName, appInstanceName, proxyId);
        },
        // TODO rename to onStopApp ?
        onDeleteInstance: async function (event, appInstanceName, proxyId) {
            if (event) {
                event.preventDefault();
            }
            if (appInstanceName === undefined) {
                // when no arguments provided -> stop the current app
                appInstanceName = Shiny.instances._toAppDisplayName(Shiny.app.staticState.appInstanceName);
                proxyId = Shiny.app.runtimeState.proxy.id;
            }

            if (confirm("Are you sure you want to stop instance \"" + appInstanceName + "\"?")) {
                if (!await Shiny.api.changeProxyStatus(proxyId, 'Stopping')) {
                    alert("Cannot stop this app now, please try again later");
                    return;
                }
                if (Shiny.instances._isOpenedApp(proxyId)) {
                    Shiny.app.runtimeState.appStopped = true;
                    Shiny.ui.removeFrame();
                    Shiny.ui.showStoppingPage();
                    Shiny.app.runtimeState.proxy = await Shiny.api.waitForStatusChange(Shiny.app.runtimeState.proxy.id);
                    Shiny.ui.showStoppedPage();
                }
            }
        },
        async onPauseApp(event, appInstanceName, proxyId) {
            if (event) {
                event.preventDefault();
            }
            if (appInstanceName === undefined) {
                // when no arguments provided -> pause the current app
                appInstanceName = Shiny.instances._toAppDisplayName(Shiny.app.staticState.appInstanceName);
                proxyId = Shiny.app.runtimeState.proxy.id;
            }

            if (confirm("Are you sure you want to pause instance \"" + appInstanceName + "\"?")) {
                if (!await Shiny.api.changeProxyStatus(proxyId, 'Pausing')) {
                    alert("Cannot pause this app now, please try again later");
                    return;
                }
                if (Shiny.instances._isOpenedApp(proxyId)) {
                    Shiny.app.runtimeState.appStopped = true;
                    Shiny.ui.removeFrame();
                    Shiny.ui.showPausingPage();
                    Shiny.app.runtimeState.proxy = await Shiny.api.waitForStatusChange(Shiny.app.runtimeState.proxy.id);
                    Shiny.ui.showPausedAppPage();
                }
            }
        },
        // TODO rename to onRestartApp?
        onRestartInstance: async function (event) {
            if (event) {
                event.preventDefault();
            }
            if (Shiny.app.runtimeState.appStopped
                || Shiny.app.runtimeState.proxy.status === "Stopped"
                || Shiny.app.runtimeState.proxy.status === "Paused") {
                window.location.reload();
                return;
            } else if (confirm("Are you sure you want to restart the current instance?")) {
                Shiny.app.runtimeState.appStopped = true;
                Shiny.ui.removeFrame();
                Shiny.ui.showStoppingPage();

                await Shiny.api.changeProxyStatus(Shiny.app.runtimeState.proxy.id, 'Stopping');
                Shiny.app.runtimeState.proxy = await Shiny.api.waitForStatusChange(Shiny.app.runtimeState.proxy.id);

                window.location.reload();
            }
        },
        onNewInstance: async function () {
            const appName = Shiny.common.runtimeState.switchInstanceApp.appName;
            const inputField = $("#instanceNameField");
            let instance = inputField.val().trim();

            if (instance === "") {
                return;
            }

            if (instance.toLowerCase() === "default") {
                instance = "_";
            }

            if (instance.length > 64) {
                alert("The provided name is too long (maximum 64 characters)");
                return;
            }

            if (!Shiny.instances._nameRegex.test(instance)) {
                alert("The provided name contains invalid characters (only alphanumeric characters, '_', '-' and '.' are allowed.)");
                return;
            }

            const existingInstances = await Shiny.api.getProxies();
            const maxInstances = Shiny.common.runtimeState.switchInstanceApp.maxInstances;
            let currentAmountOfInstances = 0;
            for (const existingInstance of existingInstances) {
                if (existingInstance.specId === appName) {
                    currentAmountOfInstances++;
                    if (existingInstance.runtimeValues.SHINYPROXY_APP_INSTANCE === instance) {
                        alert("You are already using an instance with this name!");
                        return;
                    }
                }
            }
            if (maxInstances !== -1 && currentAmountOfInstances >= maxInstances) {
                alert("You cannot start a new instance because you are using the maximum amount of instances of this app!");
                return;
            }

            if (Shiny.common.runtimeState.switchInstanceApp.newTab) {
                window.open(Shiny.instances._createUrlForInstance(instance), "_blank");
            } else {
                window.location = Shiny.instances._createUrlForInstance(instance);
            }
            inputField.val('');
            Shiny.ui.hideModal();

        },
    },
    _createUrlForInstance: function (instance) {
        return Shiny.common.staticState.contextPath + "app_i/" + Shiny.common.runtimeState.switchInstanceApp.appName + "/" + instance + "/";
    },
    _refreshModal: async function () {
        let templateData = await Shiny.api.getProxiesAsTemplateData();
        let appName = Shiny.common.runtimeState.switchInstanceApp.appName;
        if (templateData.apps.hasOwnProperty(appName)) {
            templateData = templateData.apps[appName];

            if (Shiny.app.runtimeState.proxy !== null) {
                templateData.instances.forEach(instance => {
                    instance.active = instance.proxyId === Shiny.app.runtimeState.proxy.id
                });
            }

            // put active item in front of the list
            const index = templateData.instances.findIndex(instance => instance.active);
            if (index > 0) { // list may not contain any active instance
                const active = templateData.instances[index];
                templateData.instances.splice(index, 1);
                templateData.instances.unshift(active);
            }
        } else {
            templateData = {"instances": []};
        }

        if (Shiny.common.runtimeState.switchInstanceApp.maxInstances === -1) {
            $('#maxInstances').text("unlimited");
        } else {
            $('#maxInstances').text(Shiny.common.runtimeState.switchInstanceApp.maxInstances);
        }

        $('#usedInstances').text(templateData['instances'].length);

        if (Shiny.common.runtimeState.switchInstanceApp.newTab) {
            templateData['target'] = '_blank';
        } else {
            templateData['target'] = '';
        }

        templateData['pauseSupported'] = Shiny.common.staticState.pauseSupported;

        document.getElementById('appInstances').innerHTML = Handlebars.templates.switch_instances(templateData);
    },
    _toAppDisplayName(appInstanceName) {
        if (appInstanceName === "_") {
            return "Default";
        }
        return appInstanceName;
    },
    _isOpenedApp(proxyId) {
        return Shiny.app !== undefined
            && Shiny.app.runtimeState.proxy != null
            && Shiny.app.runtimeState.proxy.id === proxyId;
    }
};
