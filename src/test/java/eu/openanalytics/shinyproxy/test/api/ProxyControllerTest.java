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

import javax.json.JsonArray;
import javax.json.JsonObject;
import java.util.List;

/**
 * Test the security (authorization of) {@link ProxyController}
 */
public class ProxyControllerTest {

    private static final String RANDOM_UUID = "8402e8c3-eaef-4fc7-9f23-9e843739dd0f";

    private static final ShinyProxyInstance inst = new ShinyProxyInstance("application-test-api.yml");
    private static final ApiTestHelper apiTestHelper = new ApiTestHelper(inst);

    @AfterAll
    public static void afterAll() {
        inst.close();
    }

    @Test
    public void testWithoutAuth() {
        Response resp = apiTestHelper.callWithoutAuth(apiTestHelper.createRequest("/api/proxyspec"));
        resp.assertAuthenticationRequired();
        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createRequest("/api/proxyspec/01_hello"));
        resp.assertAuthenticationRequired();
        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createRequest("/api/proxy"));
        resp.assertAuthenticationRequired();
        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createRequest("/api/proxy/" + RANDOM_UUID));
        resp.assertAuthenticationRequired();
        resp = apiTestHelper.callWithoutAuth(apiTestHelper.createPostRequest("/api/proxy/01_hello"));
        resp.assertAuthenticationRequired();
    }

    @Test
    public void testListSpecs() {
        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/api/proxyspec"));
        JsonArray specs = resp.jsonSuccess().asJsonArray();
        Assertions.assertEquals(1, specs.size());
        JsonObject spec = specs.getJsonObject(0);
        // response may not contain any sensitive values
        Assertions.assertEquals(List.of("id", "displayName", "description", "logoWidth", "logoHeight", "logoStyle", "logoClasses"), spec.keySet().stream().toList());
    }

    @Test
    public void testGetSpec() {
        // 1. no access to spec
        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/api/proxyspec/nobody"));
        resp.assertForbidden();
        // 2. spec does not exist
        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/api/proxyspec/does-not-exist"));
        resp.assertForbidden();
        // 3. response may not contain any sensitive values
        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/api/proxyspec/01_hello"));
        JsonObject spec = resp.jsonSuccess().asJsonObject();
        Assertions.assertEquals(List.of("id", "displayName", "description", "logoWidth", "logoHeight", "logoStyle", "logoClasses"), spec.keySet().stream().toList());
    }

    @Test
    public void testStartProxy() {
        // 1. no access to spec
        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createPostRequest("/api/proxy/nobody"));
        resp.assertForbidden();

        // 2. does not exist
        resp = apiTestHelper.callWithAuth(apiTestHelper.createPostRequest("/api/proxy/does-not-exist"));
        resp.assertForbidden();

        // 3. response may not contain any sensitive values
        resp = apiTestHelper.callWithAuth(apiTestHelper.createPostRequest("/api/proxy/01_hello"));
        JsonObject proxy = resp.jsonCreated().asJsonObject();
        apiTestHelper.validateProxyObject(proxy);

        inst.client.stopProxy(proxy.getString("id"));
    }

    @Test
    public void testGetProxy() {
        // 1. proxy does not exist
        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/api/proxy/" + RANDOM_UUID));
        resp.assertForbidden();

        // start proxy
        String id = inst.client.startProxy("01_hello");

        // 2. get proxy -> response may not contain any sensitive values
        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/api/proxy/" + id));
        JsonObject proxy = resp.jsonSuccess().asJsonObject();
        apiTestHelper.validateProxyObject(proxy);

        // 3. try to get proxy details as other user
        resp = apiTestHelper.callWithAuthDemo2(apiTestHelper.createRequest("/api/proxy/" + id));
        resp.assertForbidden();

        inst.client.stopProxy(proxy.getString("id"));
    }

    @Test
    public void testListProxies() {
        // 1. no proxies yet
        Response resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/api/proxy"));
        Assertions.assertEquals(0, resp.jsonSuccess().asJsonArray().size());

        // start proxy
        String id = inst.client.startProxy("01_hello");

        // 2. get proxies -> response may not contain any sensitive values
        resp = apiTestHelper.callWithAuth(apiTestHelper.createRequest("/api/proxy"));
        JsonArray proxies = resp.jsonSuccess().asJsonArray();
        Assertions.assertEquals(1, proxies.size());
        JsonObject proxy = proxies.getJsonObject(0);

        apiTestHelper.validateProxyObject(proxy);

        // 3. get proxies as other user
        resp = apiTestHelper.callWithAuthDemo2(apiTestHelper.createRequest("/api/proxy"));
        Assertions.assertEquals(0, resp.jsonSuccess().asJsonArray().size());

        inst.client.stopProxy(proxy.getString("id"));
    }
}
