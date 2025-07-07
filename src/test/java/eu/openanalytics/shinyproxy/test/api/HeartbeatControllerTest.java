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
import jakarta.json.JsonObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class HeartbeatControllerTest {

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
        Response resp = apiTestHelper.callWithoutAuth(apiTestHelper.createPostRequest("/heartbeat/" + RANDOM_UUID));
        resp.assertAuthenticationRequired();

        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createRequest("/heartbeat/" + RANDOM_UUID));
        resp.assertAuthenticationRequired();
    }

    @Test
    public void testWithAuth() {
        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createPostRequest("/heartbeat/" + RANDOM_UUID));
        resp.assertAppStoppedOrNonExistent();

        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/heartbeat/" + RANDOM_UUID));
        resp.assertAppStoppedOrNonExistent();
    }

    @Test
    public void testWithApp() {
        String id = inst.client.startProxy("01_hello");
        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createPostRequest("/heartbeat/" + id));
        resp.jsonSuccess();

        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/heartbeat/" + id));
        JsonObject json = resp.jsonSuccess().asJsonObject();
        Assertions.assertEquals(2, json.size());
        Assertions.assertNotNull(json.getJsonNumber("lastHeartbeat"));
        Assertions.assertNotNull(json.getJsonNumber("heartbeatRate"));

        // test as other user
        resp = apiTestHelper.callWithAuthDemo2(apiTestHelper.createPostRequest("/heartbeat/" + id));
        resp.assertAppStoppedOrNonExistent();

        resp = apiTestHelper.callWithAuthDemo2(apiTestHelper.createRequest("/heartbeat/" + id));
        resp.assertAppStoppedOrNonExistent();
    }

}
