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

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

import eu.openanalytics.services.AppService;
import eu.openanalytics.services.DockerService;

@Controller
public class AppController {

	@Inject
	DockerService dockerService;
	
	@Inject
	AppService appService;

	@Inject
	Environment environment;

	@RequestMapping("/app/*")
	String app(ModelMap map, Principal principal, HttpServletRequest request) {
		Matcher m = Pattern.compile(".*/app/(.*)").matcher(request.getRequestURI());
		String appName = m.matches() ? m.group(1) : null;
		String mapping = dockerService.getMapping(principal.getName(), appName);
		
		map.put("title", environment.getProperty("shiny.proxy.title"));
		map.put("logo", environment.getProperty("shiny.proxy.logo-url"));
		map.put("container", "/" + mapping + environment.getProperty("shiny.proxy.landing-page"));
		return "app";
	}
}
