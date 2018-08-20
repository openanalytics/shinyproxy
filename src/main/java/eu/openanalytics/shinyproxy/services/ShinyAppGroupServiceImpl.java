package eu.openanalytics.shinyproxy.services;

import eu.openanalytics.shinyproxy.entity.AppGroup;
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
public class ShinyAppGroupServiceImpl implements ShinyAppGroupService {

	private Connection connection = null;

	public ShinyAppGroupServiceImpl() {

	}

	public List<AppGroup> getAppGroups() {
		QueryRunner run = new QueryRunner();

		try {
			connection = Utils.connect();
			BeanListHandler<AppGroup> h = new BeanListHandler<AppGroup>(AppGroup.class);

			return run.query(connection, "SELECT ID, APPID, GROUPNAME FROM APPGROUPS", h);
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

	public AppGroup getAppGroup(String id) {

		QueryRunner run = new QueryRunner();

		try {
			connection = Utils.connect();
			BeanListHandler<AppGroup> h = new BeanListHandler<AppGroup>(AppGroup.class);

			List<AppGroup> appGroups = run.query(connection, "SELECT ID, APPID, GROUPNAME FROM APPGROUPS", h);

			if (appGroups.size() > 0)
				return appGroups.get(0);
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

	public AppGroup saveAppGroup(AppGroup appGroup) {

		QueryRunner run = new QueryRunner();

		try {
			connection = Utils.connect();

			ResultSetHandler<AppGroup> h = new BeanHandler<AppGroup>(AppGroup.class);

			return run.insert(connection, "INSERT INTO APPGROUPS (APPID, GROUPNAME) VALUES (:appId, :groupName)", h,
					appGroup.getAppId(), appGroup.getGroupName(), null);
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

	public void deleteAppGroup(String id) {
	}

	public Boolean isExistsById(String id) {
		return null;
	}
}