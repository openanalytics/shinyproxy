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
package eu.openanalytics.shinyproxy;

import jakarta.servlet.http.HttpServletRequest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppRequestInfo {

    private static final Pattern APP_INSTANCE_PATTERN = Pattern.compile(".*?/(app_i|app_direct_i)/([^/]*)/([^/]*)(/?.*)");
    private static final Pattern APP_PATTERN = Pattern.compile(".*?/(app|app_direct)/([^/]*)(/?.*)");
    private static final Pattern INSTANCE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]*$");

    private final String appName;
    private final String appInstance;
    private final String subPath;
    private final String appPath;

    public AppRequestInfo(String appName, String appInstance, String appPath, String subPath) {
        this.appName = appName;
        this.appInstance = appInstance;
        this.appPath = appPath;
        this.subPath = subPath;
    }

    public static AppRequestInfo fromRequestOrNull(HttpServletRequest request) {
        return fromURI(request.getRequestURI());
    }

    public static AppRequestInfo fromURI(String uri) {
        Matcher appMatcher = APP_PATTERN.matcher(uri);
        Matcher appInstanceMatcher = APP_INSTANCE_PATTERN.matcher(uri);
        if (appInstanceMatcher.matches()) {
            String appName = appInstanceMatcher.group(2);
            if (appName == null || appName.trim().equals("")) {
                return null;
            }

            String appInstance = appInstanceMatcher.group(3);
            if (appInstance == null || appInstance.trim().equals("")) {
                return null;
            }

            if (appInstance.length() > 64 || !INSTANCE_NAME_PATTERN.matcher(appInstance).matches()) {
                return null;
            }

            String subPath = appInstanceMatcher.group(4);
            String appPath;
            if (subPath == null || subPath.trim().equals("")) {
                subPath = null;
                appPath = uri;
            } else {
                subPath = subPath.trim().substring(1); // remove first slash
                appPath = uri.substring(0, uri.length() - subPath.length());
            }

            return new AppRequestInfo(appName, appInstance, appPath, subPath);
        } else if (appMatcher.matches()) {
            String appName = appMatcher.group(2);
            if (appName == null || appName.trim().equals("")) {
                return null;
            }

            String appInstance = "_";

            String subPath = appMatcher.group(3);
            String appPath;
            if (subPath == null || subPath.trim().equals("")) {
                subPath = null;
                appPath = uri;
            } else {
                subPath = subPath.trim().substring(1); // remove first slash
                appPath = uri.substring(0, uri.length() - subPath.length());
            }

            return new AppRequestInfo(appName, appInstance, appPath, subPath);
        } else {
            return null;
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

    public String getAppPath() {
        return appPath;
    }

}
