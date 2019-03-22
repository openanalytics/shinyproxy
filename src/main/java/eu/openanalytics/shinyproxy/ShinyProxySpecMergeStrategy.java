/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2019 Open Analytics
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

import java.util.Set;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import eu.openanalytics.containerproxy.model.runtime.RuntimeSetting;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.spec.IProxySpecMergeStrategy;
import eu.openanalytics.containerproxy.spec.ProxySpecException;

@Component
@Primary
public class ShinyProxySpecMergeStrategy implements IProxySpecMergeStrategy {

	@Override
	public ProxySpec merge(ProxySpec baseSpec, ProxySpec runtimeSpec, Set<RuntimeSetting> runtimeSettings) throws ProxySpecException {
		if (baseSpec == null) throw new ProxySpecException("Base proxy spec is required but missing");
		if (runtimeSpec != null) throw new ProxySpecException("Runtime proxy specs are not allowed");
		if (runtimeSettings != null && !runtimeSettings.isEmpty()) throw new ProxySpecException("Runtime proxy settings are not allowed");
		
		ProxySpec finalSpec = new ProxySpec();
		baseSpec.copy(finalSpec);
		return finalSpec;
	}

}
