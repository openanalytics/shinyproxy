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
        },
        showAppDetails: function(appInstanceName, proxyId, spInstance) {
            if (appInstanceName === undefined) {
                // when no arguments provided -> show the current app
                appInstanceName = Shiny.instances._toAppDisplayName(Shiny.app.staticState.appInstanceName);
                proxyId = Shiny.app.staticState.proxyId;
                spInstance = Shiny.common.staticState.spInstance;
                Shiny.ui.showAppDetailsModal();
            } else {
                Shiny.ui.showAppDetailsModal($('#switchInstancesModal'));
            }
            Shiny.common.loadAppDetails(appInstanceName, proxyId, spInstance);
        },
        onDeleteInstance: async function (appInstanceName, proxyId, spInstance) {
            if (appInstanceName === undefined) {
                // when no arguments provided -> stop the current app
                appInstanceName = Shiny.instances._toAppDisplayName(Shiny.app.staticState.appInstanceName);
                proxyId = Shiny.app.staticState.proxyId;
                spInstance = Shiny.common.staticState.spInstance;
            }

            if (confirm("Are you sure you want to stop instance \"" + appInstanceName + "\"?")) {
                await Shiny.instances._deleteInstance(proxyId, spInstance);
                if (Shiny.app !== undefined && proxyId === Shiny.app.staticState.proxyId) {
                    await Shiny.instances._waitUntilInstanceDeleted(Shiny.app.staticState.proxyId, Shiny.common.staticState.spInstance);
                    Shiny.ui.showStoppedPage();
                }
            }
        },
        onRestartInstance: async function () {
            if (confirm("Are you sure you want to restart the current instance?")) {
                Shiny.ui.hideModal();
                Shiny.ui.showLoading();

                if (Shiny.app.runtimeState.appStopped) {
                    window.location.reload(false);
                    return;
                }

                await Shiny.instances._deleteInstance(Shiny.app.staticState.proxyId, Shiny.common.staticState.spInstance);
                await Shiny.instances._waitUntilInstanceDeleted(Shiny.app.staticState.proxyId, Shiny.common.staticState.spInstance);
                window.location.reload(false);
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

            const existingInstances = await Shiny.api.getProxiesOnAllSpInstances();
            if (existingInstances.hasOwnProperty(appName)) {
                const currentAmountOfInstances = existingInstances[appName].length;
                const maxInstances = Shiny.common.runtimeState.switchInstanceApp.maxInstances;
                if (maxInstances !== -1 && currentAmountOfInstances >= maxInstances) {
                    alert("You cannot start a new instance because you are using the maximum amount of instances of this app!");
                    return;
                }
                for (const existingInstance of existingInstances[appName]) {
                    if (existingInstance.runtimeValues.SHINYPROXY_APP_INSTANCE === instance) {
                        alert("You are already using an instance with this name!");
                        return;
                    }
                }
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

    _deleteInstance: async function (proxyId, spInstance) {
        if (Shiny.app !== undefined && proxyId === Shiny.app.staticState.proxyId) {
            Shiny.app.runtimeState.appStopped = true;
            Shiny.ui.removeFrame();
        }
        try {
            await Shiny.api.deleteProxyById(proxyId, spInstance);
        } catch (e) {
            alert("Error stopping proxy, please try again.")
        }
    },

    _waitUntilInstanceDeleted: async function (proxyId, spInstance) {
        while (await Shiny.api.getProxyById(proxyId, spInstance) != null) {
            await Shiny.common.sleep(500);
        }
    },
    _refreshModal: async function () {
        let templateData = await Shiny.api.getProxiesAsTemplateData();
        let appName = Shiny.common.runtimeState.switchInstanceApp.appName;
        if (templateData.apps.hasOwnProperty(appName)) {
            templateData = templateData.apps[appName];

            templateData.instances.forEach(instance => {
                instance.active = instance.spInstance === Shiny.common.staticState.spInstance
                    && instance.appName === appName
                    && instance.instanceName === Shiny.instances._toAppDisplayName(Shiny.app.staticState.appInstanceName)
            });

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
            templateData['target'] = 'target="_blank"';
        } else {
            templateData['target'] = '';
        }

        document.getElementById('appInstances').innerHTML = Handlebars.templates.switch_instances(templateData);
    },
    _createUrlForProxy: function (proxy) {
        const appName = proxy.spec.id;
        const appInstance = proxy.runtimeValues.SHINYPROXY_APP_INSTANCE;
        const appSpInstance = proxy.runtimeValues.SHINYPROXY_INSTANCE;
        if (appSpInstance !== Shiny.common.staticState.spInstance) {
            return Shiny.common.staticState.contextPath + "app_i/" + appName + "/" + appInstance + "/?sp_instance_override=" + appSpInstance;
        } else {
            return Shiny.common.staticState.contextPath + "app_i/" + appName + "/" + appInstance + "/";
        }
    },
    _toAppDisplayName(appInstanceName) {
        if (appInstanceName === "_") {
            return "Default";
        }
        return appInstanceName;
    },
};
