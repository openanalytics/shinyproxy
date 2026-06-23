/*
 * ShinyProxy
 *
 * Copyright (C) 2016-2026 Open Analytics
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
import tools.jackson.databind.ObjectMapper;

public class CustomAppDetailsKey extends RuntimeValueKey<CustomAppDetails> {

    public static final CustomAppDetailsKey inst = new CustomAppDetailsKey();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public CustomAppDetailsKey() {
        super("openanalytics.eu/sp-custom-app-details",
            "SHINYPROXY_CUSTOM_APP_DETAILS",
            false,
            true, // include as annotation so that the value can be recovered
            false,
            false,
            true,
            false,
            CustomAppDetails.class);
    }

    @Override
    public CustomAppDetails deserializeFromString(String value) {
        return objectMapper.readValue(value, CustomAppDetails.class);
    }

    @Override
    public String serializeToString(CustomAppDetails value) {
        return objectMapper.writeValueAsString(value);
    }
}
