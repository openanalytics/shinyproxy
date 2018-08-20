/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2018 Open Analytics
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

import java.util.List;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import eu.openanalytics.shinyproxy.ShinyProxyApplication;
import eu.openanalytics.shinyproxy.entity.App;
import eu.openanalytics.shinyproxy.entity.AppGroup;
import eu.openanalytics.shinyproxy.entity.AppUser;
import eu.openanalytics.shinyproxy.services.AppService;
import eu.openanalytics.shinyproxy.services.ProxyService;
import eu.openanalytics.shinyproxy.services.ShinyAppGroupServiceImpl;
import eu.openanalytics.shinyproxy.services.ShinyAppServiceImpl;
import eu.openanalytics.shinyproxy.services.ShinyAppUserServiceImpl;

@Controller
public class ShinyAppController extends BaseController {

	@Inject
	ProxyService proxyService;
	
	@Inject
	ShinyAppUserServiceImpl shinyAppUserServiceImpl;
	
	@Inject
	ShinyAppGroupServiceImpl shinyAppGroupServiceImpl;

	@RequestMapping(value="/shinyapp", method=RequestMethod.GET)
	String app(ModelMap map, HttpServletRequest request) {		
		prepareMap(map, request);
		
		map.put("apps", appServiceImpl.getApps());
		
		return "shinyapp";
	}
	
	@GetMapping("/shinyapp/add")
    public String addShinyApp(Model model) {
        model.addAttribute("app", new App());
        return "shinyappadd";
    }

    @PostMapping("/shinyapp/add")
    public String addShinyApp(@ModelAttribute App app) {
    	appServiceImpl.saveApp(app);
        return "redirect:/";
    }
    
    @GetMapping("/shinyapp/adduser")
    public String addShinyAppUser(Model model) {
    	
    	List<App> apps = appServiceImpl.getApps();
    	
        model.addAttribute("appUser", new AppUser());        
        model.addAttribute("apps", apps);
        
        return "shinyappadduser";
    }

    @PostMapping("/shinyapp/adduser")
    public String addShinyAppUser(@ModelAttribute AppUser appUser) {
    	shinyAppUserServiceImpl.saveAppUser(appUser);
        return "redirect:/";
    }
    
    @GetMapping("/shinyapp/addgroup")
    public String addShinyAppGroup(Model model) {
    	
    	List<App> apps = appServiceImpl.getApps();
    	
        model.addAttribute("appGroup", new AppGroup());        
        model.addAttribute("apps", apps);
        
        return "shinyappaddgroup";
    }

    @PostMapping("/shinyapp/addgroup")
    public String addShinyAppGroup(@ModelAttribute AppGroup appGroup) {
    	shinyAppGroupServiceImpl.saveAppGroup(appGroup);
        return "redirect:/";
    }
    
    @RequestMapping(value="/shinyapp/listuser", method=RequestMethod.GET)
	String listShinyAppUser(ModelMap map, HttpServletRequest request) {		
		prepareMap(map, request);
		
		map.put("apps", appServiceImpl.getApps());
		map.put("appUsers", shinyAppUserServiceImpl.getAppUsers());
		
		return "shinyapplistuser";
	}
    
    @RequestMapping(value="/shinyapp/listgroup", method=RequestMethod.GET)
	String listShinyAppGroup(ModelMap map, HttpServletRequest request) {		
		prepareMap(map, request);
		
		map.put("apps", appServiceImpl.getApps());
		map.put("appGroups", shinyAppGroupServiceImpl.getAppGroups());
		
		return "shinyapplistgroup";
	}
}