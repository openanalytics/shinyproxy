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

import eu.openanalytics.containerproxy.model.spec.AbstractSpecExtension;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionContext;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import eu.openanalytics.shinyproxy.runtimevalues.WebsocketReconnectionMode;

import java.util.HashMap;
import java.util.Map;

public class ShinyProxySpecExtension extends AbstractSpecExtension {

    private WebsocketReconnectionMode websocketReconnectionMode;

    private Boolean shinyForceFullReload;

    private Integer maxInstances;

    private Boolean hideNavbarOnMainPageLink;

    private Boolean alwaysSwitchInstance;

    private String templateGroup;

    private Map<String, String> templateProperties = new HashMap<>();

    public WebsocketReconnectionMode getWebsocketReconnectionMode() {
        return websocketReconnectionMode;
    }

    public void setWebsocketReconnectionMode(WebsocketReconnectionMode websocketReconnectionMode) {
        this.websocketReconnectionMode = websocketReconnectionMode;
    }

    public Boolean getShinyForceFullReload() {
        return shinyForceFullReload;
    }

    public void setShinyForceFullReload(Boolean shinyForceFullReload) {
        this.shinyForceFullReload = shinyForceFullReload;
    }

    public Integer getMaxInstances() {
        return maxInstances;
    }

    public void setMaxInstances(Integer maxInstances) {
        this.maxInstances = maxInstances;
    }

    public Boolean getHideNavbarOnMainPageLink() {
        return hideNavbarOnMainPageLink;
    }

    public void setHideNavbarOnMainPageLink(Boolean hideNavbarOnMainPageLink) {
        this.hideNavbarOnMainPageLink = hideNavbarOnMainPageLink;
    }

    public void setAlwaysSwitchInstance(Boolean alwaysSwitchInstance) {
        this.alwaysSwitchInstance = alwaysSwitchInstance;
    }

    public Boolean getAlwaysShowSwitchInstance() {
        return alwaysSwitchInstance;
    }


    public void setTemplateGroup(String templateGroup) {
        this.templateGroup = templateGroup;
    }

    public String getTemplateGroup() {
        return templateGroup;
    }

    public void setTemplateProperties(Map<String, String> templateProperties) {
        this.templateProperties = templateProperties;
    }

    public Map<String, String> getTemplateProperties() {
        return templateProperties;
    }

    @Override
    public ShinyProxySpecExtension resolve(SpecExpressionResolver resolver, SpecExpressionContext context) {
        return this;
    }
}
