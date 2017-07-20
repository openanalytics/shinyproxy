/**
 * Copyright 2016 Open Analytics, Belgium
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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