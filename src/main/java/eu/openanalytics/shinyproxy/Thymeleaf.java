/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2024 Open Analytics
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
import eu.openanalytics.containerproxy.service.UserService;
import eu.openanalytics.shinyproxy.external.ExternalAppSpecExtension;
import lombok.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Component
public class Thymeleaf {

    @Inject
    private ShinyProxySpecProvider shinyProxySpecProvider;

    @Inject
    private UserService userService;

    public String getAppUrl(ProxySpec proxySpec) {
        String externalUrl = proxySpec.getSpecExtension(ExternalAppSpecExtension.class).getExternalUrl();
        if (externalUrl != null && !externalUrl.isBlank()) {
            return externalUrl;
        }

        UriComponentsBuilder builder = ServletUriComponentsBuilder.fromCurrentContextPath().pathSegment("app", proxySpec.getId());

        if (shinyProxySpecProvider.getHideNavbarOnMainPageLink(proxySpec)) {
            builder.queryParam("sp_hide_navbar", "true");
        }

        return builder.toUriString();
    }

    public boolean openSwitchInstanceInsteadOfApp(ProxySpec proxySpec) {
        return shinyProxySpecProvider.getAlwaysShowSwitchInstance(proxySpec);
    }

    public String getTemplateProperty(String specId, String property) {
        ProxySpec proxySpec = shinyProxySpecProvider.getSpec(specId);
        if (proxySpec == null) {
            return null;
        }
        return proxySpec.getSpecExtension(ShinyProxySpecExtension.class).getTemplateProperties().get(property);
    }

    public String getTemplateProperty(String specId, String property, String defaultValue) {
        String res = getTemplateProperty(specId, property);
        if (res != null) {
            return res;
        }
        return defaultValue;
    }

    /**
     * Gets all apps.
     */
    public List<ProxySpec> getAllApps() {
        return shinyProxySpecProvider.getSpecs();
    }

    /**
     * Gets all apps the user does not have access to.
     */
    public List<ProxySpec> getAllUnauthorizedApps() {
        return shinyProxySpecProvider.getSpecs().stream()
            .filter(spec -> !userService.canAccess(spec))
            .toList();
    }


    /**
     * Groups the given list of apps according to the template groups.
     * The result only contains a template group if that group contains at least one app.
     */
    public GroupedProxySpecs groupApps(List<ProxySpec> apps) {
        HashMap<String, ArrayList<ProxySpec>> groupedApps = new HashMap<>();
        List<ProxySpec> ungroupedApps = new ArrayList<>();

        for (ProxySpec app : apps) {
            String groupId = app.getSpecExtension(ShinyProxySpecExtension.class).getTemplateGroup();
            if (groupId != null) {
                groupedApps.putIfAbsent(groupId, new ArrayList<>());
                groupedApps.get(groupId).add(app);
            } else {
                ungroupedApps.add(app);
            }
        }

        List<ShinyProxySpecProvider.TemplateGroup> templateGroups = shinyProxySpecProvider.getTemplateGroups().stream().filter((g) -> groupedApps.containsKey(g.getId())).toList();
        return new GroupedProxySpecs(
            apps.stream().map(ProxySpec::getId).toList(),
            apps,
            templateGroups,
            groupedApps,
            ungroupedApps
        );
    }


    @Value
    public static class GroupedProxySpecs {

        List<String> ids;
        List<ProxySpec> apps;
        List<ShinyProxySpecProvider.TemplateGroup> templateGroups;
        HashMap<String, ArrayList<ProxySpec>> groupedApps;
        List<ProxySpec> ungroupedApps;

    }

}
