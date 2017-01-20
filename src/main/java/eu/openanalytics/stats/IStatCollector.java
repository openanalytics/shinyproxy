package eu.openanalytics.stats;

import java.io.IOException;

import org.springframework.core.env.Environment;

import eu.openanalytics.services.EventService.Event;

public interface IStatCollector {

	public void accept(Event event, Environment env) throws IOException;

}
