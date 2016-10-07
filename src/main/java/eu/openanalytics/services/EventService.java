package eu.openanalytics.services;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

@Service
public class EventService {

	private List<Consumer<Event>> listeners = new CopyOnWriteArrayList<>();
	
	public void post(String type, String user, String data) {
		post(new Event(type, user, System.currentTimeMillis(), data));
	}
	
	public void post(Event event) {
		for (Consumer<Event> listener: listeners) {
			listener.accept(event);
		}
	}
	
	public void addListener(Consumer<Event> listener) {
		listeners.add(listener);
	}
	
	public void removeListener(Consumer<Event> listener) {
		listeners.remove(listener);
	}
	
	public static class Event {
		
		public String type;
		public String user;
		public long timestamp;
		public String data;
	
		public Event() {
			// Default constructor.
		}
		
		public Event(String type, String user, long timestamp, String data) {
			this.type = type;
			this.user = user;
			this.timestamp = timestamp;
			this.data = data;
		}
	}
	
	public enum EventType {
		Login,
		Logout,
		AppStart,
		AppStop
	}
}
