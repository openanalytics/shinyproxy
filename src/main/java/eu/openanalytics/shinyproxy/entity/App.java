package eu.openanalytics.shinyproxy.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import java.io.Serializable;
import java.util.List;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToMany;



@Entity
public class App
  implements Serializable
{
  @Id
  private String id;
  @Column
  private String name;
  @Column
  private String displayName;
  @Column
  private String logoUrl;
  @Column
  private String descr;
  @ManyToMany(mappedBy="groups", cascade={javax.persistence.CascadeType.ALL})
  @JsonBackReference
  private List<Group> groups;
  
  public App() {}
  
  public String getId()
  {
    return id;
  }
  
  public void setId(String id) { this.id = id; }
  
  public String getName() {
    return name;
  }
  
  public void setName(String name) { this.name = name; }
  
  public String getDisplayName() {
    return displayName;
  }
  
  public void setDisplayName(String displayName) { this.displayName = displayName; }
  
  public String getLogoUrl() {
    return logoUrl;
  }
  
  public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
  
  public String getDescr() {
    return descr;
  }
  
  public void setDescr(String descr) { this.descr = descr; }
  
  public List<Group> getGroups() {
    return groups;
  }
  
  public void setGroups(List<Group> groups) { this.groups = groups; }
}