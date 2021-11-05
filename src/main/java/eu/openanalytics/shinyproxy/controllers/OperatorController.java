/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2021 Open Analytics
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

import eu.openanalytics.shinyproxy.OperatorCookieFilter;
import eu.openanalytics.shinyproxy.OperatorEnabledCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletRequest;

@Controller
@Conditional(OperatorEnabledCondition.class)
public class OperatorController extends BaseController {

    @RequestMapping(value = "/server-transfer", method = RequestMethod.GET)
    public String getServerTransferPage(ModelMap map, HttpServletRequest request) {
        String redirectUri = request.getParameter("redirectUri");
        String allowedRedirectUri = OperatorCookieFilter.getRedirectUriByMatch(redirectUri);

        map.put("redirectUri", allowedRedirectUri);

        prepareMap(map, request);
        return "server-transfer";
    }

}
