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

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

import eu.openanalytics.services.AppService;
import eu.openanalytics.services.DockerService;

@Controller
public class AppController extends BaseController {

	@Inject
	DockerService dockerService;
	
	@Inject
	AppService appService;

	@RequestMapping("/app/*")
	String app(ModelMap map, HttpServletRequest request) {
		prepareMap(map, request);
		
		String mapping = dockerService.getMapping(getUserName(request), getAppName(request));
		
		String queryString = request.getQueryString();
		if (queryString == null) queryString = "";
		else queryString = "?" + queryString;
		
		map.put("container", "/" + mapping + environment.getProperty("shiny.proxy.landing-page") + queryString);
		map.put("heartbeatRate", environment.getProperty("shiny.proxy.heartbeat-rate", "10000"));
		
		return "app";
	}
}
