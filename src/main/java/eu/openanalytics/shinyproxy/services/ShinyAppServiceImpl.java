package eu.openanalytics.shinyproxy.services;

import eu.openanalytics.shinyproxy.entity.App;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import org.apache.commons.dbutils.DbUtils;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.springframework.stereotype.Service;


@Service
public class ShinyAppServiceImpl
  implements ShinyAppService
{
  private Connection connection = null;
  
  public ShinyAppServiceImpl() {}
  
  private void Connect() { try { if (connection == null) {
        try {
          Class.forName("oracle.jdbc.driver.OracleDriver");
        }
        catch (ClassNotFoundException e) {
          System.out.println("Where is your Oracle JDBC Driver?");
          e.printStackTrace();
          return;
        }
        try
        {
          connection = DriverManager.getConnection("jdbc:oracle:thin:@localhost:1521:xe", "shiny", "shavar");
        }
        catch (SQLException e) {
          System.out.println("Connection Failed! Check output console");
          e.printStackTrace();
          return;
        }
      }
      else if (connection.isClosed()) {
        try {
          connection = DriverManager.getConnection("jdbc:oracle:thin:@localhost:1521:xe", "shiny", "shavar");
        }
        catch (SQLException e) {
          System.out.println("Connection Failed! Check output console");
          e.printStackTrace();
          return;
        }
      }
      
      if (connection == null) {
        System.out.println("Failed to make connection!");
      }
    }
    catch (Exception ex) {
      System.out.println("Failed to make connection!");
      ex.printStackTrace();
    }
  }
  

  public List<App> getApps()
  {
    Connect();
    QueryRunner run = new QueryRunner();

    ResultSetHandler<List<App>> h = new BeanListHandler<App>(App.class);
    
    try {
      return run.query(connection, "SELECT ID, NAME, DESCR, DISPLAYNAME, LOGOURL FROM APPS", h);
    }
    catch (Exception ex) {
      System.out.println(ex.toString());
    }
    finally {
      try {
        DbUtils.close(connection);
      }
      catch (Exception localException3) {}
    }
    return null;
  }
  

  public App getApp(String id)
  {
    return null;
  }
  

  public App saveApp(App app)
  {
    return null;
  }
  


  public void deleteApp(String id) {}
  


  public Boolean isExistsById(String id)
  {
    return null;
  }
}