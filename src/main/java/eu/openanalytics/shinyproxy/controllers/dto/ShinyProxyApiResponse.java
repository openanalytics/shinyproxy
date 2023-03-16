/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2023 Open Analytics
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
package eu.openanalytics.shinyproxy.controllers.dto;

import eu.openanalytics.containerproxy.api.dto.ApiResponse;
import org.springframework.http.ResponseEntity;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ShinyProxyApiResponse {

    public static <T> ResponseEntity<ApiResponse<T>> appStoppedOrNonExistent() {
        return ResponseEntity.status(410).body(new ApiResponse<>("fail", "app_stopped_or_non_existent"));
    }

    public static void appStoppedOrNonExistent(HttpServletResponse response) throws IOException {
        response.setStatus(410);
        response.getWriter().write("{\"status\":\"fail\", \"data\":\"app_stopped_or_non_existent\"}");
    }

    public static void authenticationRequired(HttpServletResponse response) throws IOException {
        response.setStatus(401);
        response.getWriter().write("{\"status\":\"fail\", \"data\":\"shinyproxy_authentication_required\"}");
    }

}
