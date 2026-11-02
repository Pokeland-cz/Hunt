package org.pokesplash.hunt.config;

/**
 * A config or lang file exists but couldn't be parsed. The file must not be overwritten.
 */
public class ConfigLoadException extends Exception {
	private static final long serialVersionUID = 1L;

	public ConfigLoadException(String message) {
		super(message);
	}

	public ConfigLoadException(String message, Throwable cause) {
		super(message, cause);
	}
}
