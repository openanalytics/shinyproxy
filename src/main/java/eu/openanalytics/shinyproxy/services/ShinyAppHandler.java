package eu.openanalytics.shinyproxy.services;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import eu.openanalytics.shinyproxy.entity.App;
import eu.openanalytics.shinyproxy.entity.AppGroup;
import eu.openanalytics.shinyproxy.entity.AppUser;
import eu.openanalytics.shinyproxy.entity.Group;
import eu.openanalytics.shinyproxy.entity.User;
import java.sql.ResultSet;

public class ShinyAppHandler extends BeanListHandler<App> {

	private Connection connection;

	public ShinyAppHandler(Connection con) {
		super(App.class);
		this.connection = con;
	}

	@Override
	public List<App> handle(ResultSet rs) throws SQLException {
		List<App> apps = super.handle(rs);

		QueryRunner runner = new QueryRunner();
		BeanListHandler<AppGroup> handlerGroup = new BeanListHandler<>(AppGroup.class);
		BeanListHandler<AppUser> handlerUser = new BeanListHandler<>(AppUser.class);
		String queryGroup = "SELECT ID, GROUPNAME FROM APPGROUPS a WHERE APPID = ?";
		String queryUser = "SELECT ID, USERNAME FROM APPUSERS WHERE APPID = ?";

		for (App app : apps) {
			List<AppGroup> groups = runner.query(connection, queryGroup, handlerGroup, app.getId());
			app.setGroups(groups);
			
			List<AppUser> users = runner.query(connection, queryUser, handlerUser, app.getId());
			app.setUsers(users);
		}
		return apps;
	}
}