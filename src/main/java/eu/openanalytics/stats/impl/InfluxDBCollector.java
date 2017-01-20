package eu.openanalytics.stats.impl;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.springframework.core.env.Environment;

import eu.openanalytics.services.EventService.Event;
import eu.openanalytics.stats.IStatCollector;

/**
 * E.g.:
 * usage-stats-url: http://localhost:8086/write?db=shinyproxy_usagestats
 */
public class InfluxDBCollector implements IStatCollector {

	@Override
	public void accept(Event event, Environment env) throws IOException {
		String destination = env.getProperty("shiny.proxy.usage-stats-url");
		String data = Optional.ofNullable(event.data).orElse("");
		String body = String.format("event,username=%s,type=%s data=\"%s\"", event.user, event.type, data);
		doPost(destination, body);
	}
	
	private void doPost(String url, String body) throws IOException {
		HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
		conn.setRequestMethod("POST");
		conn.setDoOutput(true);
		try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
			dos.writeBytes(body);
			dos.flush();
		}
		int responseCode = conn.getResponseCode();
		if (responseCode == 204) {
			// All is well.
		} else {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			IOUtils.copy(conn.getErrorStream(), bos);
			throw new IOException(new String(bos.toByteArray()));
		}
	}
}
