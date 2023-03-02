/**
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
package eu.openanalytics.shinyproxy.runtimevalues;


import eu.openanalytics.containerproxy.model.runtime.runtimevalues.RuntimeValueKey;

public class TrackAppUrl extends RuntimeValueKey<Boolean> {

    public TrackAppUrl() {
        super("openanalytics.eu/sp-track-app-url",
                "SHINYPROXY_TRACK_APP_URL",
                false,
                true,
                false,
                true,
                true,
                false,
                Boolean.class);
    }

    public static TrackAppUrl inst = new TrackAppUrl();

    @Override
    public Boolean deserializeFromString(String value) {
        return Boolean.valueOf(value);
    }

    @Override
    public String serializeToString(Boolean value) {
        return value.toString();
    }
}
