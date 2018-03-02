package eu.openanalytics.shinyproxy.container.kubernetes;

import eu.openanalytics.shinyproxy.container.AbstractContainerProxy;
import io.fabric8.kubernetes.api.model.Pod;

public class KubernetesContainerProxy extends AbstractContainerProxy {

	private Pod pod;
	
	public Pod getPod() {
		return pod;
	}
	
	public void setPod(Pod pod) {
		this.pod = pod;
	}
}
