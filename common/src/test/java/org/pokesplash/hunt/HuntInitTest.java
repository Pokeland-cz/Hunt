package org.pokesplash.hunt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Hunt's static state (config, lang, permissions, broadcaster...) must load without a running server,
 * because Config, Lang and FileStore log through Hunt.LOGGER.
 */
class HuntInitTest {
	@Test
	void staticStateLoadsWithoutServer() {
		assertNotNull(Hunt.LOGGER);
		assertNotNull(Hunt.config);
		assertNotNull(Hunt.language);
	}
}
