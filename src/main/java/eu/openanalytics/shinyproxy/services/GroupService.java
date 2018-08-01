package eu.openanalytics.shinyproxy.services;


import eu.openanalytics.shinyproxy.entity.Group;
import java.util.List;

public abstract interface GroupService
{
  public abstract List<Group> getGroups();
  
  public abstract Group getGroup(String paramString);
  
  public abstract List<Group> getGroupsByName(String paramString);
  
  public abstract Group saveGroup(Group paramGroup);
  
  public abstract void deleteGroup(String paramString);
  
  public abstract Boolean isExistsById(String paramString);
}