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
package eu.openanalytics.shinyproxy.container.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
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
import io.fabric8.kubernetes.api.model.SecurityContext;
import io.fabric8.kubernetes.api.model.SecurityContextBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.LogWatch;

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
		
		String certPath = getProperty(PROPERTY_CERT_PATH);
		if (certPath != null && Files.isDirectory(Paths.get(certPath))) {
			Path certFilePath = Paths.get(certPath, "ca.pem");
			if (Files.exists(certFilePath)) configBuilder.withCaCertFile(certFilePath.toString());
			certFilePath = Paths.get(certPath, "cert.pem");
			if (Files.exists(certFilePath)) configBuilder.withClientCertFile(certFilePath.toString());
			certFilePath = Paths.get(certPath, "key.pem");
			if (Files.exists(certFilePath)) configBuilder.withClientKeyFile(certFilePath.toString());
		}
		
		kubeClient = new DefaultKubernetesClient(configBuilder.build());
	}

	@Override
	protected KubernetesContainerProxy instantiateProxy() {
		return new KubernetesContainerProxy();
	}
	
	@Override
	protected void prepareProxy(KubernetesContainerProxy proxy, ContainerProxyRequest request) throws Exception {
		proxy.setName(UUID.randomUUID().toString().replace("-", ""));
		proxy.setContainerId(proxy.getName());
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

		List<EnvVar> envVars = new ArrayList<>();
		for (String envString : buildEnv(proxy.getUserId(), proxy.getApp())) {
			int idx = envString.indexOf('=');
			if (idx == -1) log.warn("Invalid environment variable: " + envString);
			envVars.add(new EnvVar(envString.substring(0, idx), envString.substring(idx + 1), null));
		}
		
		SecurityContext security = new SecurityContextBuilder()
				.withPrivileged(Boolean.valueOf(getProperty(PROPERTY_PRIVILEGED, proxy.getApp(), DEFAULT_PRIVILEGED)))
				.build();
				
		ContainerBuilder containerBuilder = new ContainerBuilder()
				.withImage(proxy.getApp().getDockerImage())
				.withName("shiny-container")
				.withPorts(new ContainerPortBuilder().withContainerPort(getAppPort(proxy)).build())
				.withVolumeMounts(volumeMounts)
				.withSecurityContext(security)
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
					.addToLabels("app", proxy.getName())
					.endMetadata()
				.withNewSpec()
					.withContainers(Collections.singletonList(containerBuilder.build()))
					.withVolumes(volumes)
					.withImagePullSecrets(Arrays.asList(imagePullSecrets).stream()
						.map(LocalObjectReference::new).collect(Collectors.toList()))
					.endSpec()
				.done();
		proxy.setPod(kubeClient.resource(pod).waitUntilReady(600, TimeUnit.SECONDS));

		if (!isUseInternalNetwork()) {
			// If SP runs outside the cluster, a NodePort service is needed to access the pod externally.
			Service service = kubeClient.services().inNamespace(kubeNamespace).createNew()
					.withApiVersion("v1")
					.withKind("Service")
					.withNewMetadata()
						.withName(proxy.getName() + "service")
						.endMetadata()
					.withNewSpec()
						.addToSelector("app", proxy.getName())
						.withType("NodePort")
						.withPorts(new ServicePortBuilder()
								.withPort(getAppPort(proxy))
								.build())
						.endSpec()
					.done();
			proxy.setService(kubeClient.resource(service).waitUntilReady(600, TimeUnit.SECONDS));
			
			releasePort(proxy.getPort());
			proxy.setPort(proxy.getService().getSpec().getPorts().get(0).getNodePort());
		}
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
		kubeClient.services().delete(getProxy(proxy).getService());
	}
	
	@Override
	public BiConsumer<File, File> getOutputAttacher(IContainerProxy proxy) {
		return (stdOut, stdErr) -> {
			try {
				String namespace = getProperty(PROPERTY_NAMESPACE, proxy.getApp(), DEFAULT_NAMESPACE);
				LogWatch watcher = kubeClient.pods().inNamespace(namespace).withName(proxy.getName()).watchLog();
				IOUtils.copy(watcher.getOutput(), new FileOutputStream(stdOut));
			} catch (IOException e) {
				log.error("Error while attaching to container output", e);
			}
		};
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
