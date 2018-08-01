package eu.openanalytics.shinyproxy.services;

import eu.openanalytics.shinyproxy.entity.App;
import java.util.List;

	public abstract interface ShinyAppService
	{
	  public abstract List<App> getApps();
	  
	  public abstract App getApp(String paramString);
	  
	  public abstract App saveApp(App paramApp);
	  
	  public abstract void deleteApp(String paramString);
	  
	  public abstract Boolean isExistsById(String paramString);
	}