/*
 * ShinyProxy
 *
 * Copyright (C) 2016-2025 Open Analytics
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
package eu.openanalytics.shinyproxy;

import eu.openanalytics.containerproxy.backend.dispatcher.proxysharing.ProxySharingScaler;
import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValueKeyRegistry;
import eu.openanalytics.containerproxy.util.ContextPathHelper;
import eu.openanalytics.shinyproxy.runtimevalues.AppInstanceKey;
import eu.openanalytics.shinyproxy.runtimevalues.CustomAppDetailsKey;
import eu.openanalytics.shinyproxy.runtimevalues.ShinyForceFullReloadKey;
import eu.openanalytics.shinyproxy.runtimevalues.TrackAppUrl;
import eu.openanalytics.shinyproxy.runtimevalues.UserTimeZoneKey;
import eu.openanalytics.shinyproxy.runtimevalues.WebSocketReconnectionModeKey;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

@Configuration
@PropertySource("classpath:application.properties")
public class ShinyProxyConfiguration {

    static {
        RuntimeValueKeyRegistry.addRuntimeValueKey(AppInstanceKey.inst);
        RuntimeValueKeyRegistry.addRuntimeValueKey(ShinyForceFullReloadKey.inst);
        RuntimeValueKeyRegistry.addRuntimeValueKey(WebSocketReconnectionModeKey.inst);
        RuntimeValueKeyRegistry.addRuntimeValueKey(TrackAppUrl.inst);
        RuntimeValueKeyRegistry.addRuntimeValueKey(UserTimeZoneKey.inst);
        RuntimeValueKeyRegistry.addRuntimeValueKey(CustomAppDetailsKey.inst);
    }

    @Inject
    private ContextPathHelper contextPathHelper;

    @PostConstruct
    public void init() {
        ProxySharingScaler.setPublicPathPrefix(contextPathHelper.withEndingSlash() + "app_proxy/");
    }

}
