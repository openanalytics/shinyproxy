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
package eu.openanalytics;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseFactory;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.DatabasePopulator;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.social.UserIdSource;
import org.springframework.social.config.annotation.ConnectionFactoryConfigurer;
import org.springframework.social.config.annotation.EnableSocial;
import org.springframework.social.config.annotation.SocialConfigurer;
import org.springframework.social.connect.Connection;
import org.springframework.social.connect.ConnectionFactory;
import org.springframework.social.connect.ConnectionFactoryLocator;
import org.springframework.social.connect.ConnectionSignUp;
import org.springframework.social.connect.UsersConnectionRepository;
import org.springframework.social.connect.jdbc.JdbcUsersConnectionRepository;
import org.springframework.social.connect.web.SignInAdapter;
import org.springframework.social.facebook.connect.FacebookConnectionFactory;
import org.springframework.social.github.connect.GitHubConnectionFactory;
import org.springframework.social.google.connect.GoogleConnectionFactory;
import org.springframework.social.linkedin.connect.LinkedInConnectionFactory;
import org.springframework.social.security.AuthenticationNameUserIdSource;
import org.springframework.social.twitter.connect.TwitterConnectionFactory;
import org.springframework.web.context.request.NativeWebRequest;

@Configuration
@EnableSocial
public class SocialConfiguration implements SocialConfigurer {
	
	@Override
	public void addConnectionFactories(ConnectionFactoryConfigurer connectionFactoryConfigurer, Environment environment) {
		for (Provider provider: socialProviders(environment)) {
			connectionFactoryConfigurer.addConnectionFactory(provider.createConnectionFactory(provider.getAppId(environment), provider.getAppSecret(environment)));
		}
	}

	@Override
	public UserIdSource getUserIdSource() {
		return new AuthenticationNameUserIdSource();
	}

	@Override
	public UsersConnectionRepository getUsersConnectionRepository(ConnectionFactoryLocator connectionFactoryLocator) {
		JdbcUsersConnectionRepository repository = new JdbcUsersConnectionRepository(dataSource(), connectionFactoryLocator, Encryptors.noOpText());
		repository.setConnectionSignUp(new ImplicitConnectionSignUp());
		return repository;
	}
	
	@Bean
	public DataSource dataSource() {
		EmbeddedDatabaseFactory factory = new EmbeddedDatabaseFactory();
		factory.setDatabaseName("shinyproxy-social");
		factory.setDatabaseType(EmbeddedDatabaseType.H2);
		factory.setDatabasePopulator(databasePopulator());
		return factory.getDatabase();
	}
	
	private DatabasePopulator databasePopulator() {
		ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
		populator.addScript(new ClassPathResource("JdbcUsersConnectionRepository.sql", JdbcUsersConnectionRepository.class));
		return populator;
	}

	@Bean
	public List<Provider> socialProviders(Environment environment) {
		List<Provider> activeProviders = new ArrayList<>();
		for (Provider provider: Provider.values()) {
			if (provider.getAppId(environment) != null && provider.getAppSecret(environment) != null) activeProviders.add(provider);
		}
		return activeProviders;
	}
	
	public enum Provider {
		
		facebook("Facebook"),
		twitter("Twitter"),
		google("Google+"),
		linkedin("LinkedIn"),
		github("GitHub");
		
		private String label;
		
		private Provider(String label) {
			this.label = label;
		}
		
		public ConnectionFactory<?> createConnectionFactory(String appId, String appSecret) {
			switch(this) {
			case facebook:
				return new FacebookConnectionFactory(appId, appSecret);
			case twitter:
				return new TwitterConnectionFactory(appId, appSecret);
			case google:
				GoogleConnectionFactory factory = new GoogleConnectionFactory(appId, appSecret);
				factory.setScope("openid profile");
				return factory;
			case linkedin:
				return new LinkedInConnectionFactory(appId, appSecret);
			case github:
				return new GitHubConnectionFactory(appId, appSecret);
			default:
				return null;
			}
		}
		
		public String label() {
			return label;
		}
		
		public String getAppId(Environment environment) {
			return environment.getProperty(String.format("shiny.proxy.social.%s.app-id", this.toString()));
		}
		
		public String getAppSecret(Environment environment) {
			return environment.getProperty(String.format("shiny.proxy.social.%s.app-secret", this.toString()));
		}
	}
	
	@Bean
	public SignInAdapter signInAdapter() {
		return new SimpleSignInAdapter(new HttpSessionRequestCache());
	}
	
	/**
	 * A simple signin adapter that sets up the Authentication context after an OAuth authentication
	 * and then redirects the user to the original requested URL.
	 */
	private static class SimpleSignInAdapter implements SignInAdapter {

		private final RequestCache requestCache;

		@Inject
		public SimpleSignInAdapter(RequestCache requestCache) {
			this.requestCache = requestCache;
		}
		
		@Override
		public String signIn(String localUserId, Connection<?> connection, NativeWebRequest request) {
			SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(localUserId, null, null));
			return extractOriginalUrl(request);
		}

		private String extractOriginalUrl(NativeWebRequest request) {
			HttpServletRequest nativeReq = request.getNativeRequest(HttpServletRequest.class);
			HttpServletResponse nativeRes = request.getNativeResponse(HttpServletResponse.class);
			SavedRequest saved = requestCache.getRequest(nativeReq, nativeRes);
			if (saved == null) {
				return null;
			}
			requestCache.removeRequest(nativeReq, nativeRes);
			removeAutheticationAttributes(nativeReq.getSession(false));
			return saved.getRedirectUrl();
		}
			 
		private void removeAutheticationAttributes(HttpSession session) {
			if (session == null) {
				return;
			}
			session.removeAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
		}

	}

	/**
	 * Simple connection signup that implicitly sings up the user
	 * using their display name.
	 */
	public final class ImplicitConnectionSignUp implements ConnectionSignUp {

		public String execute(Connection<?> connection) {
			return connection.getDisplayName();
		}

	}
}
