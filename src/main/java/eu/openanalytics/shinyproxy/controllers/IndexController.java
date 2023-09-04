/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2023 Open Analytics
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
import eu.openanalytics.shinyproxy.ShinyProxySpecExtension;
import eu.openanalytics.shinyproxy.ShinyProxySpecProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.view.RedirectView;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class IndexController extends BaseController {

	@Inject
	private ShinyProxySpecProvider shinyProxySpecProvider;

	@Inject
	private Environment environment;

	private MyAppsMode myAppsMode;

	@PostConstruct
	public void init() {
		myAppsMode = environment.getProperty("proxy.my-apps-mode", MyAppsMode.class, MyAppsMode.None);
	}

	@RequestMapping("/")
    private Object index(ModelMap map, HttpServletRequest request) {
		String landingPage = environment.getProperty("proxy.landing-page", "/");
		if (!landingPage.equals("/")) return new RedirectView(landingPage);	
		
		prepareMap(map, request);
		
		ProxySpec[] apps = proxyService.getProxySpecs(null, false).toArray(new ProxySpec[0]);
		map.put("apps", apps);

        // app logos
		Map<ProxySpec, LogoInfo> appLogos = new HashMap<>();
		for (ProxySpec app: apps) {
            appLogos.put(app, getAppLogoInfo(app));
		}
        map.put("appLogos", appLogos);

		// template groups
		HashMap<String, ArrayList<ProxySpec>> groupedApps = new HashMap<>();
		List<ProxySpec> ungroupedApps = new ArrayList<>();

		for (ProxySpec app: apps) {
			String groupId = app.getSpecExtension(ShinyProxySpecExtension.class).getTemplateGroup();
			if (groupId != null) {
				groupedApps.putIfAbsent(groupId, new ArrayList<>());
				groupedApps.get(groupId).add(app);
			} else {
				ungroupedApps.add(app);
			}
		}

		List<ShinyProxySpecProvider.TemplateGroup> templateGroups = shinyProxySpecProvider.getTemplateGroups().stream().filter((g) -> groupedApps.containsKey(g.getId())).collect(Collectors.toList());
		map.put("templateGroups", templateGroups);
		map.put("groupedApps", groupedApps);
		map.put("ungroupedApps", ungroupedApps);

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
