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
import jakarta.json.JsonValue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


/**
 * Test the security (authorization of) {@link eu.openanalytics.containerproxy.api.ProxyStatusController}
 */
public class ProxyStatusControllerTest {

    private static final String RANDOM_UUID = "8402e8c3-eaef-4fc7-9f23-9e843739dd0f";

    private static final ShinyProxyInstance inst = new ShinyProxyInstance("application-test-api.yml");
    private static final ApiTestHelper apiTestHelper = new ApiTestHelper(inst);

    @AfterAll
    public static void afterAll() {
        inst.close();
    }

    @Test
    public void testWithoutAuth() {
        Response resp = apiTestHelper.callWithoutAuth(apiTestHelper.createRequest("/api/proxy/" + RANDOM_UUID + "/status"));
        resp.assertAuthenticationRequired();
        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createPutRequest("/api/proxy/" + RANDOM_UUID + "/status"));
        resp.assertAuthenticationRequired();
    }

    @Test
    public void testGetStatus() {
        // 1. proxy does not exists
        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/api/proxy/" + RANDOM_UUID + "/status"));
        JsonObject proxy = resp.jsonSuccess().asJsonObject();
        apiTestHelper.validateStoppedProxyObject(proxy);

        resp = apiTestHelper.callWithAuth(apiTestHelper.createPutRequest("/api/proxy/" + RANDOM_UUID + "/status", "{\"status\": \"Stopping\"}"));
        resp.assertForbidden();

        // 2. create proxy and get status
        String id = inst.client.startProxy("01_hello");
        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/api/proxy/" + id + "/status"));
        proxy = resp.jsonSuccess().asJsonObject();
        apiTestHelper.validateProxyObject(proxy);

        // 3. try to get proxy details as other user
        resp = apiTestHelper.callWithAuthDemo2(apiTestHelper.createRequest("/api/proxy/" + id + "/status"));
        // response should be that proxy was stopped
        proxy = resp.jsonSuccess().asJsonObject();
        apiTestHelper.validateStoppedProxyObject(proxy);

        // 4. try to change status as other user
        resp = apiTestHelper.callWithAuthDemo2(apiTestHelper.createPutRequest("/api/proxy/" + id + "/status", "{\"status\": \"Stopping\"}"));
        resp.assertForbidden();

        // 5. change status
        resp = apiTestHelper.callWithAuth(apiTestHelper.createPutRequest("/api/proxy/" + id + "/status", "{\"status\": \"Stopping\"}"));
        Assertions.assertEquals(JsonValue.NULL, resp.jsonSuccess());

        // 3. create proxy as non-admin user (demo2) and get status
        String id2 = inst.getClient("demo2").startProxy("all-access");
        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/api/proxy/" + id2 + "/status"));
        proxy = resp.jsonSuccess().asJsonObject();
        apiTestHelper.validateProxyObject(proxy);

        // 6. try to change status as admin (demo) of other user (demo2)
        resp = apiTestHelper.callWithAuth(apiTestHelper.createPutRequest("/api/proxy/" + id2 + "/status", "{\"status\": \"Pausing\"}"));
        resp.assertForbidden();

        // 7. try to change status as admin
        resp = apiTestHelper.callWithAuth(apiTestHelper.createPutRequest("/api/proxy/" + id2 + "/status", "{\"status\": \"Stopping\"}"));
        Assertions.assertEquals(JsonValue.NULL, resp.jsonSuccess());
    }

}
