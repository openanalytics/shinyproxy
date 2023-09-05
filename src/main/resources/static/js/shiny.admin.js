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
Shiny.admin = {

    _adminData: null,
    _detailsRefreshIntervalId: null,
    _table: null,

    async init() {
        Shiny.admin._adminData = await Shiny.api.getAdminData();
        Shiny.admin._table = $('.table').DataTable({
            data: Shiny.admin._adminData,
            aaSorting: [], // apply no sort by default
            paging: false,
            lengthChange: false,
            buttons: [
                {
                    extend: 'csv'
                },
                {
                    text: 'Stop all',
                    action: function () {
                        Shiny.admin.stopAll();
                    }
                }
            ],
            responsive: {
                details: false
            },
            columns: [
                {
                    data: 'server',
                    className: 'admin-monospace',
                },
                {
                    data: 'proxyId',
                    className: 'admin-monospace',
                },
                {
                    data: null,
                    render: function (data, type) {
                        if (type === 'display') {
                            return Shiny.ui.formatStatus(data.status);
                        }
                        return data;
                    }
                },
                {
                    data: 'userId',
                    className: 'admin-monospace',
                },
                {
                    data: 'appName',
                    className: 'admin-monospace',
                },
                {
                    data: 'instanceName',
                    className: 'admin-monospace',
                },
                {
                    data: 'endpoint',
                    className: 'admin-monospace',
                },
                {
                    data: 'uptime',
                    className: 'admin-monospace',
                },
                {
                    data: 'lastHeartBeat',
                    className: 'admin-monospace',
                },
                {
                    data: 'imageName',
                    className: 'admin-monospace',
                },
                {
                    data: 'imageTag',
                    className: 'admin-monospace',
                },
                {
                    data: null,
                    render: function (data, type) {
                        if (type === 'display') {
                            return `
                               <div class="btn-group btn-group-xs" style="width: 100px; display:block;" role="group">
                                    <button type="button" class="btn btn-primary"
                                            onclick="Shiny.admin.showAppDetails('${data.displayName}', '${data.instanceName}', '${data.proxyId}');">
                                        Details
                                    </button>
                                    <button type="button" class="btn btn-primary"
                                            onclick="Shiny.instances.eventHandlers.onDeleteInstance(event, '${data.instanceName}', '${data.proxyId}');">
                                        Stop
                                    </button>
                                </div>
                            `;
                        }
                        return data;
                    },
                },
            ]
        });
        Shiny.admin._table.buttons().container().prependTo('#allApps');

        window.addEventListener("resize", function () {
            Shiny.admin._table.columns.adjust();
            Shiny.admin._table.responsive.rebuild();
            Shiny.admin._table.responsive.recalc();
        });

        Shiny.admin._refreshIntervalId = setInterval(async function () {
            if (!document.hidden) {
                await Shiny.admin._refreshTable();
            }
        }, 2500);
    },

    async _refreshTable() {
        Shiny.admin._adminData = await Shiny.api.getAdminData();
        Shiny.admin._table.clear().rows.add(Shiny.admin._adminData).draw();
    },

    showAppDetails(appName, appInstanceName, proxyId) {
        function refresh() {
            let appDetails = null;
            for (const app of Shiny.admin._adminData) {
                if (app.proxyId === proxyId) {
                    appDetails = app;
                    break;
                }
            }
            if (appDetails === null) {
                console.log("Did not found details for app", proxyId);
                return;
            }

            document.getElementById('appDetails').innerHTML = Handlebars.templates.app_details(appDetails);
            Shiny.ui.showAppDetailsModal();
        }

        refresh();
        Shiny.admin._detailsRefreshIntervalId = setInterval(function () {
            if (!document.hidden) {
                refresh();
            }
        }, 2500);
    },

    async stopAll() {
        if (confirm("Are you sure you want to stop all apps from all users?")) {
            $("#adminStoppingApps").show();
            $('#adminStoppedApps').hide();
            await Shiny.admin._refreshTable();
            const proxyIds = [];
            for (const proxy of Shiny.admin._adminData) {
                Shiny.api.changeProxyStatus(proxy.proxyId, 'Stopping');
                proxyIds.push(proxy.proxyId);
            }
            // wait for all proxies to be stopped
            while (!await Shiny.admin._areAllProxiesDeleted(proxyIds)) {
                await Shiny.common.sleep(1000);
            }
            $("#adminStoppingApps").hide();
            $('#adminStoppedApps').show();
        }

    },

    async _areAllProxiesDeleted(proxyIds) {
        await Shiny.admin._refreshTable();
        for (const proxy of Shiny.admin._adminData) {
            if (proxyIds.includes(proxy.proxyId)) {
                return false;
            }
        }
        return true;
    },

}
