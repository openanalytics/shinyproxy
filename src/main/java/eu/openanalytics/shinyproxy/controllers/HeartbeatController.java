package eu.openanalytics.shinyproxy.controllers;

import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.service.HeartbeatService;
import eu.openanalytics.containerproxy.service.ProxyService;
import eu.openanalytics.containerproxy.service.UserService;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

@Controller
public class HeartbeatController {

    @Inject
    private HeartbeatService heartbeatService;

    @Inject
    private ProxyService proxyService;

    @Inject
    private UserService userService;

    /**
     * Endpoint used to force a heartbeat. This is used when an app cannot piggy-back heartbeats on other requests
     * or on a WebSocket connection.
     */
    @RequestMapping(value = "/heartbeat/{proxyId}", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, String> heartbeat(@PathVariable("proxyId") String proxyId) {
        Proxy proxy = proxyService.getProxy(proxyId);

        if (!userService.isOwner(proxy)) {
            throw new AccessDeniedException(String.format("Cannot register heartbeat for proxy %s: access denied", proxyId));
        }

        heartbeatService.heartbeatReceived(proxy);

        Map<String,String> response = new HashMap<>();
        response.put("status", "success");
        return response;
    }
}
