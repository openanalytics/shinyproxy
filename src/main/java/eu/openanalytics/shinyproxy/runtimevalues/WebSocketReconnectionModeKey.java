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
package eu.openanalytics.shinyproxy.runtimevalues;


import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValueKey;

public class WebSocketReconnectionModeKey extends RuntimeValueKey<WebsocketReconnectionMode> {

    public static final WebSocketReconnectionModeKey inst = new WebSocketReconnectionModeKey();

    public WebSocketReconnectionModeKey() {
        super("openanalytics.eu/sp-websocket-reconnection-mode",
            "SHINYPROXY_WEBSOCKET_RECONNECTION_MODE",
            false,
            true,
            false,
            true,
            false,
            false,
            WebsocketReconnectionMode.class);
    }

    @Override
    public WebsocketReconnectionMode deserializeFromString(String value) {
        return WebsocketReconnectionMode.valueOf(value);
    }

    @Override
    public String serializeToString(WebsocketReconnectionMode value) {
        return value.name();
    }
}
