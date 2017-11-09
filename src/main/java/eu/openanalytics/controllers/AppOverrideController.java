/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2017 Open Analytics
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
package eu.openanalytics.controllers;

import eu.openanalytics.ShinyProxyApplication;
import eu.openanalytics.services.AppService;
import eu.openanalytics.services.DockerService;
import eu.openanalytics.services.TagOverrideService;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.SignatureException;
import java.util.Date;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.bouncycastle.util.Arrays;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.Base64Utils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.google.common.primitives.Longs;

@Controller
public class AppOverrideController extends BaseController {

	@Inject
	DockerService dockerService;

	@Inject
	AppService appService;

	@Inject
	TagOverrideService tagOverrideService;

	private byte[] hashOverride(byte[] secret, String app, String tag, long expires) throws NoSuchAlgorithmException {
		byte[] appBytes;
		byte[] tagBytes;
		try {
			appBytes = app.getBytes("UTF8");
			tagBytes = tag.getBytes("UTF8");
		} catch (UnsupportedEncodingException e) {
			appBytes = app.getBytes();
			tagBytes = tag.getBytes();
		}
		byte[] expiresBytes = Longs.toByteArray(expires);
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		digest.update(appBytes);
		digest.update((byte) 0);
		digest.update(tagBytes);
		digest.update((byte) 0);
		digest.update(expiresBytes);
		digest.update((byte) 0);
		digest.update(secret);
		return digest.digest();
	}

	@RequestMapping(value="/appOverride/**", params={"sig", "expires"}, method=RequestMethod.GET)
	String appOverride(ModelMap map, Principal principal, HttpServletRequest request, HttpServletResponse response) throws InvalidKeyException, NoSuchAlgorithmException, SignatureException {
		if (!validateOverride(request, response)) return null;
		prepareMap(map, request);
		
		String mapping = dockerService.getMapping(getUserName(request), getAppName(request), getTagOverride(request), false);
		String contextPath = ShinyProxyApplication.getContextPath(environment);

		map.put("appTitle", getAppTitle(request));
		map.put("container", appService.buildContainerPath(mapping, request));
		map.put("heartbeatRate", environment.getProperty("shiny.proxy.heartbeat-rate", "10000"));
		map.put("heartbeatPath", contextPath + "/heartbeat");
		
		return "app";
	}

	@RequestMapping(value="/appOverride/**", params={"sig", "expires"}, method=RequestMethod.POST)
	@ResponseBody
	String startAppOverride(HttpServletRequest request, HttpServletResponse response) throws InvalidKeyException, NoSuchAlgorithmException, SignatureException {
		if (!validateOverride(request, response)) return null;
		String mapping = dockerService.getMapping(getUserName(request), getAppName(request), getTagOverride(request), true);
		return appService.buildContainerPath(mapping, request);
	}

	private boolean validateOverride(HttpServletRequest request, HttpServletResponse response) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		String appName = getAppName(request);
		String tagOverride = getTagOverride(request);
		if (appName == null || tagOverride == null) {
			sendSimpleResponse(response, 400, "Bad Request: tag override or app name not specified");
			return false;
		}
		byte[] signature;
		try {
			signature = Base64Utils.decodeFromUrlSafeString(request.getParameter("sig"));
		} catch (Exception e) {
			sendSimpleResponse(response, 400, "Bad Request: failed to decode signature as hex");
			return false;
		}
		long expires;
		try {
			expires = Long.parseLong(request.getParameter("expires"));
		} catch (NumberFormatException e) {
			sendSimpleResponse(response, 400, "Bad Request: expires parameter not a valid long");
			return false;
		}
		if (expires > 0 && expires < new Date().getTime()) {
			sendSimpleResponse(response, 400, "Bad Request: tag override has expired");
			return false;
		}
		byte[] secret = tagOverrideService.getSecret();
		if (secret == null) {
			sendSimpleResponse(response, 501, "Not Implemented: tag overriding is either disabled or failed to intialize");
			return false;
		}
		byte[] hashBytes = hashOverride(secret, getAppName(request), getTagOverride(request), expires);
		int sigLen = tagOverrideService.getMinSigLen();
		if (signature.length < sigLen) {
			sendSimpleResponse(response, 400, "Bad Request: signature not long enough");
			return false;
		}
		int xor = 0;
		// CAUTION - THIS NEEDS TO BE TIMING ATTACK RESISTANT
		// A simple == will lead to a timing attack
		// Google "crypto timing attack" for more info
		for (int i = 0; i < sigLen; i++) {
			xor |= hashBytes[i] ^ signature[i];
		}
		if (xor != 0) {
			sendSimpleResponse(response, 400, "Bad Request: signature does not match");
			return false;
		}
		return true;
	}

	@RequestMapping(value="/appOverride/**", method={RequestMethod.GET, RequestMethod.POST})
	@ResponseBody
	String createAppOverride(
		HttpServletRequest request, HttpServletResponse response,
		// number of milliseconds before signature expires
		// clamped by config
		@RequestParam(value="expiry", required=false) Long expiry
	) throws SignatureException, NoSuchAlgorithmException, InvalidKeyException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			// User is not logged in
			response.setStatus(401);
			response.setContentType("text/plain");
			return "Unauthorized: you must sign in to create app overrides";
		}
		if (!userService.isAdmin(authentication)) {
			// User is logged in, but is not an admin
			response.setStatus(403);
			response.setContentType("text/plain");
			return "Forbidden: only admins may create app overrides";
		}
		byte[] secret = tagOverrideService.getSecret();
		if (secret == null) {
			response.setStatus(501);
			response.setContentType("text/plain");
			return "Not Implemented: tag overriding is either disabled or failed to intialize";
		}
		long maxExpiry = ((long) tagOverrideService.getMaxTagOverrideExpirationDays()) * 1000 * 60 * 60 * 24;
		if (expiry == null) {
			expiry = ((long) tagOverrideService.getDefaultTagOverrideExpirationDays()) * 1000 * 60 * 60 * 24;
		} else if (maxExpiry > 0) {
			if (expiry > 0) {
				expiry = Math.min(expiry, maxExpiry);
			} else {
				expiry = maxExpiry;
			}
		}
		long expiresAt;
		if (expiry <= 0) {
			expiresAt = 0;
		} else {
			expiresAt = new Date().getTime() + expiry;
		}
		byte[] hashBytes = hashOverride(secret, getAppName(request), getTagOverride(request), expiresAt);
		byte[] shortHashBytes = Arrays.copyOfRange(hashBytes, 0, tagOverrideService.getURLSigLen());
		// No longer an actual signature, just a hash including a secret
		String signature = Base64Utils.encodeToUrlSafeString(shortHashBytes);
		String overrideLocation = "?expires=" + expiresAt + "&sig=" + signature;
		String requestQS = request.getQueryString();
		if (requestQS != null) {
			requestQS = "&" + requestQS;
			int expiryIdx = requestQS.indexOf("&expiry=");
			int nextParamIdx = requestQS.indexOf('&', expiryIdx + 1);
			if (expiryIdx != -1) {
				String otherQS = requestQS.substring(0, expiryIdx);
				if (nextParamIdx != -1) {
					otherQS += requestQS.substring(nextParamIdx);
				}
				requestQS = otherQS;
			}
			overrideLocation += requestQS;
		}
		if (request.getMethod() == "GET") {
			response.setStatus(302);
			response.setHeader("Location", overrideLocation);
			return null;
		} else {
			return request.getRequestURI() + overrideLocation;
		}
	}
}
