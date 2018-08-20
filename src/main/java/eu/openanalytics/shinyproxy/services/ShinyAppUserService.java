package eu.openanalytics.shinyproxy.services;

import eu.openanalytics.shinyproxy.entity.App;
import eu.openanalytics.shinyproxy.entity.AppUser;

import java.util.List;

public abstract interface ShinyAppUserService {
	
	public abstract List<AppUser> getAppUsers();
	
	public abstract List<AppUser> getAppUsersByUserName(String username);

	public abstract AppUser getAppUser(String id);

	public abstract AppUser saveAppUser(AppUser appUser);

	public abstract void deleteAppUser(String id);

	public abstract Boolean isExistsById(String id);
}