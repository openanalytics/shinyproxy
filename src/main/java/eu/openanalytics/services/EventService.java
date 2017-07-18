/**
 * Copyright 2016 Open Analytics, Belgium
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
