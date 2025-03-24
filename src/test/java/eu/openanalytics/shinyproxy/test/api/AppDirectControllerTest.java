/*
 * ShinyProxy
 *
 * Copyright (C) 2016-2025 Open Analytics
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AppDirectControllerTest {

    private static final ShinyProxyInstance inst = new ShinyProxyInstance("application-test-api.yml");
    private static final ApiTestHelper apiTestHelper = new ApiTestHelper(inst);

    @AfterAll
    public static void afterAll() {
        inst.close();
    }

    @AfterEach
    public void afterEach() {
        inst.stopAllApps();
    }

    @Test
    public void testWithoutAuth() {
        Response resp = apiTestHelper.callWithoutAuth(apiTestHelper.createRequest("/app_direct/01_hello/"));
        resp.assertHtmlAuthenticationRequired();

        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createRequest("/app_direct/01_hello/test/"));
        resp.assertHtmlAuthenticationRequired();

        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createRequest("/app_direct_i/01_hello/_/"));
        resp.assertHtmlAuthenticationRequired();

        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createRequest("/app_direct_i/01_hello/_/test"));
        resp.assertHtmlAuthenticationRequired();
    }

    @Test
    public void testNonExistingSpec() {
        // spec does not exist
        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app_direct/non-existing-spec/"));
        resp.assertForbidden();

        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app_direct/non-existing-spec/test/"));
        resp.assertForbidden();

        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app_direct_i/non-existing-spec/_/"));
        resp.assertForbidden();

        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app_direct_i/non-existing-spec/_/test"));
        resp.assertForbidden();

        // invalid instance name
        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app_direct_i/01_hello/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/test"));
        resp.assertForbidden();
    }

    @Test
    public void testNoAccess() {
        // spec exists, but no access
        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app_direct/nobody/"));
        resp.assertForbidden();

        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app_direct/nobody/test/"));
        resp.assertForbidden();

        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app_direct_i/nobody/_/"));
        resp.assertForbidden();

        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app_direct_i/nobody/_/test"));
        resp.assertForbidden();
    }

    @Test
    public void testStartApp() {
        // start app
        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app_direct/01_hello/"));
        resp.assertHtmlSuccess();
        Assertions.assertTrue(resp.body().contains("Welcome to nginx!"));
        Assertions.assertFalse(resp.body().contains("js/shiny.iframe.js"));

        // make request
        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app_direct/01_hello/"));
        resp.assertHtmlSuccess();
        Assertions.assertTrue(resp.body().contains("Welcome to nginx!"));
        Assertions.assertFalse(resp.body().contains("js/shiny.iframe.js"));

        // make sub-path request
        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app_direct/01_hello/test"));
        Assertions.assertEquals(404, resp.code());
        Assertions.assertTrue(resp.body().contains("404 Not Found"));
        Assertions.assertFalse(resp.body().contains("js/shiny.iframe.js"));
    }

    @Test
    public void testStartAppInstance() {
        // start app
        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app_direct_i/01_hello/myInstance/"));
        resp.assertHtmlSuccess();
        Assertions.assertTrue(resp.body().contains("Welcome to nginx!"));
        Assertions.assertFalse(resp.body().contains("js/shiny.iframe.js"));

        // make request
        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app_direct_i/01_hello/myInstance/"));
        resp.assertHtmlSuccess();
        Assertions.assertTrue(resp.body().contains("Welcome to nginx!"));
        Assertions.assertFalse(resp.body().contains("js/shiny.iframe.js"));

        // make sub-path request
        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app_direct_i/01_hello/myInstance/tests"));
        Assertions.assertEquals(404, resp.code());
        Assertions.assertTrue(resp.body().contains("404 Not Found"));
        Assertions.assertFalse(resp.body().contains("js/shiny.iframe.js"));
    }

    @Test
    public void testRedirect() {
        // no slash, should redirect to url with slash
        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app_direct/01_hello"));
        Assertions.assertEquals(302, resp.code());
        Assertions.assertEquals("http://localhost:7583/app_direct/01_hello/", resp.header("Location"));
    }

}
