package eu.openanalytics.controllers;

import java.io.IOException;
import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import eu.openanalytics.services.HeartbeatService;

@Controller
public class HeartbeatController {

	@Inject
	HeartbeatService heartbeatService;
	
	@RequestMapping("/heartbeat/**")
	void heartbeat(Principal principal, HttpServletRequest request, HttpServletResponse response) {
		String userName = (principal == null) ? request.getSession().getId() : principal.getName();
		Matcher matcher = Pattern.compile(".*/app/(.*)").matcher(request.getRequestURI());
		String appName = matcher.matches() ? matcher.group(1) : null;
		heartbeatService.heartbeatReceived(userName, appName);
		try {
			response.setStatus(200);
			response.getWriter().write("Ok");
			response.getWriter().flush();
		} catch (IOException e) {}
	}
}
