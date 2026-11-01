package org.pokesplash.hunt.api.event;

import org.pokesplash.hunt.Hunt;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * Class used to control a type of event.
 * @param <T> The type of event to control.
 */
public class Event<T> {
	private ArrayList<Subscription<T>> subscriptions = new ArrayList<>(); // A list of current subscriptions to
	// the event.

	/**
	 * Method used to subscribe to the event.
	 * @param callback
	 * @return
	 */
	public Subscription<T> subscribe(Consumer<T> callback) {
		Subscription<T> sub = new Subscription<>(this, callback);
		subscriptions.add(sub);
		return sub;
	}

	// Method used to unsubscribe from the event.
	public void unsubscribe(Subscription<T> subscription) {
		subscriptions.remove(subscription);
	}

	/**
	 * Triggers the event. A failing subscriber is logged and doesn't stop the other subscribers
	 * or the code that triggered the event.
	 */
	public void trigger(T t) {
		// Iterate over a copy, so a subscriber can unsubscribe while the event runs.
		for (Subscription<T> sub : new ArrayList<>(subscriptions)) {
			try {
				sub.run(t);
			} catch (Exception ex) {
				String type = t == null ? "null" : t.getClass().getSimpleName();
				Hunt.LOGGER.error("A subscriber to the hunt event " + type + " failed", ex);
			}
		}
	}
}