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

import eu.openanalytics.containerproxy.test.helpers.ShinyProxyInstance;
import eu.openanalytics.shinyproxy.test.helpers.ApiTestHelper;
import eu.openanalytics.shinyproxy.test.helpers.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

public class DelegateProxyAdminControllerTest {

    private static final ShinyProxyInstance inst = new ShinyProxyInstance("application-test-delegate.yml");
    private static final ApiTestHelper apiTestHelper = new ApiTestHelper(inst);
    private static final String RANDOM_UUID = "8402e8c3-eaef-4fc7-9f23-9e843739dd0f";

    @AfterAll
    public static void afterAll() {
        inst.close();
    }

    @Test
    public void testWithoutAuth() {
        Response resp = apiTestHelper.callWithoutAuth(apiTestHelper.createDeleteRequest("/admin/delegate-proxy"));
        resp.assertHtmlAuthenticationRequired();

        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createDeleteRequest("/admin/delegate-proxy?id=" + RANDOM_UUID));
        resp.assertHtmlAuthenticationRequired();

        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createDeleteRequest("/admin/delegate-proxy?specId=01_hello"));
        resp.assertHtmlAuthenticationRequired();

        // get does nothing for now
        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createRequest("/admin/delegate-proxy"));
        resp.assertHtmlAuthenticationRequired(); // TODO
    }

    @Test
    public void testNonAdminUser() {
        Response resp = apiTestHelper.callWithAuthDemo2(apiTestHelper.createDeleteRequest("/admin/delegate-proxy"));
        resp.assertForbidden();

        resp = apiTestHelper.callWithAuthDemo2(apiTestHelper.createDeleteRequest("/admin/delegate-proxy?id=" + RANDOM_UUID));
        resp.assertForbidden();

        resp = apiTestHelper.callWithAuthDemo2(apiTestHelper.createDeleteRequest("/admin/delegate-proxy?specId=01_hello"));
        resp.assertForbidden();

        // get does nothing for now
        resp = apiTestHelper.callWithAuthDemo2(apiTestHelper.createRequest("/admin/delegate-proxy"));
        resp.assertForbidden(); // TODO
    }

    @Test
    public void testAdminUser() {
        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createDeleteRequest("/admin/delegate-proxy"));
        resp.jsonSuccess();
    }

}
