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

import eu.openanalytics.containerproxy.model.runtime.ParameterValues;
import eu.openanalytics.containerproxy.util.BadRequestException;

import javax.servlet.http.HttpServletRequest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppRequestInfo {

    private static final Pattern APP_INSTANCE_PATTERN = Pattern.compile(".*?/(app_i|app_direct_i)/([^/]*)/([^/]*)(/?.*)");
    private static final Pattern APP_PATTERN = Pattern.compile(".*?/(app|app_direct)/([^/]*)(/?.*)");
    private static final Pattern INSTANCE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]*$");

    private final String appName;
    private final String appInstance;
    private final String subPath;
    private ParameterValues providedParameters = null;

    public AppRequestInfo(String appName, String appInstance, String subPath) {
        this.appName = appName;
        this.appInstance = appInstance;
        this.subPath = subPath;
    }

    public static AppRequestInfo fromRequestOrException(HttpServletRequest request) {
        AppRequestInfo result = fromURI(request.getRequestURI());
        if (result == null) {
            throw new BadRequestException("Error parsing URL.");
        }
        return result;
    }

    public static AppRequestInfo fromRequestOrException(HttpServletRequest request, ParameterValues providedParameters) {
        AppRequestInfo result = fromRequestOrException(request);
        result.setProvidedParameters(providedParameters);
        return result;
    }


    public static AppRequestInfo fromURI(String uri) {
        Matcher appMatcher = APP_PATTERN.matcher(uri);
        Matcher appInstanceMatcher = APP_INSTANCE_PATTERN.matcher(uri);
        if (appInstanceMatcher.matches()) {
            String appName = appInstanceMatcher.group(2);
            if (appName == null || appName.trim().equals("")) {
                throw new BadRequestException("Error parsing URL: name of app not found in URL.");
            }

            String appInstance = appInstanceMatcher.group(3);
            if (appInstance == null || appInstance.trim().equals("")) {
                throw new BadRequestException("Error parsing URL: name of instance not found in URL.");
            }

            if (appInstance.length() > 64 || !INSTANCE_NAME_PATTERN.matcher(appInstance).matches()) {
                throw new BadRequestException("Error parsing URL: name of instance contains invalid characters or is too long.");
            }

            String subPath = appInstanceMatcher.group(4);
            if (subPath == null || subPath.trim().equals("")) {
                subPath = null;
            } else {
                subPath = subPath.trim();
            }

            return new AppRequestInfo(appName, appInstance, subPath);
        } else if (appMatcher.matches()) {
            String appName = appMatcher.group(2);
            if (appName == null || appName.trim().equals("")) {
                throw new BadRequestException("Error parsing URL: name of app not found in URL.");
            }

            String appInstance = "_";

            String subPath = appMatcher.group(3);
            if (subPath == null || subPath.trim().equals("")) {
                subPath = null;
            } else {
                subPath = subPath.trim();
            }

            return new AppRequestInfo(appName, appInstance, subPath);
        } else {
            return null;
        }
    }


    public String getAppInstance() {
        return appInstance;
    }

    public String getAppInstanceDisplayName() {
        if (appInstance.equals("_")) {
            return "Default";
        }
        return appInstance;
    }

    public String getAppName() {
        return appName;
    }

    public String getSubPath() {
        return subPath;
    }

    public ParameterValues getProvidedParameters() {
        return providedParameters;
    }

    private void setProvidedParameters(ParameterValues providedParameters) {
        this.providedParameters = providedParameters;
    }

}
