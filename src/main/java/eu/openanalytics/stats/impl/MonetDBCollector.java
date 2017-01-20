package eu.openanalytics.stats.impl;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

import org.springframework.core.env.Environment;

import eu.openanalytics.services.EventService.Event;
import eu.openanalytics.stats.IStatCollector;

/**
 * E.g.:
 * usage-stats-url: jdbc:monetdb://localhost:50000/usage_stats
 * 
 * Assumed table layout:
 * 
 * create table event(
 *  event_time timestamp,
 *  username varchar(128),
 *  type varchar(128),
 *  data text
 * );
 * 
 */
public class MonetDBCollector implements IStatCollector {

	@Override
	public void accept(Event event, Environment env) throws IOException {
		String username = env.getProperty("shiny.proxy.usage-stats-username", "monetdb");
		String password = env.getProperty("shiny.proxy.usage-stats-password", "monetdb");
		String baseURL = env.getProperty("shiny.proxy.usage-stats-url");
		try (Connection conn = DriverManager.getConnection(baseURL, username, password)) {
			String sql = "INSERT INTO event(event_time, username, type, data) VALUES (?,?,?,?)";
			try (PreparedStatement stmt = conn.prepareStatement(sql)) {
				stmt.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
				stmt.setString(2, event.user);
				stmt.setString(3, event.type);
				stmt.setString(4, event.data);
				stmt.executeUpdate();
			}
		} catch (SQLException e) {
			throw new IOException("Failed to connect to " + baseURL, e);
		}
	}
}
