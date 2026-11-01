package org.pokesplash.hunt.util;

/**
 * What happened to one reward command, for the payout log.
 * @param template the command as written in the config.
 * @param command the command that was run (placeholders filled in), or the template if it wasn't run.
 * @param status whether the command ran, was skipped or failed.
 * @param result the value returned by the command, or 0 if it didn't run.
 */
public record CommandResult(String template, String command, Status status, int result) {

	public enum Status {
		RAN,
		SKIPPED_UNSAFE_NAME,
		FAILED
	}
}
