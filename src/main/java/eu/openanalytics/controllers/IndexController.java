/**
 * RDepot
 *
 * Copyright (C) 2012-${year} ${company}
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
package eu.openanalytics.controllers;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

import eu.openanalytics.services.AppService.ShinyApp;
 
@Controller
public class IndexController extends BaseController {
	
	@RequestMapping("/")
    String index(ModelMap map, HttpServletRequest request) {
		prepareMap(map, request);
		
		List<ShinyApp> apps = userService.getAccessibleApps(SecurityContextHolder.getContext().getAuthentication());
		map.put("apps", apps.toArray());

		boolean displayAppLogos = false;
		for (ShinyApp app: apps) {
			if (app.getLogoUrl() != null) displayAppLogos = true;
		}
		map.put("displayAppLogos", displayAppLogos);

		return "index";
    }
}