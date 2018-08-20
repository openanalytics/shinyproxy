package eu.openanalytics.shinyproxy.services;

import eu.openanalytics.shinyproxy.entity.AppUser;
import eu.openanalytics.shinyproxy.util.Utils;

import java.sql.Connection;
import java.util.List;
import org.apache.commons.dbutils.DbUtils;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.BeanHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.springframework.stereotype.Service;

@Service
public class ShinyAppUserServiceImpl implements ShinyAppUserService {

	private Connection connection = null;

	public ShinyAppUserServiceImpl() {

	}

	public List<AppUser> getAppUsers() {
		QueryRunner run = new QueryRunner();

		try {
			connection = Utils.connect();
			BeanListHandler<AppUser> h = new BeanListHandler<AppUser>(AppUser.class);

			return run.query(connection, "SELECT ID, APPID, USERNAME FROM APPUSERS", h);
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

	public AppUser getAppUser(String id) {
		
		QueryRunner run = new QueryRunner();

		try {
			connection = Utils.connect();
			BeanListHandler<AppUser> h = new BeanListHandler<AppUser>(AppUser.class);

			List<AppUser> appUsers = run.query(connection, "SELECT ID, APPID, USERNAME FROM APPUSERS", h);
			
			if (appUsers.size() > 0)
				return appUsers.get(0);
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

	public AppUser saveAppUser(AppUser appUser) {
		
		QueryRunner run = new QueryRunner();

		try {
			connection = Utils.connect();
			
			ResultSetHandler<AppUser> h = new BeanHandler<AppUser>(AppUser.class);

			return run.insert(connection, "INSERT INTO APPUSERS (APPID, USERNAME) VALUES (:appId, :userName)", h, appUser.getAppId(), appUser.getUserName(), null);
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

	public void deleteAppUser(String id) {
	}

	public Boolean isExistsById(String id) {
		return null;
	}

	@Override
	public List<AppUser> getAppUsersByUserName(String username) {
		QueryRunner run = new QueryRunner();
		Connection connection = null;

		try {
			connection = Utils.connect();
			BeanListHandler<AppUser> h = new BeanListHandler<AppUser>(AppUser.class);

			return run.query(connection, "SELECT ID, APPID, USERNAME FROM APPUSERS WHERE USERNAME = ?", h, username);
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
}