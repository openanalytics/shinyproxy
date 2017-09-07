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

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.inject.Inject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.embedded.EmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.PortInUseException;
import org.springframework.boot.context.embedded.undertow.UndertowDeploymentInfoCustomizer;
import org.springframework.boot.context.embedded.undertow.UndertowEmbeddedServletContainerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;

import eu.openanalytics.services.DockerService;
import eu.openanalytics.services.DockerService.MappingListener;
import io.undertow.Handlers;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.servlet.api.DeploymentInfo;

@SpringBootApplication
@EnableAsync
@Configuration
public class ShinyProxyApplication {

	@Inject
	DockerService dockerService;

	@Inject
	Environment environment;

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(ShinyProxyApplication.class);
		
		boolean hasExternalConfig = Files.exists(Paths.get("application.yml"));
		if (!hasExternalConfig) app.setAdditionalProfiles("demo");
		
		try {
			app.run(args);
		} catch (Exception e) {
			// Workaround for bug in UndertowEmbeddedServletContainer.start():
			// If undertow.start() fails, started remains false which prevents undertow.stop() from ever being called.
			// Undertow's (non-daemon) XNIO worker threads will then prevent the JVM from exiting.
			if (e instanceof PortInUseException) System.exit(-1);
		}
	}

	public static String getContextPath(Environment env) {
		String contextPath = env.getProperty("server.contextPath");
		if (contextPath == null) contextPath = "";
		return contextPath;
	}
	
	@Bean
	public EmbeddedServletContainerFactory servletContainer() {
		UndertowEmbeddedServletContainerFactory factory = new UndertowEmbeddedServletContainerFactory();
		factory.addDeploymentInfoCustomizers(new UndertowDeploymentInfoCustomizer() {
			@Override
			public void customize(DeploymentInfo deploymentInfo) {
				deploymentInfo.addInitialHandlerChainWrapper(new RootHandlerWrapper());
			}
		});
		factory.setPort(Integer.parseInt(environment.getProperty("shiny.proxy.port", "8080")));
		return factory;	
	}

	private class RootHandlerWrapper implements HandlerWrapper {
		public HttpHandler wrap(HttpHandler defaultHandler) {
			PathHandler pathHandler = Handlers.path(defaultHandler);
			dockerService.addMappingListener(new MappingListener() {
				@Override
				public void mappingAdded(String mapping, URI target) {
					LoadBalancingProxyClient proxyClient = new LoadBalancingProxyClient();
					proxyClient.addHost(target);
					HttpHandler handler = new ProxyHandler(proxyClient, ResponseCodeHandler.HANDLE_404);
					pathHandler.addPrefixPath(mapping, handler);
				}
				@Override
				public void mappingRemoved(String mapping) {
					pathHandler.removePrefixPath(mapping);
				}
			});
			return pathHandler;
		}
	}
}
