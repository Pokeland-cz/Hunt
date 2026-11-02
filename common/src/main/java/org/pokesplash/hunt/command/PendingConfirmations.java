package org.pokesplash.hunt.command;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Destructive admin commands are run twice: first without "confirm" to see what would happen, then with
 * "confirm" within the time limit. Each source (a player, the console) has at most one pending action.
 * Only touched on the server thread.
 */
public final class PendingConfirmations {
	public static final long DEFAULT_TIMEOUT_MS = 30_000;

	private record Pending(String action, long expires) {
	}

	private final Map<String, Pending> pending = new HashMap<>();
	private final LongSupplier clock;
	private final long timeoutMs;

	public PendingConfirmations() {
		this(System::currentTimeMillis, DEFAULT_TIMEOUT_MS);
	}

	public PendingConfirmations(LongSupplier clock, long timeoutMs) {
		this.clock = clock;
		this.timeoutMs = timeoutMs;
	}

	/**
	 * Remembers an action the source may confirm, replacing any earlier one.
	 * @param source who ran the command.
	 * @param action what would be done, e.g. "reset:&lt;uuid&gt;".
	 */
	public void request(String source, String action) {
		pending.put(source, new Pending(action, clock.getAsLong() + timeoutMs));
	}

	/**
	 * Uses up the source's pending action if it's the same action and hasn't expired.
	 * @return true if the action may be done now.
	 */
	public boolean confirm(String source, String action) {
		Pending entry = pending.get(source);
		if (entry == null) {
			return false;
		}
		if (clock.getAsLong() > entry.expires()) {
			pending.remove(source);
			return false;
		}
		if (!entry.action().equals(action)) {
			return false;
		}
		pending.remove(source);
		return true;
	}

	public long timeoutSeconds() {
		return timeoutMs / 1000;
	}
}
