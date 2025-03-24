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

public class IssueControllerTest {

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
    public void testWithoutAuth() {
        // no body
        Response resp = apiTestHelper.callWithoutAuth(apiTestHelper.createPostRequest("/issue"));
        resp.assertAuthenticationRequired();

        // some body
        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createPostRequest("/issue", "{\"currentLocation\": \"myLocation\", \"message\": \"myMessage\"}"));
        resp.assertAuthenticationRequired();

        // body with proxyId
        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createPostRequest("/issue", "{\"currentLocation\": \"myLocation\", \"message\": \"myMessage\", \"proxyId\": \"8402e8c3-eaef-4fc7-9f23-9e843739dd0f\"}"));
        resp.assertAuthenticationRequired();
    }

    @Test
    public void testWithAuth() {
        // no body
        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createPostRequest("/issue"));
        Assertions.assertEquals(400, resp.code());
        Assertions.assertEquals("{\"status\":\"fail\",\"data\":\"request body missing or invalid\"}", resp.body());

        // body without proxyId
        resp = apiTestHelper.callWithAuth(apiTestHelper.createPostRequest("/issue", "{\"currentLocation\": \"myLocation\", \"message\": \"myMessage\"}"));
        resp.jsonSuccess();

        // body with non-existent proxyId
        resp = apiTestHelper.callWithAuth(apiTestHelper.createPostRequest("/issue", "{\"currentLocation\": \"myLocation\", \"message\": \"myMessage\", \"proxyId\": \"8402e8c3-eaef-4fc7-9f23-9e843739dd0f\"}"));
        resp.assertForbidden();
    }

    @Test
    public void testWithApp() {
        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createPostRequest("/app_i/01_hello/_/"));
        String id = apiTestHelper.validateProxyObject(resp.jsonSuccess().asJsonObject());
        inst.client.waitForProxyStatus(id);

        // test as same user
        resp = apiTestHelper.callWithAuth(apiTestHelper.createPostRequest("/issue", "{\"currentLocation\": \"myLocation\", \"message\": \"myMessage\", \"proxyId\": \"" + id + "\"}"));
        resp.jsonSuccess();

        // test as other user
        resp = apiTestHelper.callWithAuthDemo2(apiTestHelper.createPostRequest("/issue", "{\"currentLocation\": \"myLocation\", \"message\": \"myMessage\", \"proxyId\": \"" + id + "\"}"));
        resp.assertForbidden();
    }

}
