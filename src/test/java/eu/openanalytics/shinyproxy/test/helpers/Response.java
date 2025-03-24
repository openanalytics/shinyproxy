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
package eu.openanalytics.shinyproxy.test.helpers;

import org.junit.jupiter.api.Assertions;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;


public class Response {

    private final okhttp3.Response response;
    private String body;

    public Response(okhttp3.Response response) {
        this.response = response;
    }

    public void assertAuthenticationRequired() {
        Assertions.assertEquals(410, code());
        Assertions.assertEquals("{\"status\":\"fail\",\"data\":\"shinyproxy_authentication_required\"}", body());
    }

    public void assertAppStoppedOrNonExistent() {
        Assertions.assertEquals(410, code());
        Assertions.assertEquals("{\"status\":\"fail\",\"data\":\"app_stopped_or_non_existent\"}", body());
    }

    public void assertHtmlAuthenticationRequired() {
        Assertions.assertEquals(302, code());
        Assertions.assertNotNull(response.header("Location"));
    }

    public void assertForbidden() {
        Assertions.assertEquals(403, code());
        Assertions.assertEquals("{\"status\":\"fail\",\"data\":\"forbidden\"}", body());
    }

    public void assertFail(String message) {
        Assertions.assertEquals(400, code());
        Assertions.assertEquals("{\"status\":\"fail\",\"data\":\"" + message + "\"}", body());
    }

    private JsonValue parseJson() {
        JsonReader jsonReader = Json.createReader(new ByteArrayInputStream(body().getBytes(StandardCharsets.UTF_8)));
        JsonObject object = jsonReader.readObject();
        jsonReader.close();
        Assertions.assertTrue(object.containsKey("status"));
        Assertions.assertEquals("success", object.getString("status"));
        return object.get("data");
    }

    public JsonValue jsonSuccess() {
        Assertions.assertEquals(200, code());
        return parseJson();
    }

    public JsonValue jsonCreated() {
        Assertions.assertEquals(201, code());
        return parseJson();
    }

    public void assertHtmlSuccess() {
        Assertions.assertEquals(200, code());
        Assertions.assertTrue(response.header("Content-Type", "").startsWith("text/html"));
        Assertions.assertNotNull(body());
    }

    public String body() {
        if (body == null) {
            try {
                if (response.body() == null) {
                    throw new RuntimeException("Body is null");
                }
                body = response.body().string();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return body;
    }

    public int code() {
        return response.code();
    }

    public String header(String location) {
        return response.header(location);
    }
}
