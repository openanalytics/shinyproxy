package eu.openanalytics.shinyproxy.container.kubernetes;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

import eu.openanalytics.shinyproxy.ShinyProxyException;
import eu.openanalytics.shinyproxy.container.AbstractContainerBackend;
import eu.openanalytics.shinyproxy.container.ContainerProxyRequest;
import eu.openanalytics.shinyproxy.container.IContainerProxy;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

public class KubernetesBackend extends AbstractContainerBackend<KubernetesContainerProxy> {

	private static final String PROPERTY_PREFIX = "shiny.proxy.kubernetes.";
	
	private static final String PROPERTY_NAMESPACE = "namespace";
	private static final String PROPERTY_IMG_PULL_POLICY = "image-pull-policy";
	private static final String PROPERTY_IMG_PULL_SECRETS = "image-pull-secrets";
	private static final String PROPERTY_IMG_PULL_SECRET = "image-pull-secret";
	
	private static final String DEFAULT_NAMESPACE = "default";
	
	private static Logger log = Logger.getLogger(KubernetesBackend.class);
	
	private KubernetesClient kubeClient;
	
	@Override
	public void initialize() throws ShinyProxyException {
		ConfigBuilder configBuilder = new ConfigBuilder();
		String masterUrl = getProperty(PROPERTY_URL);
		if (masterUrl != null) configBuilder.withMasterUrl(masterUrl);
		kubeClient = new DefaultKubernetesClient(configBuilder.build());
	}

	@Override
	protected KubernetesContainerProxy instantiateProxy() {
		return new KubernetesContainerProxy();
	}
	
	@Override
	protected void prepareProxy(KubernetesContainerProxy proxy, ContainerProxyRequest request) throws Exception {
		proxy.setName(UUID.randomUUID().toString());
	}
	
	@Override
	protected void doStartProxy(KubernetesContainerProxy proxy) throws Exception {
		String kubeNamespace = getProperty(PROPERTY_NAMESPACE, proxy.getApp(), DEFAULT_NAMESPACE);
		
		String[] volumeStrings = Optional.ofNullable(proxy.getApp().getDockerVolumes()).orElse(new String[] {});
		Volume[] volumes = new Volume[volumeStrings.length];
		VolumeMount[] volumeMounts = new VolumeMount[volumeStrings.length];
		for (int i = 0; i < volumeStrings.length; i++) {
			String[] volume = volumeStrings[i].split(":");
			String hostSource = volume[0];
			String containerDest = volume[1];
			String name = "shinyproxy-volume-" + i;
			volumes[i] = new VolumeBuilder()
					.withNewHostPath(hostSource)
					.withName(name)
					.build();
			volumeMounts[i] = new VolumeMountBuilder()
					.withMountPath(containerDest)
					.withName(name)
					.build();
		}

		ContainerPortBuilder containerPortBuilder = new ContainerPortBuilder().withContainerPort(getAppPort(proxy));
		if (!isUseInternalNetwork()) {
			containerPortBuilder.withHostPort(proxy.getPort());
		}

		List<EnvVar> envVars = new ArrayList<>();
		for (String envString : buildEnv(proxy.getUserId(), proxy.getApp())) {
			int idx = envString.indexOf('=');
			if (idx == -1) log.warn("Invalid environment variable: " + envString);
			envVars.add(new EnvVar(envString.substring(0, idx), envString.substring(idx + 1), null));
		}
		
		ContainerBuilder containerBuilder = new ContainerBuilder()
				.withImage(proxy.getApp().getDockerImage())
				.withName("shiny-container")
				.withPorts(containerPortBuilder.build())
				.withEnv(envVars);

		String imagePullPolicy = getProperty(PROPERTY_IMG_PULL_POLICY, proxy.getApp(), null);
		if (imagePullPolicy != null) containerBuilder.withImagePullPolicy(imagePullPolicy);

		if (proxy.getApp().getDockerCmd() != null) containerBuilder.withCommand(proxy.getApp().getDockerCmd());

		String[] imagePullSecrets = getProperty(PROPERTY_IMG_PULL_SECRETS, proxy.getApp(), String[].class, null);
		if (imagePullSecrets == null) {
			String imagePullSecret = getProperty(PROPERTY_IMG_PULL_SECRET, proxy.getApp(), null);
			if (imagePullSecret != null) {
				imagePullSecrets = new String[] {imagePullSecret};
			} else {
				imagePullSecrets = new String[0];
			}
		}
		
		Pod pod = kubeClient.pods().inNamespace(kubeNamespace).createNew()
				.withApiVersion("v1")
				.withKind("Pod")
				.withNewMetadata()
				.withName(proxy.getName())
				.endMetadata()
				.withNewSpec()
				.withContainers(Collections.singletonList(containerBuilder.build()))
				.withVolumes(Arrays.asList(volumes))
				.withImagePullSecrets(Arrays.asList(imagePullSecrets).stream()
						.map(LocalObjectReference::new).collect(Collectors.toList()))
				.endSpec()
				.done();

		proxy.setContainerId(proxy.getName());
		proxy.setPod(kubeClient.resource(pod).waitUntilReady(20, TimeUnit.SECONDS));
	}

	@Override
	protected void calculateTargetURL(KubernetesContainerProxy proxy) throws Exception {
		String protocol = getProperty(PROPERTY_CONTAINER_PROTOCOL, null, DEFAULT_TARGET_PROTOCOL);
		String hostName = proxy.getPod().getStatus().getHostIP();
		if (isUseInternalNetwork()) {
			hostName = proxy.getPod().getStatus().getPodIP();
		}
		int port = proxy.getPort();
		
		String target = String.format("%s://%s:%d", protocol, hostName, port);
		proxy.setTarget(target);
	}
	
	@Override
	protected void doStopProxy(KubernetesContainerProxy proxy) throws Exception {
		kubeClient.pods().delete(getProxy(proxy).getPod());
	}
	
	@Override
	public BiConsumer<File, File> getOutputAttacher(IContainerProxy proxy) {
		//TODO
//		LogWatch watcher = kubeClient.pods().inNamespace(kubeNamespace).withName(proxy.name).watchLog();
		return super.getOutputAttacher(proxy);
	}

	@Override
	protected String getPropertyPrefix() {
		return PROPERTY_PREFIX;
	}
	
	@Override
	protected Logger getLog() {
		return log;
	}
	
	protected KubernetesContainerProxy getProxy(IContainerProxy proxy) {
		if (proxy instanceof KubernetesContainerProxy) return (KubernetesContainerProxy) proxy;
		else throw new IllegalArgumentException("Not a valid Kubernetes proxy: " + proxy);
	}
}
