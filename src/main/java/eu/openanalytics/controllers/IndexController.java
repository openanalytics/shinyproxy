/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2017 Open Analytics
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

import eu.openanalytics.services.TagOverrideService;
import eu.openanalytics.services.AppService.ShinyApp;

@Controller
public class IndexController extends BaseController {

	@Inject
	TagOverrideService tagOverrideService;

	@RequestMapping("/")
    String index(ModelMap map, HttpServletRequest request) {
		prepareMap(map, request);

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		List<ShinyApp> apps = userService.getAccessibleApps(authentication);
		map.put("apps", apps.toArray());

		Map<ShinyApp, String> appLogos = new HashMap<>();
		map.put("appLogos", appLogos);
		
		boolean displayAppLogos = false;
		for (ShinyApp app: apps) {
			if (app.getLogoUrl() != null) {
				displayAppLogos = true;
				appLogos.put(app, resolveImageURI(app.getLogoUrl()));
			}
		}
		map.put("displayAppLogos", displayAppLogos);

		boolean isAdmin = userService.isAdmin(authentication);
		boolean canOverrideTags = isAdmin && tagOverrideService.getSecret() != null;
		map.put("canOverrideTags", canOverrideTags);
		if (canOverrideTags) {
			map.put("maxTagOverrideExpirationDays", tagOverrideService.getMaxTagOverrideExpirationDays());
			map.put("defaultTagOverrideExpirationDays", tagOverrideService.getMaxTagOverrideExpirationDays());
		}

		return "index";
    }
}