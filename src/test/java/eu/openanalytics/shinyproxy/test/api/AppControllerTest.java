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

public class AppControllerTest {
    private static final String RANDOM_UUID = "8402e8c3-eaef-4fc7-9f23-9e843739dd0f";

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
    public void testAppPageWithoutAuth() {
        Response resp = apiTestHelper.callWithoutAuth(apiTestHelper.createRequest("/app/01_hello/"));
        resp.assertHtmlAuthenticationRequired();

        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createRequest("/app/01_hello/test/"));
        resp.assertHtmlAuthenticationRequired();

        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createRequest("/app_i/01_hello/_/"));
        resp.assertHtmlAuthenticationRequired();

        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createRequest("/app_i/01_hello/_/test"));
        resp.assertHtmlAuthenticationRequired();
    }

    @Test
    public void testAppPageWithoutAccess() {
        // no access to spec
        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app/nobody/"));
        resp.assertForbidden();

        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app/nobody/test/"));
        resp.assertForbidden();

        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app_i/nobody/_/"));
        resp.assertForbidden();

        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app_i/nobody/_/test"));
        resp.assertForbidden();
    }

    @Test
    public void testAppPage() {
        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app/01_hello/"));
        resp.assertHtmlSuccess();
        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app/01_hello/xyz"));
        resp.assertHtmlSuccess();
        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app/spec-doest-not-exists"));
        resp.assertForbidden();
        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app/spec-doest-not-exists/my_path"));
        resp.assertForbidden();
    }

    @Test
    public void testStartAppWithoutAuth() {
        Response resp = apiTestHelper.callWithoutAuth(apiTestHelper.createPostRequest("/app_i/01_hello/_/"));
        resp.assertHtmlAuthenticationRequired();

        // invalid url
        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createPostRequest("/app_i/01_hello/_/test"));
        resp.assertHtmlAuthenticationRequired();
    }

    @Test
    public void testStartAppWithoutAccess() {
        // no access to spec
        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createPostRequest("/app_i/nobody/_/"));
        resp.assertForbidden();

        // invalid url
        resp = apiTestHelper.callWithAuth(apiTestHelper.createPostRequest("/app_i/nobody/_/test"));
        resp.assertForbidden();
    }

    @Test
    public void testStartApp() {
        // start app
        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createPostRequest("/app_i/01_hello/_/"));
        String id = apiTestHelper.validateProxyObject(resp.jsonSuccess().asJsonObject());

        // load app page
        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app/01_hello/"));
        resp.assertHtmlSuccess();
        // page should container app id
        Assertions.assertTrue(resp.body().contains("\"" + id + "\""));

        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app_i/01_hello/_/"));
        resp.assertHtmlSuccess();
        // page should container app id
        Assertions.assertTrue(resp.body().contains("\"" + id + "\""));

        // load app page with sub-path
        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app/01_hello/my-path"));
        resp.assertHtmlSuccess();
        // page should container app id
        Assertions.assertTrue(resp.body().contains("\"" + id + "\""));

        // load app page with sub-path
        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app_i/01_hello/_/my-path"));
        resp.assertHtmlSuccess();
        // page should container app id
        Assertions.assertTrue(resp.body().contains("\"" + id + "\""));

        // try to start app again
        resp = apiTestHelper.callWithAuth(apiTestHelper.createPostRequest("/app_i/01_hello/_/"));
        resp.assertFail("You already have an instance of this app with the given name");
    }

    @Test
    public void testProxyWithoutAuth() {
        Response resp = apiTestHelper.callWithoutAuth(apiTestHelper.createRequest("/app_proxy/" + RANDOM_UUID + "/"));
        resp.assertAuthenticationRequired();

        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createRequest("/app_proxy/" + RANDOM_UUID + "/xyz/test/"));
        resp.assertAuthenticationRequired();
    }

    @Test
    public void testProxyWithoutAccess() {
        String id = inst.client.startProxy("01_hello");
        Response resp = apiTestHelper.callWithAuthDemo2(apiTestHelper.createRequest("/app_proxy/" + id + "/"));
        resp.assertAppStoppedOrNonExistent();

        resp = apiTestHelper.callWithAuthDemo2(apiTestHelper.createRequest("/app_proxy/" + id + "/xyz/test/"));
        resp.assertAppStoppedOrNonExistent();
    }

    @Test
    public void testProxyInvalidId() {
        Response resp = apiTestHelper.callWithAuthDemo2(apiTestHelper.createRequest("/app_proxy/to-short-id/"));
        resp.assertAppStoppedOrNonExistent();
        resp = apiTestHelper.callWithAuthDemo2(apiTestHelper.createRequest("/app_proxy/to-short-id/my-path"));
        resp.assertAppStoppedOrNonExistent();

        // html
        resp = apiTestHelper.callWithAuthDemo2(apiTestHelper.createRequest("/app_proxy/to-short-id/").addHeader("Accept", "text/html"));
        resp.assertAppStoppedOrNonExistent();
        resp = apiTestHelper.callWithAuthDemo2(apiTestHelper.createRequest("/app_proxy/to-short-id/my-path").addHeader("Accept", "text/html"));
        resp.assertAppStoppedOrNonExistent();
    }

    @Test
    public void testProxy() {
        // start app
        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createPostRequest("/app_i/01_hello/_/"));
        String id = apiTestHelper.validateProxyObject(resp.jsonSuccess().asJsonObject());

        inst.client.waitForProxyStatus(id);

        // normal request
        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app_proxy/" + id + "/"));
        resp.assertHtmlSuccess();
        Assertions.assertTrue(resp.body().contains("Welcome to nginx!"));
        Assertions.assertFalse(resp.body().contains("js/shiny.iframe.js"));

        // html request
        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app_proxy/" + id + "/").addHeader("Accept", "text/html"));
        resp.assertHtmlSuccess();
        Assertions.assertTrue(resp.body().contains("Welcome to nginx!"));
        Assertions.assertTrue(resp.body().endsWith("<script src='/9f0e4e7085654f8393139ec029b480b1ca8bbe96/js/shiny.iframe.js'></script>"));

        // normal sub-path request
        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app_proxy/" + id + "/my-path"));
        Assertions.assertEquals(404, resp.code());
        Assertions.assertTrue(resp.body().contains("404 Not Found"));
        Assertions.assertFalse(resp.body().contains("js/shiny.iframe.js"));

        // html sub-path request
        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app_proxy/" + id + "/my-path").addHeader("Accept", "text/html"));
        Assertions.assertEquals(404, resp.code());
        Assertions.assertTrue(resp.body().contains("404 Not Found"));
        // non 200 request -> don't inject iframe
        Assertions.assertFalse(resp.body().contains("js/shiny.iframe.js"));

        // other user
        resp = apiTestHelper.callWithAuthDemo2(apiTestHelper.createRequest("/app_proxy/" + id + "/"));
        resp.assertAppStoppedOrNonExistent();

        // other user with sub-path
        resp = apiTestHelper.callWithAuthDemo2(apiTestHelper.createRequest("/app_proxy/" + id + "/my-path"));
        resp.assertAppStoppedOrNonExistent();

        // other user with sub-path and post
        resp = apiTestHelper.callWithAuthDemo2(apiTestHelper.createPostRequest("/app_proxy/" + id + "/my-path"));
        resp.assertAppStoppedOrNonExistent();
    }

    @Test
    public void testAppPageRedirect() {
        // no redirect
        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app/01_hello/my-page"));
        resp.assertHtmlSuccess();

        // redirect, abc is a target
        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app/01_hello/abc"));
        Assertions.assertEquals(302, resp.code());
        Assertions.assertEquals("http://localhost:7583/app/01_hello/abc/", resp.header("Location"));

        // no redirect (sub-path on additional target)
        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/app/01_hello/abc/my-page"));
        resp.assertHtmlSuccess();
    }
}
