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
Shiny.common = {

    staticState: {
        contextPath: null,
        applicationName: null,
        spInstance: null,
    },
    _refreshIntervalId: null,

    init: function (contextPath, applicationName, spInstance) {
        Shiny.common.staticState.contextPath = contextPath;
        Shiny.common.staticState.applicationName = applicationName;
        Shiny.common.staticState.spInstance = spInstance;
    },

    sleep: function (ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    },

    onShowMyApps: function () {
        Shiny.common._refreshModal();
        clearInterval(Shiny.common._refreshIntervalId);
        Shiny.common._refreshIntervalId = setInterval(async function () {
            if (!document.hidden) {
                await Shiny.common._refreshModal();
            }
        }, 2500);
    },

    _refreshModal: async function () {
        const templateData = await Shiny.api.getProxiesAsTemplateData();
        templateData.apps = Object.values(templateData.apps);
        templateData.apps.sort(function (a, b) {
            return a.displayName.toLowerCase() > b.displayName.toLowerCase() ? 1 : -1
        });
        document.getElementById('myApps').innerHTML = Handlebars.templates.my_apps(templateData);
    },

}

$(window).on('load', function () {
    $('#myAppsModal-btn').click(function () {
        Shiny.common.onShowMyApps();
    });
});
