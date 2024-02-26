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
package eu.openanalytics.shinyproxy.test.helpers;

import org.junit.jupiter.api.Assertions;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import java.io.IOException;


public class Response {
    private final okhttp3.Response response;

    public Response(okhttp3.Response response) {
        this.response = response;
    }

    public void assertAuthenticationRequired() {
        Assertions.assertEquals(410, code());
        Assertions.assertEquals("{\"status\":\"fail\", \"data\":\"shinyproxy_authentication_required\"}", body());
    }

    public void assertForbidden() {
        Assertions.assertEquals(403, code());
        Assertions.assertEquals("{\"status\":\"fail\",\"data\":\"forbidden\"}", body());
    }

    private JsonValue parseJson() {
        JsonReader jsonReader = Json.createReader(response.body().byteStream());
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

    public String body() {
        try {
            if (response.body() == null) {
                throw new RuntimeException("Body is null");
            }
            return response.body().string();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public int code() {
        return response.code();
    }

}
