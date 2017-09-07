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

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import eu.openanalytics.ShinyProxyApplication;
import eu.openanalytics.services.LogService;
import eu.openanalytics.services.LogService.IssueForm;


@Controller
public class IssueController extends BaseController {

	@Inject
	LogService logService;

	@RequestMapping(value="/issue", method=RequestMethod.POST)
	public String postIssue(HttpServletRequest request, HttpServletResponse response) {
		IssueForm form = new IssueForm();
		form.setUserName(getUserName(request));
		form.setCurrentLocation(request.getParameter("currentLocation"));
		form.setAppName(getAppName(form.getCurrentLocation()));
		form.setCustomMessage(request.getParameter("customMessage"));
		logService.sendSupportMail(form);
		//TODO Redirect to current location
		return "redirect:" + ShinyProxyApplication.getContextPath(environment) + "/";
	}
}
