/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2021 Open Analytics
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
package eu.openanalytics.shinyproxy;

import eu.openanalytics.containerproxy.util.BadRequestException;

import javax.servlet.http.HttpServletRequest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppRequestInfo {

    private final String appName;
    private final String appInstance;
    private final String subPath;

    private static final Pattern appPattern = Pattern.compile(".*?/app[^/]*/([^/]*)/([^/]*)(/?.*)");

    public AppRequestInfo(String appName, String appInstance, String subPath) {
        this.appName = appName;
        this.appInstance = appInstance;
        this.subPath = subPath;
    }

    public static AppRequestInfo fromRequest(HttpServletRequest request) {
        return fromURI(request.getRequestURI());
    }

    public static AppRequestInfo fromURI(String uri) {
        Matcher matcher = appPattern.matcher(uri);
        if (matcher.matches()) {

            String appName =  matcher.group(1);
            if (appName == null || appName.trim().equals("")) {
                throw new BadRequestException("Error parsing URL: name of app not found in URL.");
            }

            String appInstance =  matcher.group(2);
            if (appInstance == null || appInstance.trim().equals("")) {
                throw new BadRequestException("Error parsing URL: name of instance not found in URL.");
            }

            String subPath =  matcher.group(3);
            if (subPath == null || subPath.trim().equals("")) {
                subPath = null;
            } else {
                subPath = subPath.trim();
            }

            return new AppRequestInfo(appName, appInstance, subPath);
        } else {
            throw new BadRequestException("Error parsing URL.");
        }
    }

    public String getAppInstance() {
        return appInstance;
    }

    public String getAppName() {
        return appName;
    }

    public String getSubPath() {
        return subPath;
    }
}
