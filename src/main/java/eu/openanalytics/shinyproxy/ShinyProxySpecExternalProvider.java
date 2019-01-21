/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2018 Open Analytics
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
package eu.openanalytics.shinyproxy;

import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.shinyproxy.ShinyProxySpecMainProvider.ShinyProxySpec;

/**
 * This component converts proxy specs from the external specs path.
 */
@Component
public class ShinyProxySpecExternalProvider {

	protected final Logger log = LogManager.getLogger(getClass());

	@Inject
	private Environment environment;

	private String specsExternalPath = null;

	@PostConstruct
	public void init() {
		specsExternalPath = environment.getProperty("proxy.specs-external-path");
	}

	public List<ProxySpec> getSpecs() {
		// No setting, we leave with empty collection
		if (specsExternalPath == null) {
			return Collections.emptyList();
		}

		final File appSettingsFolder = new File(specsExternalPath);
		if (appSettingsFolder.exists() == false) {
			return Collections.emptyList();
		}

		// We load the settings file
		final File[] allSettingsFiles = appSettingsFolder.listFiles(new FileFilter() {

			@Override
			public boolean accept(File pathname) {
				return pathname.getName().endsWith("yml") || pathname.getName().endsWith("yaml");
			}
		});

		// For each file, we create a new ProxySpec
		final List<ProxySpec> allExternalSpecs = new ArrayList<>();
		for (int i = 0; i < allSettingsFiles.length; i++) {
			try {
				allExternalSpecs.add(loadYamlFile(allSettingsFiles[i]));
			} catch (Exception e) {
				log.error("An error occured while trying to open " + allSettingsFiles[i].getName() + " "
						+ e.getMessage());
			}
		}

		return allExternalSpecs;
	}

	/**
	 * Map the yaml parameter with the shiny app object
	 * 
	 * @param inputFile
	 * @return
	 * @throws IOException
	 */
	private ProxySpec loadYamlFile(final File inputFile) throws IOException {
		try (final FileReader fileReader = new FileReader(inputFile)) {
			final Yaml yaml = new Yaml(new Constructor(ShinyProxySpec.class));
			final ShinyProxySpec shinyProxySpec = yaml.load(fileReader);
			log.debug(" Id " + shinyProxySpec.getId());
			log.debug(" DisplayName " + shinyProxySpec.getDisplayName());
			log.debug(" Description " + shinyProxySpec.getDescription());
			log.debug(" LogoURL " + shinyProxySpec.getLogoURL());
			// We reuse the Main provider converter
			return ShinyProxySpecMainProvider.convert(shinyProxySpec);
		}
	}
}
