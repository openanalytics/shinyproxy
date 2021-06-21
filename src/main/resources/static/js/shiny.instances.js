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
        onShow: function () {
            Shiny.instances._refreshModal();
            clearInterval(Shiny.instances._refreshIntervalId);
            Shiny.instances._refreshIntervalId = setInterval(function() {
                if (!document.hidden) {
                    Shiny.instances._refreshModal();
                }
            }, 2500);
        },
        onClose: function() {
            clearInterval(Shiny.instances._refreshIntervalId);
        },
        onDeleteInstance: function (instanceName) {
            Shiny.instances._deleteInstance(instanceName, function () {
                if (instanceName === Shiny.app.staticState.appInstanceName) {
                    Shiny.ui.showStoppedPage();
                }
            });
        },
        onRestartInstance: function (instanceName) {
            if (instanceName === "Default") {
                instanceName = "_";
            }
            if (instanceName !== Shiny.app.staticState.appInstanceName) {
                return;
            }

            Shiny.ui.hideInstanceModal();
            Shiny.ui.showLoading();

            Shiny.instances._deleteInstance(instanceName, function (proxyId) {
                Shiny.instances._waitUntilInstanceDeleted(proxyId, function () {
                    window.location.reload(false);
                });
            });
        },
        onNewInstance: function () {
            var inputField = $("#instanceNameField");
            var instance = inputField.val().trim();

            if (instance === "") {
                return;
            }

            if (instance.length > 64) {
                alert("The provide name is too long (maximum 64 characters)");
                return;
            }

            if (!Shiny.instances._nameRegex.test(instance)) {
                alert("The provide name contains invalid characters (ony alphanumeric characters, '_', '-' and '.' are allowed.)");
                return;
            }

            if (instance === Shiny.app.staticState.appInstanceName) {
                alert("This instance is already opened in the current tab");
                return;
            }

            // this must be a synchronous call (i.e. without any callbacks) so that the window.open function is not
            // blocked by the browser.
            var currentAmountOfInstances = Shiny.instances._getCurrentAmountOfInstances();
            if (currentAmountOfInstances >= Shiny.app.staticState.maxInstances) {
                alert("You cannot start a new instance because you are using the maximum amount of instances of this app!");
                return;
            }

            window.open(Shiny.instances._createUrlForInstance(instance), "_blank");
            inputField.val('');
            Shiny.ui.hideInstanceModal();

        },
    },

    _createUrlForInstance: function (instance) {
        return Shiny.app.staticState.contextPath + "app/" + Shiny.app.staticState.appName + "/" + instance + "/";
    },

    _deleteInstance: function (instanceName, cb) {
        if (instanceName === "Default") {
            instanceName = "_";
        }
        if (instanceName === Shiny.app.staticState.appInstanceName) {
            Shiny.app.runtimeState.appStopped = true;
            Shiny.ui.removeFrame();
        }
        Shiny.api.getProxyId(Shiny.app.staticState.appName, instanceName, function (proxyId) {
            if (proxyId !== null) {
                Shiny.api.deleteProxyById(proxyId, function () {
                    cb(proxyId);
                });
            }
        });
    },

    _waitUntilInstanceDeleted: function (proxyId, cb) {
        Shiny.api.getProxyById(proxyId, function (found) {
            if (!found) {
                cb();
                return;
            }
            setTimeout(function () {
                Shiny.instances._waitUntilInstanceDeleted(proxyId, cb);
            }, 500);
        });
    },
    _refreshModal: function() {
        Shiny.api.getProxies(function (proxies) {
            var templateData = {'instances': []};

            for (var idx = 0; idx < proxies.length; idx++) {
                var proxy = proxies[idx];

                if (proxy.hasOwnProperty('spec') && proxy.spec.hasOwnProperty('id') &&
                    proxy.hasOwnProperty('runtimeValues') && proxy.runtimeValues.hasOwnProperty('SHINYPROXY_APP_INSTANCE')) {

                    var appInstance = proxy.runtimeValues.SHINYPROXY_APP_INSTANCE;
                    if (proxy.spec.id !== Shiny.app.staticState.appName) {
                        continue;
                    }

                    if (proxy.status !== "Up" && proxy.status !== "Starting" && proxy.status !== "New") {
                        continue;
                    }

                    var proxyName = ""
                    if (appInstance === "_") {
                        proxyName = "Default";
                    } else {
                        proxyName = appInstance;
                    }

                    var active = Shiny.app.staticState.appInstanceName === appInstance;
                    var url = Shiny.instances._createUrlForInstance(appInstance);

                    templateData['instances'].push({name: proxyName, active: active, url: url});
                } else {
                    console.log("Received invalid proxy object from server.");
                }
            }

            templateData['instances'].sort(function (a, b) {
                return a.name.toLowerCase() > b.name.toLowerCase() ? 1 : -1
            });

            document.getElementById('appInstances').innerHTML = Shiny.instances._template(templateData);
        });
    },
    _getCurrentAmountOfInstances: function() {
        var currentAmountOfInstances = 0;

        $.ajax({
            url: Shiny.app.staticState.contextPath + "api/proxy",
            success: function(result) {
                for (var idx = 0; idx < result.length; idx++) {
                    var proxy = result[idx];
                    if (proxy.hasOwnProperty('spec') && proxy.spec.hasOwnProperty('id') && proxy.spec.id === Shiny.app.staticState.appName) {
                        currentAmountOfInstances++;
                    }
                }
            },
            async: false
        });

        return currentAmountOfInstances;
    }
};