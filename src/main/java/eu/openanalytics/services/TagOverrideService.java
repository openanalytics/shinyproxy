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
package eu.openanalytics.services;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class TagOverrideService {

	private Logger log = Logger.getLogger(TagOverrideService.class);

	private String secretFile;

	@Inject
	SecretService secretService;

	@Inject
	Environment environment;

	public int getMaxTagOverrideExpirationDays() {
		return Integer.parseInt(environment.getProperty("shiny.proxy.tag-overriding.max-expiration-days", "7"));
	}

	public int getMinSigLen() {
		return Integer.parseInt(environment.getProperty("shiny.proxy.tag-overriding.minimum-signature-bytes", "16"));
	}

	public int getDefaultSigLen() {
		return Integer.parseInt(environment.getProperty("shiny.proxy.tag-overriding.default-signature-bytes", "16"));
	}

	public int getDefaultTagOverrideExpirationDays() {
		int defaultDays = Integer.parseInt(environment.getProperty("shiny.proxy.tag-overriding.default-expiration-days", "7"));
		int maxDays = getMaxTagOverrideExpirationDays();
		if (maxDays <= 0) {
			return defaultDays;
		} else {
			return Math.min(maxDays, defaultDays);
		}
	}

	public byte[] getSecret() {
		return secretService.getSecret(secretFile);
	}

	@PostConstruct
	private void initSecret() {
		if (environment.getProperty("shiny.proxy.tag-overriding.enabled", "").equals("")) {
			log.info("Tag overriding disabled");
			return;
		}
		log.info("Tag overriding enabled");
		secretFile = environment.getProperty("shiny.proxy.tag-overriding.secret-file", "tagOverrideSecret.bin");
		secretService.warmupCache(secretFile);
	}

}