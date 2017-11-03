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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class TagOverrideService {

	private Logger log = Logger.getLogger(TagOverrideService.class);

	private KeyPair keyPair;

	@Inject
	Environment environment;

	public int getTagOverrideExpirationDays() {
		return Integer.parseInt(environment.getProperty("shiny.proxy.tag-overriding.expiration-days", "7"));
	}

	public KeyPair getKeyPair() {
		return keyPair;
	}

	@PostConstruct
	private void initKeyPair() {
		if (environment.getProperty("shiny.proxy.tag-overriding.enabled", "").equals("")) {
			log.info("Tag overriding disabled");
			return;
		}
		log.info("Tag overriding enabled");
		File keyFileFile = new File(environment.getProperty("shiny.proxy.tag-overriding.key-file", "tagOverrideKey.ser"));
		if (keyFileFile.exists()) {
			try (FileInputStream fileStream = new FileInputStream(keyFileFile)) {
				try (ObjectInputStream objectStream = new ObjectInputStream(fileStream)) {
					keyPair = (KeyPair) objectStream.readObject();
					return;
				}
			} catch (Exception e) {
				log.error("Failed to read override key file", e);
			}
		}
		try {
			SecureRandom rng = SecureRandom.getInstanceStrong();
			KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DSA");
			log.info("Generating tag override key pair. This may take a while...");
			keyGen.initialize(1024, rng);
			keyPair = keyGen.generateKeyPair();
		} catch (Exception e) {
			log.error("Failed to generate override key pair", e);
			return;
		}
		try (FileOutputStream fileStream = new FileOutputStream(keyFileFile)) {
			try (ObjectOutputStream objectStream = new ObjectOutputStream(fileStream)) {
				objectStream.writeObject(keyPair);
			}
		} catch (Exception e) {
			log.error("Failed to write override key file", e);
		}
	}

}