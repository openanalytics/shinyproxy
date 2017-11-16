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

import com.google.common.collect.Sets;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Service;

@Service
// TODO: (US) Secret Service joke
public class SecretService {
	private Logger log = Logger.getLogger(TagOverrideService.class);

    // value is null: means never been initialized
    // value is optional, but optional is "null": means initialization failed
    private Map<String, Optional<byte[]>> secrets = new HashMap<>();

    private byte[] getSecretWithoutCache(String filename) {
        File secretFile = new File(filename);
        if (secretFile.exists()) {
            try (FileInputStream fileStream = new FileInputStream(secretFile)) {
                return Files.readAllBytes(secretFile.toPath());
            } catch (Exception e) {
                log.error("Failed to read secret, though it exists", e);
            }
        }
        byte[] secret;
        try {
            SecureRandom rng = SecureRandom.getInstance("SHA1PRNG");
            secret = new byte[2048];
            rng.nextBytes(secret);
        } catch (Exception e) {
            log.error("Failed to generate secret", e);
            return null;
        }
        try (FileOutputStream fileStream = new FileOutputStream(secretFile)) {
            Files.setPosixFilePermissions(secretFile.toPath(), Sets.newHashSet(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            fileStream.write(secret);
        } catch (Exception e) {
            log.error("Failed to write secret (or set permissions)", e);
        }
        return secret;
    }

    public byte[] getSecret(String filename) {
        synchronized(secrets) {
            Optional<byte[]> secret = secrets.get(filename);
            if (secret != null) {
                return secret.orElse(null);
            }
            byte[] newSecret = getSecretWithoutCache(filename);
            secrets.put(filename, Optional.ofNullable(newSecret));
            return newSecret;
        }
    }

    public Thread warmupCache(String filename) {
        Thread thread = new Thread(() -> getSecret(filename));
        thread.start();
        return thread;
    }

}