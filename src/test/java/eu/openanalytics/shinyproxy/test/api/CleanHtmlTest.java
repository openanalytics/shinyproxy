/*
 * ShinyProxy
 *
 * Copyright (C) 2016-2026 Open Analytics
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
package eu.openanalytics.shinyproxy.test.api;

import eu.openanalytics.containerproxy.ContainerProxyApplication;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;
import eu.openanalytics.containerproxy.test.helpers.PropertyOverrideContextInitializer;
import eu.openanalytics.shinyproxy.ShinyProxySpecExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import jakarta.inject.Inject;

@SpringBootTest(classes = {ContainerProxyApplication.class})
@ExtendWith(SpringExtension.class)
@ActiveProfiles("test-clean-html")
@ContextConfiguration(initializers = PropertyOverrideContextInitializer.class)
public class CleanHtmlTest {

    @Inject
    private IProxySpecProvider proxySpecProvider;

    @Test
    public void testComputerTargetPath() {
        ProxySpec spec = proxySpecProvider.getSpec("01_hello");
        Assertions.assertEquals("<b>Test!</b>", spec.getDescription());
        Assertions.assertEquals("<b>MyParameter!</b><a rel=\"nofollow\"></a>", spec.getParameters().getDefinitions().get(0).getDescription());
        Assertions.assertEquals("CustomAppDetailsDescription<a rel=\"nofollow\">link</a>", spec.getSpecExtension(ShinyProxySpecExtension.class).getCustomAppDetails().get(0).getDescription());
    }

}
