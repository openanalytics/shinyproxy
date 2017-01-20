package eu.openanalytics.stats;

import java.io.IOException;
import java.util.function.Consumer;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import eu.openanalytics.services.EventService;
import eu.openanalytics.services.EventService.Event;
import eu.openanalytics.stats.impl.InfluxDBCollector;
import eu.openanalytics.stats.impl.MonetDBCollector;

@Component
public class StatCollectorRegistry implements Consumer<Event> {
	
	private Logger log = Logger.getLogger(StatCollectorRegistry.class);
	
	@Inject
	Environment environment;
	
	@Inject
	EventService eventService;
	
	private IStatCollector collector;
	
	@PostConstruct
	public void init() {
		String baseURL = environment.getProperty("shiny.proxy.usage-stats-url");
		collector = findCollector(baseURL);
		if (collector == null) {
			log.info("Disabled. Usage statistics will not be processed.");
		} else {
			eventService.addListener(this);
			log.info(String.format("Enabled. Sending usage statistics to %s", baseURL));
		}
	}
	
	@Override
	public void accept(Event event) {
		if (collector != null) {
			try {
				collector.accept(event, environment);
			} catch (IOException e) {
				log.error("Failed to submit usage statistic event", e);
			}
		}
	}
	
	private IStatCollector findCollector(String baseURL) {
		if (baseURL == null || baseURL.isEmpty()) return null;
		if (baseURL.toLowerCase().contains("/write?db=")) {
			return new InfluxDBCollector();
		} else if (baseURL.toLowerCase().startsWith("jdbc:monetdb")) {
			return new MonetDBCollector();
		}
		return null;
	}
}
