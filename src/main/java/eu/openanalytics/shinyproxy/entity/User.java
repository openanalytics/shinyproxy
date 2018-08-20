package eu.openanalytics.shinyproxy.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import java.io.Serializable;
import java.util.List;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToMany;

@Entity
public class User implements Serializable
{
  @Id
  private String id;
  @Column
  private String userName;
    
  @ManyToMany(mappedBy="groups", cascade={javax.persistence.CascadeType.ALL})
  @JsonBackReference
  private List<Group> groups;
  
  public User() {}
  
  public String getId()
  {
    return id;
  }
  
  public void setId(String id) { this.id = id; }
  
  public String getUserName() {
    return userName;
  }
  
  public void setUserName(String userName) { this.userName = userName; }
 
  public List<Group> getGroups() {
    return groups;
  }
  
  public void setGroups(List<Group> groups) { this.groups = groups; }
}