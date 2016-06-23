/**
 * Copyright 2016 Open Analytics, Belgium
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.openanalytics;

import java.net.URI;

import javax.inject.Inject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.embedded.EmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.undertow.UndertowBuilderCustomizer;
import org.springframework.boot.context.embedded.undertow.UndertowDeploymentInfoCustomizer;
import org.springframework.boot.context.embedded.undertow.UndertowEmbeddedServletContainerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.core.context.SecurityContext;

import eu.openanalytics.services.AppService;
import eu.openanalytics.services.DockerService;
import eu.openanalytics.services.DockerService.MappingListener;
import io.undertow.Handlers;
import io.undertow.Undertow.Builder;
import io.undertow.UndertowOptions;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.proxy.ProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.server.handlers.proxy.SimpleProxyClientProvider;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionListener;
import io.undertow.servlet.api.DeploymentInfo;

/**
 * @author Torkild U. Resheim, Itema AS
 */
@SpringBootApplication
@EnableAsync
@Configuration
public class ShinyProxyApplication {

	@Inject
	DockerService dockerService;

	@Inject
	AppService appService;
	
	@Inject
	Environment environment;

	public static void main(String[] args) {
		SpringApplication.run(new Class[] { ShinyProxyApplication.class }, args);
	}

	@Bean
	public EmbeddedServletContainerFactory servletContainer() {
		int port = Integer.parseInt(environment.getProperty("shiny.proxy.port", "8080"));
		int sessionTimeout = Integer.parseInt(environment.getProperty("shiny.proxy.session-timeout", "1800"));
		
		UndertowEmbeddedServletContainerFactory factory = new UndertowEmbeddedServletContainerFactory();
		factory.addDeploymentInfoCustomizers(new UndertowDeploymentInfoCustomizer() {
			@Override
			public void customize(DeploymentInfo deploymentInfo) {
				deploymentInfo.addInitialHandlerChainWrapper(new RootHandlerWrapper());
				deploymentInfo.addSessionListener(new SessionClosedListener());
			}
		});
		factory.addBuilderCustomizers(new UndertowBuilderCustomizer() {
			@Override
			public void customize(Builder builder) {
				builder.setServerOption(UndertowOptions.IDLE_TIMEOUT, sessionTimeout*1000);
			}
		});
		factory.setSessionTimeout(sessionTimeout);
		factory.setPort(port);
		return factory;	
	}

	private class RootHandlerWrapper implements HandlerWrapper {
		public HttpHandler wrap(HttpHandler defaultHandler) {
			PathHandler pathHandler = Handlers.path(defaultHandler);
			dockerService.addMappingListener(new MappingListener() {
				@Override
				public void mappingAdded(String mapping, URI target) {
					ProxyClient proxyClient = new SimpleProxyClientProvider(target);
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
	
	private class SessionClosedListener implements SessionListener {

		@Override
		public void sessionDestroyed(Session session, HttpServerExchange exchange, SessionDestroyedReason reason) {
			String userName = session.getId();
			SecurityContext ctx = (SecurityContext) session.getAttribute("SPRING_SECURITY_CONTEXT");
			if (ctx != null) userName = ctx.getAuthentication().getName();
			dockerService.releaseProxy(userName);
		}
		
		@Override
		public void sessionCreated(Session session, HttpServerExchange exchange) {
			// Do nothing
		}

		@Override
		public void attributeAdded(Session session, String name, Object value) {
			// Do nothing
		}

		@Override
		public void attributeUpdated(Session session, String name, Object newValue, Object oldValue) {
			// Do nothing
		}

		@Override
		public void attributeRemoved(Session session, String name, Object oldValue) {
			// Do nothing
		}

		@Override
		public void sessionIdChanged(Session session, String oldSessionId) {
			// Do nothing
		}
		
	}
}
