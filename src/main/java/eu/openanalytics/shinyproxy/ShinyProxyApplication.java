/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2018 Open Analytics
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

import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import javax.inject.Inject;

import org.apache.log4j.Logger;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.xnio.OptionMap;
import org.xnio.Options;

import eu.openanalytics.shinyproxy.entity.App;
import eu.openanalytics.shinyproxy.services.ProxyService;
import eu.openanalytics.shinyproxy.services.ProxyService.MappingListener;
import eu.openanalytics.shinyproxy.services.ShinyAppServiceImpl;
import eu.openanalytics.shinyproxy.services.UserService;
import eu.openanalytics.shinyproxy.util.Utils;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.RequestDumpingHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.util.PathMatcher;

@SpringBootApplication
@EnableAsync
@Configuration
public class ShinyProxyApplication {

	private static final Logger log = Utils.loggerForThisClass();
	private static final OptionMap DEFAULT_OPTIONS;
	
	@Inject	
	ProxyService proxyService;
	
	@Inject
	UserService userService;
	
	@Inject
	ShinyAppServiceImpl shinyAppServiceImpl;

	@Inject
	Environment environment;

	public static void main(String[] args) {
		log.info("Started");
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
		log.info("Finished");
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
	
	static {
        final OptionMap.Builder builder = OptionMap.builder()
                .set(Options.WORKER_IO_THREADS, 8)
                .set(Options.TCP_NODELAY, true)
                .set(Options.KEEP_ALIVE, true)
                .set(Options.WORKER_NAME, "Client");

        DEFAULT_OPTIONS = builder.getMap();
    }

	private class RootHandlerWrapper implements HandlerWrapper {		
		
		public HttpHandler wrap(HttpHandler defaultHandler) {
			PathHandler pathHandler = new PathHandler(defaultHandler) {
				
				@SuppressWarnings("unchecked")
				@Override
				public void handleRequest(HttpServerExchange exchange) throws Exception {
					
					log.info("handleRequest started: " + exchange.getRequestURI());
					Field field = PathHandler.class.getDeclaredField("pathMatcher");
					field.setAccessible(true);
					PathMatcher<HttpHandler> pathMatcher = (PathMatcher<HttpHandler>) field.get(this);
					PathMatcher.PathMatch<HttpHandler> match = pathMatcher.match(exchange.getRelativePath());

					// Proxy URLs bypass the Spring security filters, so the session ID must be checked here instead.
					boolean sessionMatch = true;
					
					if (match.getValue() instanceof ProxyHandler) {
						log.info("handleRequest: ProxyHandler=yes");
						sessionMatch = true; //proxyService.sessionOwnsProxy(exchange);						
						log.info("handleRequest: exchange.getRelativePath()=" + exchange.getRelativePath());
						String proxyName = exchange.getRelativePath().replace("/app/", "");
						if (proxyName.indexOf("/")> 0)
							proxyName = proxyName.substring(0, proxyName.indexOf("/"));
						log.info("handleRequest: proxyName=" + proxyName);
						App app = shinyAppServiceImpl.getApp(proxyName);
						log.info("handleRequest: app.getId()=" + ((app!=null)?app.getId():"null"));
						Authentication auth = SecurityContextHolder.getContext().getAuthentication();
						UserDetails userDetails = auth==null?null:(UserDetails) auth.getPrincipal();
						log.info(userDetails!=null?userDetails.getUsername():"No username");												
					}
					log.info("handleRequest sessionMatch: " +  ((Boolean)sessionMatch).toString());

					if (sessionMatch) {						
						super.handleRequest(exchange);
					} else {
						exchange.setStatusCode(401);
						exchange.getResponseChannel().write(ByteBuffer.wrap("No session ID found".getBytes()));
					}
					log.info("handleRequest finished: " + exchange.getRequestURI());					
				}
			};
			
			List<App> apps = shinyAppServiceImpl.getApps();
			
			for (App app: apps) {
				try {
					LoadBalancingProxyClient proxyClient = new LoadBalancingProxyClient();
					proxyClient.addHost(new URI(app.getMapping()));
					ProxyHandler proxyHandler = new ProxyHandler(proxyClient, ResponseCodeHandler.HANDLE_404);
					pathHandler.addPrefixPath("/"+ app.getName(), proxyHandler);
				} catch (URISyntaxException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			//RequestDumpingHandler debugHandler = new RequestDumpingHandler(pathHandler);
			
			proxyService.addMappingListener(new MappingListener() {
				@Override
				public void mappingAdded(String mapping, URI target) {
					/*log.info("mappingAdded started : mapping=" + mapping+ ", target="+ target);
					LoadBalancingProxyClient proxyClient = new LoadBalancingProxyClient();
					proxyClient.addHost(target);
					HttpHandler handler = new ProxyHandler(proxyClient, ResponseCodeHandler.HANDLE_404);
					pathHandler.addExactPath("/app/hello", handler);
					//pathHandler.addPrefixPath("/app", handler);
					log.info("mappingAdded finished : mapping=" + mapping+ ", target="+ target);*/
				}
				@Override
				public void mappingRemoved(String mapping) {
					/*log.info("mappingRemoved started : mapping=" + mapping);
					pathHandler.removePrefixPath(mapping);
					log.info("mappingRemoved finished : mapping=" + mapping);*/
				}
			});
						
			return pathHandler;// debugHandler;
		}
	}	
}
