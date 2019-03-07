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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;

/**
 * This component aggregate the proxy specs from the Main Provider (mainning the
 * application.yml file) and the others settings files as definied in the
 * proxy.specs-external-path properties.
 */
@Component
@Primary
public class ShinyProxySpecProvider implements IProxySpecProvider {

	protected final Logger log = LogManager.getLogger(getClass());
	
	@Inject
	private ShinyProxySpecMainProvider mainProvider;

	@Inject
	private ShinyProxySpecExternalProvider externalProvider;

	public List<ProxySpec> getSpecs() {
		final List<ProxySpec> allProxySpec = new ArrayList<>(mainProvider.getSpecs());
		allProxySpec.addAll(externalProvider.getSpecs());
		Collections.sort(allProxySpec, PROXY_SPEC_COMPARATOR);
		log.debug("We have found " + allProxySpec.size() + " spec(s)");
		return allProxySpec;
	}

	public ProxySpec getSpec(String id) {
		if (id == null || id.isEmpty())
			return null;
		return getSpecs().stream().filter(s -> id.equals(s.getId())).findAny().orElse(null);
	}

	// This comparator uses the display name to sort
	private static final Comparator<ProxySpec> PROXY_SPEC_COMPARATOR = new Comparator<ProxySpec>() {

		@Override
		public int compare(ProxySpec o1, ProxySpec o2) {
			if (o1 == o2)
				return 0;
			if (o1 == null)
				return -1;
			if (o2 == null)
				return 1;
			return StringUtils.compare(o1.getDisplayName(), o2.getDisplayName());
		}
	};

}
