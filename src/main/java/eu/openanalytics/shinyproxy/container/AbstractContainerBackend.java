package eu.openanalytics.shinyproxy.container;

import java.io.File;
import java.util.function.BiConsumer;

import javax.inject.Inject;

import org.springframework.core.env.Environment;

import eu.openanalytics.shinyproxy.ShinyProxyException;

public abstract class AbstractContainerBackend implements IContainerBackend {

	@Inject
	protected Environment environment;
	
	@Override
	public void initialize() throws ShinyProxyException {
		// Default behavior: do nothing.	
	}
	
	@Override
	public BiConsumer<File, File> getOutputAttacher(IContainerProxy proxy) {
		// Default: do not support output attaching.
		return null;
	}
}
