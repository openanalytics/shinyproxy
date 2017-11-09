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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.google.common.collect.Sets;

@Service
public class TagOverrideService {

	private Logger log = Logger.getLogger(TagOverrideService.class);

	private byte[] secret;

	@Inject
	Environment environment;

	public int getMaxTagOverrideExpirationDays() {
		return Integer.parseInt(environment.getProperty("shiny.proxy.tag-overriding.max-expiration-days", "7"));
	}

	public int getMinSigLen() {
		return Integer.parseInt(environment.getProperty("shiny.proxy.tag-overriding.minimum-signature-bytes", "16"));
	}

	public int getURLSigLen() {
		return Integer.parseInt(environment.getProperty("shiny.proxy.tag-overriding.url-signature-bytes", "16"));
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
		return secret;
	}

	@PostConstruct
	private void initSecret() {
		if (environment.getProperty("shiny.proxy.tag-overriding.enabled", "").equals("")) {
			log.info("Tag overriding disabled");
			return;
		}
		log.info("Tag overriding enabled");
		File secretFile = new File(environment.getProperty("shiny.proxy.tag-overriding.secret-file", "tagOverride.secret"));
		if (secretFile.exists()) {
			try (FileInputStream fileStream = new FileInputStream(secretFile)) {
				secret = Files.readAllBytes(secretFile.toPath());
				return;
			} catch (Exception e) {
				log.error("Failed to read override key file", e);
			}
		}
		try {
			SecureRandom rng = SecureRandom.getInstance("SHA1PRNG");
			secret = new byte[2048];
			rng.nextBytes(secret);
		} catch (Exception e) {
			log.error("Failed to generate override key pair", e);
			return;
		}
		try (FileOutputStream fileStream = new FileOutputStream(secretFile)) {
			Files.setPosixFilePermissions(secretFile.toPath(), Sets.newHashSet(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
			fileStream.write(secret);
		} catch (Exception e) {
			log.error("Failed to write override key file", e);
		}
	}

}