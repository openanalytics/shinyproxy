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
