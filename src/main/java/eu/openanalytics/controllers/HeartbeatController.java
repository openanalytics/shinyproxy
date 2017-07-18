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

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class HeartbeatController extends BaseController {

	private Logger log = Logger.getLogger(HeartbeatController.class);
	
	@RequestMapping("/heartbeat/**")
	void heartbeat(HttpServletRequest request, HttpServletResponse response) {
		userService.heartbeatReceived(getUserName(request), getAppName(request));
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
