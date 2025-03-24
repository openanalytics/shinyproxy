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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.json.JsonArray;
import javax.json.JsonObject;
import java.util.List;
import java.util.Map;

public class ProxyApiControllerTest {

    private static final String RANDOM_UUID = "8402e8c3-eaef-4fc7-9f23-9e843739dd0f";

    @Test
    public void testChangeProxyUserIdDisabled() {
        try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-api.yml")) {
            ApiTestHelper apiTestHelper = new ApiTestHelper(inst);
            // start app
            Response resp = apiTestHelper.callWithAuth(apiTestHelper.createPostRequest("/app_i/01_hello/_/"));
            String id = apiTestHelper.validateProxyObject(resp.jsonSuccess().asJsonObject());
            inst.client.waitForProxyStatus(id);

            // try to change user id
            resp = apiTestHelper.callWithAuth(apiTestHelper.createPutRequest("/api/proxy/" + id + "/userId", "{\"userId\": \"demo2\"}"));
            resp.assertForbidden();
        }
    }

    @Test
    public void testChangeProxyUserId() {
        try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-api.yml", Map.of("proxy.allow-transfer-app", "true"))) {
            ApiTestHelper apiTestHelper = new ApiTestHelper(inst);
            // start app
            Response resp = apiTestHelper.callWithAuth(apiTestHelper.createPostRequest("/app_i/01_hello/_/"));
            String id = apiTestHelper.validateProxyObject(resp.jsonSuccess().asJsonObject());
            inst.client.waitForProxyStatus(id);

            // try to change user id
            resp = apiTestHelper.callWithAuth(apiTestHelper.createPutRequest("/api/proxy/" + id + "/userId", "{\"userId\": \"demo2\"}"));
            resp.jsonSuccess();
        }
    }

    @Test
    public void testChangeProxyUserIdNoAccess() {
        try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-api.yml", Map.of("proxy.allow-transfer-app", "true"))) {
            ApiTestHelper apiTestHelper = new ApiTestHelper(inst);
            // try to change user id of app that does not exist
            Response resp = apiTestHelper.callWithAuth(apiTestHelper.createPutRequest("/api/proxy/" + RANDOM_UUID + "/userId", "{\"userId\": \"demo2\"}"));
            resp.assertForbidden();

            // start app
            resp = apiTestHelper.callWithAuth(apiTestHelper.createPostRequest("/app_i/01_hello/_/"));
            String id = apiTestHelper.validateProxyObject(resp.jsonSuccess().asJsonObject());
            inst.client.waitForProxyStatus(id);

            // without auth, random id
            resp = apiTestHelper.callWithoutAuth(apiTestHelper.createPutRequest("/api/proxy/" + RANDOM_UUID + "/userId", "{\"userId\": \"demo2\"}"));
            resp.assertAuthenticationRequired();

            // without auth, real id
            resp = apiTestHelper.callWithoutAuth(apiTestHelper.createPutRequest("/api/proxy/" + id + "/userId", "{\"userId\": \"demo2\"}"));
            resp.assertAuthenticationRequired();

            // try to change user id using other user
            resp = apiTestHelper.callWithAuthDemo2(apiTestHelper.createPutRequest("/api/proxy/" + id + "/userId", "{\"userId\": \"demo2\"}"));
            resp.assertForbidden();
        }
    }

    @Test
    public void testCustomAppDetails() {
        try (ShinyProxyInstance inst = new ShinyProxyInstance("application-test-api.yml")) {
            ApiTestHelper apiTestHelper = new ApiTestHelper(inst);

            // without auth, random id
            Response resp = apiTestHelper.callWithoutAuth(apiTestHelper.createRequest("/api/proxy/" + RANDOM_UUID + "/details"));
            resp.assertAuthenticationRequired();

            // start app
            resp = apiTestHelper.callWithAuth(apiTestHelper.createPostRequest("/app_i/01_hello/_/"));
            String id = apiTestHelper.validateProxyObject(resp.jsonSuccess().asJsonObject());
            inst.client.waitForProxyStatus(id);

            // without auth, real id
            resp = apiTestHelper.callWithoutAuth(apiTestHelper.createRequest("/api/proxy/" + id + "/details"));
            resp.assertAuthenticationRequired();

            // with auth, real id
            resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/api/proxy/" + id + "/details"));
            JsonArray details = resp.jsonSuccess().asJsonArray();
            Assertions.assertEquals(0, details.size());

            // with auth, real id, but wrong user
            resp = apiTestHelper.callWithAuthDemo2(apiTestHelper.createRequest("/api/proxy/" + id + "/details"));
            resp.assertAppStoppedOrNonExistent();
        }

    }

}
