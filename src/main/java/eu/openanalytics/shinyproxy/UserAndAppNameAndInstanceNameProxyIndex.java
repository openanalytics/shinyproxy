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

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.store.IProxyStore;
import eu.openanalytics.containerproxy.service.ProxyIdIndex;
import eu.openanalytics.shinyproxy.runtimevalues.AppInstanceKey;
import org.springframework.stereotype.Component;

@Component
public class UserAndAppNameAndInstanceNameProxyIndex extends ProxyIdIndex<UserAndAppNameAndInstanceNameProxyIndex.UserAndAppNameAndInstanceNameKey> {

    public UserAndAppNameAndInstanceNameProxyIndex(IProxyStore proxyStore) {
        super(proxyStore, (key, proxy) -> proxy.getSpecId().equals(key.appName) && proxy.getUserId().equals(key.userId) && proxy.getRuntimeValue(AppInstanceKey.inst).equals(key.instanceName));
    }

    public Proxy getProxy(String userId, String appname, String appInstance) {
        return getProxy(userId, new UserAndAppNameAndInstanceNameKey(userId, appname, appInstance));
    }

    public record UserAndAppNameAndInstanceNameKey(String userId, String appName, String instanceName) {
    }

}
