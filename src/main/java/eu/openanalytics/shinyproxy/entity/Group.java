package eu.openanalytics.shinyproxy.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import java.io.Serializable;
import java.util.List;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToMany;



@Entity
public class Group
  implements Serializable
{
  @Id
  private String id;
  @Column
  private String name;
  @ManyToMany(mappedBy="apps", cascade={javax.persistence.CascadeType.ALL})
  @JsonBackReference
  private List<App> apps;
  
  public Group() {}
  
  public String getId()
  {
    return id;
  }
  
  public void setId(String id) { this.id = id; }
  
  public String getName() {
    return name;
  }
  
  public void setName(String name) { this.name = name; }
  
  public List<App> getApps() {
    return apps;
  }
  
  public void setApps(List<App> apps) { this.apps = apps; }
}