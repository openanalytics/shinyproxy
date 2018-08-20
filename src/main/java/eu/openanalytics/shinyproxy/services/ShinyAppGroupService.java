package eu.openanalytics.shinyproxy.services;

import eu.openanalytics.shinyproxy.entity.AppGroup;
import eu.openanalytics.shinyproxy.entity.AppUser;

import java.util.List;

public abstract interface ShinyAppGroupService {
	
	public abstract List<AppGroup> getAppGroups();

	public abstract AppGroup getAppGroup(String id);

	public abstract AppGroup saveAppGroup(AppGroup appGroup);

	public abstract void deleteAppGroup(String id);

	public abstract Boolean isExistsById(String id);
}