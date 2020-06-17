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
package eu.openanalytics.shinyproxy.controllers;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.view.RedirectView;

import eu.openanalytics.containerproxy.model.spec.ProxySpec;
 
@Controller
public class IndexController extends BaseController {
	
	@RequestMapping("/")
    private Object index(ModelMap map, HttpServletRequest request) {
		String landingPage = environment.getProperty("proxy.landing-page", "/");
		if (!landingPage.equals("/")) return new RedirectView(landingPage);	
		
		prepareMap(map, request);
		
		ProxySpec[] apps = proxyService.getProxySpecs(null, false).toArray(new ProxySpec[0]);
		map.put("apps", apps);

		Map<ProxySpec, String> appLogos = new HashMap<>();
		map.put("appLogos", appLogos);
		
		boolean displayAppLogos = false;
		for (ProxySpec app: apps) {
			if (app.getLogoURL() != null) {
				displayAppLogos = true;
				appLogos.put(app, resolveImageURI(app.getLogoURL()));
			}
		}
		map.put("displayAppLogos", displayAppLogos);

		return "index";
    }
}