/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2017 Open Analytics
 *
 * ===========================================================================
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
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
		String body = String.format("event,username=%s,type=%s data=\"%s\"", event.user.replace(" ", "\\ "), event.type.replace(" ", "\\ "), data);
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
