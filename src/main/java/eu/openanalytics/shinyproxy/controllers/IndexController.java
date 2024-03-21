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

import eu.openanalytics.shinyproxy.ShinyProxySpecProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.view.RedirectView;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

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
