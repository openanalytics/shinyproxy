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
Shiny.admin = {

    _adminData: null,

    async init() {
        await Shiny.admin._refreshTable();
        Shiny.instances._refreshIntervalId = setInterval(async function () {
            if (!document.hidden) {
                await Shiny.admin._refreshTable();
            }
        }, 2500);
    },

    async _refreshTable() {
        this._adminData = await Shiny.api.getAdminData();
        document.getElementById('allApps').innerHTML = Handlebars.templates.admin(this._adminData);
    },

    showAppDetails(appInstanceName, proxyId, spInstance) {
        let appDetails = null;
        for (const instance of this._adminData.instances) {
            for (const app of instance.apps) {
                if (app.proxyId === proxyId) {
                    appDetails = app;
                }
            }
        }
        if (appDetails === null) {
            console.log("Did not found details for app", appDetails);
            return;
        }

        document.getElementById('appDetails').innerHTML = Handlebars.templates.app_details(appDetails);
        Shiny.ui.showAppDetailsModal();
    },

}
