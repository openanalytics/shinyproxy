/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2023 Open Analytics
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

import eu.openanalytics.containerproxy.model.spec.AbstractSpecExtension;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionContext;
import eu.openanalytics.containerproxy.spec.expression.SpecExpressionResolver;
import eu.openanalytics.containerproxy.spec.expression.SpelField;
import eu.openanalytics.shinyproxy.runtimevalues.WebsocketReconnectionMode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@Setter
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE) // force Spring to not use constructor
@NoArgsConstructor(force = true, access = AccessLevel.PRIVATE) // Jackson deserialize compatibility
public class ShinyProxySpecExtension extends AbstractSpecExtension {

    WebsocketReconnectionMode websocketReconnectionMode;

    Boolean shinyForceFullReload;

    @Builder.Default
    SpelField.Integer maxInstances = new SpelField.Integer();

    Boolean hideNavbarOnMainPageLink;

    Boolean alwaysShowSwitchInstance;

    Boolean trackAppUrl;

    String templateGroup;

    Map<String, String> templateProperties = new HashMap<>();

    @Override
    public ShinyProxySpecExtension firstResolve(SpecExpressionResolver resolver, SpecExpressionContext context) {
        return this;
    }

    @Override
    public ShinyProxySpecExtension finalResolve(SpecExpressionResolver resolver, SpecExpressionContext context) {
        return this;
    }

}
