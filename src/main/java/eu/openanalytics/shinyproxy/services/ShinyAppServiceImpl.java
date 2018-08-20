package eu.openanalytics.shinyproxy.services;

import eu.openanalytics.shinyproxy.entity.App;
import eu.openanalytics.shinyproxy.util.Utils;

import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import org.apache.commons.dbutils.DbUtils;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.BeanHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.springframework.stereotype.Service;

@Service
public class ShinyAppServiceImpl implements ShinyAppService {

	private Connection connection = null;

	public ShinyAppServiceImpl() {
	}

	private void Connect() {
		try {
			if (connection == null) {
				try {
					Class.forName("oracle.jdbc.driver.OracleDriver");
				} catch (ClassNotFoundException e) {
					System.out.println("Where is your Oracle JDBC Driver?");
					e.printStackTrace();
					return;
				}
				try {
					connection = DriverManager.getConnection("jdbc:oracle:thin:@localhost:1521:xe", "shiny", "shavar");
				} catch (SQLException e) {
					System.out.println("Connection Failed! Check output console");
					e.printStackTrace();
					return;
				}
			} else if (connection.isClosed()) {
				try {
					connection = DriverManager.getConnection("jdbc:oracle:thin:@localhost:1521:xe", "shiny", "shavar");
				} catch (SQLException e) {
					System.out.println("Connection Failed! Check output console");
					e.printStackTrace();
					return;
				}
			}

			if (connection == null) {
				System.out.println("Failed to make connection!");
			}
		} catch (Exception ex) {
			System.out.println("Failed to make connection!");
			ex.printStackTrace();
		}
	}

	public List<App> getApps() {
		Connect();
		QueryRunner run = new QueryRunner();

		ShinyAppHandler h = new ShinyAppHandler(connection);

		try {
			return run.query(connection, "SELECT ID, NAME, DESCR, DISPLAYNAME, LOGOURL, MAPPING FROM APPS", h);
		} catch (Exception ex) {
			System.out.println(ex.toString());
		} finally {
			try {
				DbUtils.close(connection);
			} catch (Exception localException3) {
			}
		}
		return null;
	}

	public App getApp(String id) {		
		QueryRunner run = new QueryRunner();
		
		try {
			connection = Utils.connect();

			ShinyAppHandler h = new ShinyAppHandler(connection);
		
			List<App> apps = run.query(connection,
					"SELECT ID, NAME, DESCR, DISPLAYNAME, LOGOURL, MAPPING FROM APPS WHERE ID = ?", h, id);
			if (apps.size() > 0)
				return apps.get(0);
		} catch (Exception ex) {
			System.out.println(ex.toString());
		} finally {
			try {
				DbUtils.close(connection);
			} catch (Exception localException3) {
			}
		}
		return null;
	}

	public App getAppByName(String appName) {

		QueryRunner run = new QueryRunner();

		try {
			connection = Utils.connect();

			ShinyAppHandler h = new ShinyAppHandler(connection);

			List<App> apps = run.query(connection,
					"SELECT ID, NAME, DESCR, DISPLAYNAME, LOGOURL, MAPPING FROM APPS WHERE UPPER(NAME) = ?", h,
					appName.toUpperCase());
			if (apps.size() > 0)
				return apps.get(0);
		} catch (Exception ex) {
			System.out.println(ex.toString());
		} finally {
			try {
				DbUtils.close(connection);
			} catch (Exception localException3) {
			}
		}
		return null;
	}

	public App saveApp(App app) {
		Connect();
		QueryRunner run = new QueryRunner();

		ResultSetHandler<App> h = new BeanHandler<App>(App.class);

		try {
			return run.insert(connection,
					"INSERT INTO APPS (ID, NAME, DESCR, DISPLAYNAME, LOGOURL, MAPPING) VALUES (:id, :name, :descr, :displayName, :logoUrl, :mapping)",
					h, app.getId(), app.getName(), app.getDescr(), app.getDisplayName(), app.getLogoUrl(),
					app.getMapping());
		} catch (Exception ex) {
			System.out.println(ex.toString());
		} finally {
			try {
				DbUtils.close(connection);
			} catch (Exception localException3) {
			}
		}
		return null;
	}

	public void deleteApp(String id) {
	}

	public Boolean isExistsById(String id) {
		return null;
	}
}