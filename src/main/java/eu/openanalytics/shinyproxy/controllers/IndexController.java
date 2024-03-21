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
package eu.openanalytics.shinyproxy.controllers;

import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.shinyproxy.ShinyProxySpecProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.view.RedirectView;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.List;

@Controller
public class IndexController extends BaseController {

    /**
     * Allows users on a ShinyProxy deployment <b>with only one app defined</b> to be redirected straight
     * to the only existing app, without going through the Index page.
     */
    private static final String PROXY_LANDING_PAGE_SINGLE_APP_OPTION = "SingleApp";

    /**
     * Allows users on any ShinyProxy deployment to be redirected straight to the first available app,
     * without going through the Index page.
     */
    private static final String PROXY_LANDING_PAGE_FIRST_APP_OPTION = "FirstApp";

    /**
     * Redirects users on a ShinyProxy deployment to the index page.
     */
    private static final String PROXY_LANDING_PAGE_INDEX_OPTION = "/";

    @Inject
    private ShinyProxySpecProvider shinyProxySpecProvider;

    @Inject
    private Environment environment;

    private MyAppsMode myAppsMode;

    private String landingPage;

    @PostConstruct
    public void init() {
        myAppsMode = environment.getProperty("proxy.my-apps-mode", MyAppsMode.class, MyAppsMode.None);
        landingPage = environment.getProperty("proxy.landing-page", "/");
    }

    @RequestMapping("/")
    private Object index(ModelMap map, HttpServletRequest request) {
        if (!landingPage.equals(PROXY_LANDING_PAGE_INDEX_OPTION)
            && !landingPage.equals(PROXY_LANDING_PAGE_SINGLE_APP_OPTION)
            && !landingPage.equals(PROXY_LANDING_PAGE_FIRST_APP_OPTION)) {
            return new RedirectView(landingPage, true);
        }

        List<ProxySpec> apps = proxyService.getUserSpecs();

        // If set to `FirstApp`, redirect to the first app available to the logged-in user
        if (!apps.isEmpty() && landingPage.equals(PROXY_LANDING_PAGE_FIRST_APP_OPTION)) {
            return new RedirectView("/app/" + apps.get(0).getId(), true);
        }
        // If set to `SingleApp` and only one app is available to the logged-in user, redirect to it
        if (apps.size() == 1 && landingPage.equals(PROXY_LANDING_PAGE_SINGLE_APP_OPTION)) {
            return new RedirectView("/app/" + apps.get(0).getId(), true);
        }

        prepareMap(map, request);

        // navbar
        map.put("page", "index");

        map.put("myAppsMode", myAppsMode.toString());

        return "index";
    }

    public enum MyAppsMode {
        Inline,
        Modal,
        None
    }

}
