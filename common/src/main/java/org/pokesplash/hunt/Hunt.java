package org.pokesplash.hunt;

import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pokesplash.hunt.broadcast.Broadcaster;
import org.pokesplash.hunt.command.basecommand.HuntCommand;
import org.pokesplash.hunt.config.Config;
import org.pokesplash.hunt.config.Lang;
import org.pokesplash.hunt.event.CaptureEvent;
import org.pokesplash.hunt.hunts.CurrentHunts;
import org.pokesplash.hunt.hunts.HuntManager;
import org.pokesplash.hunt.hunts.HuntStorage;
import org.pokesplash.hunt.hunts.SingleHunt;
import org.pokesplash.hunt.hunts.SpawnRates;
import org.pokesplash.hunt.stats.HuntStats;
import org.pokesplash.hunt.util.CommandsRegistry;
import org.pokesplash.hunt.util.Permissions;

import java.util.UUID;

public class Hunt
{
	public static final String MOD_ID = "hunt";
	public static final String CONFIG_VERSION = "1.2.3";
	public static final String LANG_VERSION = "1.0.0";
	public static final Logger LOGGER = LogManager.getLogger("Hunt");
	public static Config config = new Config();
	public static final Permissions permissions = new Permissions();
	public static final Broadcaster broadcaster = new Broadcaster();
	public static SpawnRates spawnRates = new SpawnRates();
	public static CurrentHunts hunts = new CurrentHunts(null);
	public static HuntManager manager = new HuntManager();
	public static Lang language = new Lang();
	public static final HuntStorage storage = new HuntStorage();
	public static final HuntStats stats = new HuntStats();
	public static MinecraftServer server;
	// True once load() has run, so a failed startup never overwrites the saved hunts with empty data.
	private static boolean loaded = false;

	public static void init() {
		// Adds command to registry
		CommandsRegistry.addCommand(new HuntCommand());
		CaptureEvent.registerEvents();
	}

	/**
	 * Called when the server has started: loads configs, saved hunts and stats.
	 */
	public static void load() {
		loadConfigs();

		// Start from a clean state (statics survive between worlds in singleplayer), then restore saved hunts.
		hunts = new CurrentHunts(null);
		manager = new HuntManager();
		storage.load();
		stats.init();

		fillHunts();
		loaded = true;
	}

	/**
	 * Reloads the configs. Current hunts are kept, but trimmed or topped up to the configured amount.
	 */
	public static void reload() {
		loadConfigs();
		fillHunts();
	}

	/**
	 * Called when the server is stopping: saves hunts and stats.
	 */
	public static void stop() {
		if (!loaded) {
			return;
		}
		storage.saveNow();
		stats.saveNow();
		loaded = false;
	}

	private static void loadConfigs() {
		config.init();
		language.init();
		broadcaster.init();
		spawnRates.init();
	}

	/**
	 * Brings the hunts in line with the config: removes extras and generates missing ones.
	 * Hunts are only touched on the server thread, so no locking is needed.
	 */
	private static void fillHunts() {
		if (!config.isIndividualHunts()) {
			hunts.trimToLimit();
			hunts.init();
		}
		manager.init();
		storage.markDirty();
	}

	/**
	 * Replaces expired hunts. Must be called from the server thread.
	 */
	public static void check() {
		if (!loaded) {
			return;
		}
		if (config.isIndividualHunts()) {
			manager.checkOnlinePlayers();
		} else {
			hunts.getHuntValues().forEach(SingleHunt::check);
		}
		storage.saveIfDirty();
		stats.saveIfDirty();
	}

	/**
	 * Gets the hunts that apply to a player: their own hunts if individual hunts are enabled,
	 * otherwise the global hunts.
	 * @param player the player's UUID.
	 * @return the hunts for the player.
	 */
	public static CurrentHunts getHuntsFor(UUID player) {
		return config.isIndividualHunts() ? manager.getOrCreatePlayerHunts(player) : hunts;
	}
}
