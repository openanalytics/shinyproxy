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

import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import javax.inject.Inject;

@Component
public class Thymeleaf {

    @Inject
    private ShinyProxySpecProvider shinyProxySpecProvider;

    public String getAppUrl(ProxySpec proxySpec) {
        UriComponentsBuilder builder = ServletUriComponentsBuilder.fromCurrentContextPath().pathSegment("app", proxySpec.getId());

        if (shinyProxySpecProvider.getHideNavbarOnMainPageLink(proxySpec.getId())) {
            builder.queryParam("sp_hide_navbar", "true");
        }

        return builder.toUriString();
    }

    public boolean openSwitchInstanceInsteadOfApp(ProxySpec proxySpec) {
        return shinyProxySpecProvider.getAlwaysShowSwitchInstance(proxySpec.getId());
    }

    public String getTemplateProperty(String specId, String property) {
        ShinyProxySpecProvider.ShinyProxySpec shinyProxySpec = shinyProxySpecProvider.getShinyProxySpec(specId);
        if (shinyProxySpec == null) {
            return null;
        }
        return shinyProxySpec.getTemplateProperties().get(property);
    }

    public String getTemplateProperty(String specId, String property, String defaultValue) {
        String res = getTemplateProperty(specId, property);
        if (res != null) {
            return res;
        }
        return defaultValue;
    }

}
