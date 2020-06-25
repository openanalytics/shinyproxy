/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2020 Open Analytics
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

import eu.openanalytics.containerproxy.ContainerProxyApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.inject.Inject;

@Component
public class StartupEventListner {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerProxyApplication.class);

    @Inject
    private BuildProperties buildProperties;

    @EventListener
    public void onStartup(ApplicationReadyEvent event) {
        StringBuilder startupMsg = new StringBuilder("Started ");
        startupMsg.append(buildProperties.getName()).append(" ");
        startupMsg.append(buildProperties.getVersion()).append(" (");
        startupMsg.append("ContainerProxy ");
        startupMsg.append(buildProperties.get("containerProxyVersion")).append(")");
        LOGGER.info(startupMsg.toString());
    }
}
