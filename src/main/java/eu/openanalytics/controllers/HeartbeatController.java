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

import java.io.IOException;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import eu.openanalytics.services.DockerService;

@Controller
public class HeartbeatController extends BaseController {

	private Logger log = Logger.getLogger(HeartbeatController.class);

	@Inject
	DockerService dockerService;
	
	@RequestMapping("/heartbeat/**")
	void heartbeat(HttpServletRequest request, HttpServletResponse response) {
		dockerService.heartbeatReceived(getUserName(request), getAppName(request));
		try {
			response.setStatus(200);
			response.setContentType("text/html");
			response.getWriter().write("Ok");
			response.getWriter().flush();
		} catch (IOException e) {
			log.error("Failed to send heartbeat response", e);
		}
	}
}
