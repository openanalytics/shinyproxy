package eu.openanalytics.services;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.spotify.docker.client.LogStream;

import eu.openanalytics.services.DockerService.Proxy;

@Service
public class LogService {

	private boolean enabled;
	private String containerPath;

	private Logger log = Logger.getLogger(LogService.class);
	
	@Inject
	Environment environment;
	
	@PostConstruct
	public void init() {
		containerPath = environment.getProperty("shiny.proxy.support.container-log-store");
		try {
			Files.createDirectories(Paths.get(containerPath));
			enabled = true;
		} catch (IOException e) {
			log.error("Failed to initialize container logging directory at " + containerPath, e);
		}
	}
	
	public boolean isEnabled() {
		return enabled;
	}
	
	public void attachLogWriter(Proxy proxy, LogStream logStream) {
		if (!enabled) return;
		try {
			attachToFiles(proxy.containerId, logStream);
		} catch (IOException e) {
			log.error("Failed to attach logging of container " + proxy.containerId, e);
		}
	}
	
	private void attachToFiles(String containerId, LogStream logStream) throws IOException {
		Path stdOutPath = Paths.get(containerPath, containerId, "_stdout.log");
		Path stdErrPath = Paths.get(containerPath, containerId, "_stderr.log");
		logStream.attach(new FileOutputStream(stdOutPath.toFile()), new FileOutputStream(stdErrPath.toFile()));
	}
}
