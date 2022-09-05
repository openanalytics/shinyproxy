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
package eu.openanalytics.shinyproxy;

import eu.openanalytics.containerproxy.service.IdentifierService;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

@Component
public class OperatorService {

    @Inject
    private Environment environment;

    @Inject
    private IdentifierService identifierService;

    private Boolean isEnabled;

    private Boolean mustForceTransfer;

    private Boolean mustForceTransferWithActiveApps;

    private Boolean showTransferMessageOnMainPage;

    private Boolean showTransferMessageOnAppPage;

    @PostConstruct
    public void init() {
        isEnabled = identifierService.realmId != null;
        mustForceTransfer = environment.getProperty("proxy.operator.force-transfer", Boolean.class, false);
        mustForceTransferWithActiveApps = environment.getProperty("proxy.operator.force-transfer-with-active-apps", Boolean.class, false);
        showTransferMessageOnAppPage = environment.getProperty("proxy.operator.show-transfer-message-app-page", Boolean.class, true);
        showTransferMessageOnMainPage = environment.getProperty("proxy.operator.show-transfer-message-main-page", Boolean.class, true);
    }

    /**
     * @return whether this ShinyProxy server is running in an environment controlled by the ShinyProxy operator.
     */
    public Boolean isEnabled() {
        return isEnabled;
    }

    /**
     * @return whether to force transferring the user to the latest instance if no apps running and if the user
     * is authenticated. (this is unrelated to transferring the user before logging in and after logging out)
     */
    public Boolean mustForceTransfer() {
        return mustForceTransfer;
    }

    /**
     * @return whether to force transferring the user to the latest instance even if apps are running (and if the user
     * is authenticated, this is unrelated to transferring the user before logging in and after logging out)
     */
    public Boolean mustForceTransferWithActiveApps() {
        return mustForceTransferWithActiveApps;
    }

    /**
     * @return whether a message/popup should be shown on the app page when the user is using an old server and they
     * have at least one app running.
     */
    public Boolean showTransferMessageOnAppPage() {
        return showTransferMessageOnAppPage;
    }

    /**
     * @return whether a message/popup should be shown on the main page when the user is using an old server and they
     * have at least one app running.
     */
    public Boolean showTransferMessageOnMainPage() {
        return showTransferMessageOnMainPage;
    }
}
