package eu.openanalytics.shinyproxy.controllers;

import eu.openanalytics.shinyproxy.entity.Group;
import eu.openanalytics.shinyproxy.services.GroupService;
import java.io.PrintStream;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class GroupController extends BaseController
{
  private static Logger logger = Logger.getLogger(GroupController.class);
  
  public GroupController()
  {
    logger.info("GroupController initialized");
    System.out.println("GroupController initialized");
  }
  
  @RequestMapping(value={"/group/{name}/"}, method={org.springframework.web.bind.annotation.RequestMethod.GET})
  @ResponseBody
  public List<Group> groupssList(@PathVariable("name") String name) {
    if (name != null) {
      return groupServiceImpl.getGroupsByName(name);
    }
    return groupServiceImpl.getGroups();
  }
  
  @RequestMapping(value={"/group/{name}/adduser"}, method={org.springframework.web.bind.annotation.RequestMethod.GET})
  @ResponseBody
  public List<Group> groupAddUser(@PathVariable("name") String name) {    
    return groupServiceImpl.getGroups();
  }
  
  @RequestMapping(value={"/group/{name}/adduser"}, method={org.springframework.web.bind.annotation.RequestMethod.POST})
  @ResponseBody
  public List<Group> groupSaveUser(@PathVariable("name") String name) {    
    return groupServiceImpl.getGroups();
  }

  
  @RequestMapping(value={"/"}, method={org.springframework.web.bind.annotation.RequestMethod.POST})
  @ResponseBody
  public Group groupsCreate(@RequestBody Group group)
  {
    return groupServiceImpl.saveGroup(group);
  }
  
  @RequestMapping(value={"/group"}, method={org.springframework.web.bind.annotation.RequestMethod.GET})
  String groupAll(ModelMap map, HttpServletRequest request) {
	  prepareMap(map, request);
	  map.put("groups", groupServiceImpl.getGroups());
	  return "group";
  }
  
  @RequestMapping(value={"/{id}/"}, method={org.springframework.web.bind.annotation.RequestMethod.GET})
  @ResponseBody
  public Group groupDetail(@PathVariable("id") String id) {
    logger.info("Getting Group: " + id);
    return groupServiceImpl.getGroup(id);
  }
  
  @RequestMapping(value={"/{id}/"}, method={org.springframework.web.bind.annotation.RequestMethod.DELETE})
  @ResponseBody
  public void groupDelete(@PathVariable("id") String id)
  {
    logger.info("Deleteing Group: " + id);
    try {
    	groupServiceImpl.deleteGroup(id);
    }
    catch (Exception ex) {
      logger.warn("Unable to delete Group: " + id + " with error:" + ex.toString());
    }
  }
}