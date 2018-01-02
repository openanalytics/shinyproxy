package eu.openanalytics.shinyproxy.container;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;

import eu.openanalytics.shinyproxy.container.docker.DockerEngineBackend;
import eu.openanalytics.shinyproxy.container.docker.DockerSwarmBackend;

@Service
public class ContainerBackendFactory extends AbstractFactoryBean<IContainerBackend> implements ApplicationContextAware {
	
	private ApplicationContext applicationContext;
	
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
	
	@Override
	public Class<?> getObjectType() {
		return IContainerBackend.class;
	}

	@Override
	protected IContainerBackend createInstance() throws Exception {
		try {
			return tryCreate(DockerSwarmBackend.class);
		} catch (Exception e) {
			// Fall back to next backend type.
		}
		return tryCreate(DockerEngineBackend.class);
	}

	private IContainerBackend tryCreate(Class<? extends IContainerBackend> backendClass) throws Exception {
		IContainerBackend backend = backendClass.newInstance();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(backend);
		backend.initialize();
		return backend;
	}
}
