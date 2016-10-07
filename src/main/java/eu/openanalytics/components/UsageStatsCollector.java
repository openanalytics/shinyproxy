package eu.openanalytics.components;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Optional;
import java.util.function.Consumer;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import eu.openanalytics.services.EventService;
import eu.openanalytics.services.EventService.Event;

@Component
public class UsageStatsCollector implements Consumer<Event> {
	
	private Logger log = Logger.getLogger(UsageStatsCollector.class);
	
	@Inject
	Environment environment;
	
	@Inject
	EventService eventService;
	
	private String baseURL;
	private String dbName;
	
	@PostConstruct
	public void init() {
		baseURL = environment.getProperty("shiny.proxy.usage-stats-url");
		dbName = environment.getProperty("shiny.proxy.usage-stats-db");
		if (baseURL == null || dbName == null) {
			log.info("Disabled. Usage statistics will not be posted.");
		} else {
			eventService.addListener(this);
			log.info(String.format("Enabled. Posting usage statistics to %s at %s", dbName, baseURL));
		}
	}
	
	@Override
	public void accept(Event event) {
		String destination = String.format("%s/write?db=%s", baseURL, dbName);
		String data = Optional.ofNullable(event.data).orElse("");
		String body = String.format("event,username=%s,type=%s data=\"%s\"", event.user, event.type, data);
		try {
			doPost(destination, body);
		} catch (IOException e) {
			log.error("Failed to submit usage statistic event", e);
		}
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
