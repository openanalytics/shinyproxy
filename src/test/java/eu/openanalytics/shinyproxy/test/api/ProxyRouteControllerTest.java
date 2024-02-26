/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2024 Open Analytics
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

import eu.openanalytics.containerproxy.api.ProxyController;
import eu.openanalytics.containerproxy.test.helpers.ShinyProxyInstance;
import eu.openanalytics.shinyproxy.test.helpers.ApiTestHelper;
import eu.openanalytics.shinyproxy.test.helpers.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test the security (authorization of) {@link ProxyController}
 */
public class ProxyRouteControllerTest {

    private static final String RANDOM_UUID = "8402e8c3-eaef-4fc7-9f23-9e843739dd0f";

    private static final ShinyProxyInstance inst = new ShinyProxyInstance("application-test-api.yml");
    private static final ApiTestHelper apiTestHelper = new ApiTestHelper(inst);

    @AfterAll
    public static void afterAll() {
        inst.close();
    }

    @Test
    public void testWithoutAuth() {
        // invalid app id
        Response resp = apiTestHelper.callWithoutAuth(apiTestHelper.createRequest("/api/route/" + RANDOM_UUID + "/"));
        resp.assertAuthenticationRequired();
        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createRequest("/api/route/" + RANDOM_UUID + "/test"));
        resp.assertAuthenticationRequired();


        // valid app id, but not authenticated
        String id = inst.client.startProxy("01_hello");
        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createRequest("/api/route/" + id + "/"));
        resp.assertAuthenticationRequired();
        inst.client.stopProxy(id);
    }

    @Test
    public void testWithAndInvalidId() {
        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/api/route/" + RANDOM_UUID + "/"));
        Assertions.assertEquals(403, resp.code());
        Assertions.assertEquals("Not authorized to access this proxy", resp.body());

        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/api/route/weird_id/test"));
        Assertions.assertEquals(403, resp.code());
        Assertions.assertEquals("Not authorized to access this proxy", resp.body());

        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/api/route/very_long_id" + RANDOM_UUID + "/test"));
        Assertions.assertEquals(403, resp.code());
        Assertions.assertEquals("Not authorized to access this proxy", resp.body());
    }

    @Test
    public void testWithApp() {
        String id = inst.client.startProxy("01_hello");
        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/api/route/" + id + "/"));
        Assertions.assertEquals(200, resp.code());
        Assertions.assertNotNull(resp.body());

        // slash at the end is required
        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/api/route/" + id));
        Assertions.assertEquals(403, resp.code());
        Assertions.assertEquals("Not authorized to access this proxy", resp.body());
    }

}
