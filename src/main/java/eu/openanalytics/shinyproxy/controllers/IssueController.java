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
package eu.openanalytics.shinyproxy.controllers;

import eu.openanalytics.containerproxy.api.dto.ApiResponse;
import eu.openanalytics.containerproxy.log.LogPaths;
import eu.openanalytics.containerproxy.model.runtime.Proxy;
import eu.openanalytics.containerproxy.model.spec.ProxySpec;
import eu.openanalytics.containerproxy.service.LogService;
import eu.openanalytics.containerproxy.service.StructuredLogger;
import eu.openanalytics.containerproxy.spec.IProxySpecProvider;
import eu.openanalytics.shinyproxy.ShinyProxySpecExtension;
import eu.openanalytics.shinyproxy.controllers.dto.ReportIssueDto;
import eu.openanalytics.shinyproxy.runtimevalues.AppInstanceKey;
import jakarta.mail.internet.MimeMessage;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.inject.Inject;
import java.io.File;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Controller
public class IssueController extends BaseController {

    private final String mailFromAddress;
    private final String defaultMailSubject;

    @Inject
    private LogService logService;

    @Inject
    private IProxySpecProvider proxySpecProvider;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final StructuredLogger slogger = new StructuredLogger(logger);

    public IssueController(Environment environment) {
        mailFromAddress = environment.getProperty("proxy.support.mail-from-address", "issues@shinyproxy.io");
        defaultMailSubject = environment.getProperty("proxy.support.mail-subject", "ShinyProxy Error Report");
    }

    @RequestMapping(value = "/issue", method = RequestMethod.POST)
    public ResponseEntity<ApiResponse<Void>> postIssue(@RequestBody ReportIssueDto reportIssueDto) {
        if (StringUtils.isBlank(defaultSupportAddress) || mailSender == null) {
            return ApiResponse.fail("Report issue is not configured");
        }
        if (StringUtils.isBlank(reportIssueDto.getMessage())) {
            return ApiResponse.fail("Cannot report issue: no message provided");
        }
        if (StringUtils.isBlank(reportIssueDto.getCurrentLocation())) {
            return ApiResponse.fail("Cannot report issue: no currentLocation provided");
        }
        Proxy proxy = null;
        String supportAddress = defaultSupportAddress;
        String subject = defaultMailSubject;
        if (!StringUtils.isBlank(reportIssueDto.getProxyId())) {
            proxy = proxyService.getUserProxy(reportIssueDto.getProxyId());
            if (proxy == null) {
                return ApiResponse.failForbidden();
            }
            ProxySpec proxySpec = proxySpecProvider.getSpec(proxy.getSpecId());
            if (proxySpec != null) {
                ShinyProxySpecExtension extension = proxySpec.getSpecExtension(ShinyProxySpecExtension.class);
                if (extension.getSupportMailToAddress() != null) {
                    supportAddress = extension.getSupportMailToAddress();
                }
                if (extension.getSupportMailSubject() != null) {
                    subject = extension.getSupportMailSubject();
                }
            }
        }

        if (sendSupportMail(proxy, supportAddress, subject, reportIssueDto.getMessage(), reportIssueDto.getCurrentLocation())) {
            return ApiResponse.success();
        }
        return ApiResponse.fail("Error while sending e-mail");
    }

    private boolean sendSupportMail(Proxy proxy, String supportAddress, String subject, String message, String currentLocation) {
        try {
            MimeMessage mailMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mailMessage, true);

            // Headers
            helper.setFrom(mailFromAddress);
            helper.addTo(supportAddress);
            helper.setSubject(subject);

            // Body
            String lineSep = System.lineSeparator();
            StringBuilder body = new StringBuilder();
            body.append(String.format("This is an error report generated by ShinyProxy%s", lineSep));
            body.append(String.format("User: %s%s", userService.getCurrentUserId(), lineSep));
            body.append(String.format("Location: %s%s", currentLocation, lineSep));
            body.append(String.format("Message: %s%s", message, lineSep));
            if (proxy != null) {
                body.append(String.format("AppId: %s%s", proxy.getId(), lineSep));
                body.append(String.format("App: %s%s", proxy.getSpecId(), lineSep));
                String instanceName = proxy.getRuntimeValue(AppInstanceKey.inst);
                if (instanceName.equals("_")) {
                    body.append(String.format("Instance name: Default%s", lineSep));
                } else {
                    body.append(String.format("Instance name: %s%s", instanceName, lineSep));
                }

                // Attach logs (if container-logging is enabled)
                LogPaths filePaths = logService.getLogs(proxy);

                if (filePaths != null) {
                    File stdout = filePaths.getStdout().toFile();
                    if (stdout.exists() && stdout.length() > 0) {
                        helper.addAttachment(stdout.getName(), stdout);
                        // if stderr exists add it as well (stdout may exists without stderr)
                        File stderr = filePaths.getStderr().toFile();
                        if (stderr.exists() && stderr.length() > 0) {
                            helper.addAttachment(stderr.getName(), stderr);
                        }
                    } else {
                        body.append(String.format("Log (stdout): %s%s", filePaths.getStdout(), lineSep));
                        body.append(String.format("Log (stderr): %s%s", filePaths.getStderr(), lineSep));
                    }
                }
            }

            helper.setText(body.toString());
            mailSender.send(mailMessage);
            if (proxy != null) {
                slogger.info(proxy, "User reported an issue, location: " + currentLocation);
            } else {
                logger.info("[{}] User reported an issue, location: " + currentLocation, kv("user", userService.getCurrentUserId()));
            }
            return true;
        } catch (Exception e) {
            logger.error("Error while sending issue report", e);
            return false;
        }
    }


}
