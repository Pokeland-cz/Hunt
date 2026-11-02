package org.pokesplash.hunt;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pokesplash.hunt.broadcast.Broadcaster;
import org.pokesplash.hunt.command.basecommand.HuntCommand;
import org.pokesplash.hunt.config.Config;
import org.pokesplash.hunt.config.ConfigLoadException;
import org.pokesplash.hunt.config.ConfigValidator;
import org.pokesplash.hunt.config.Lang;
import org.pokesplash.hunt.event.CaptureEvent;
import org.pokesplash.hunt.event.PayoutJournal;
import org.pokesplash.hunt.hunts.CurrentHunts;
import org.pokesplash.hunt.hunts.HuntManager;
import org.pokesplash.hunt.hunts.HuntStorage;
import org.pokesplash.hunt.hunts.SingleHunt;
import org.pokesplash.hunt.hunts.SpawnRates;
import org.pokesplash.hunt.stats.HuntStats;
import org.pokesplash.hunt.util.CommandsRegistry;
import org.pokesplash.hunt.util.FileStore;
import org.pokesplash.hunt.util.Permissions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
	// Hunts and stats are saved at most this often, and always on server stop.
	private static final long SAVE_INTERVAL_MS = 30_000;
	private static final String CONFIG_FILE = "config.json";
	private static final String LANG_FILE = "lang.json";
	// True once a config.json has been loaded in this run, so a failed reload can keep it.
	private static boolean configLoaded = false;
	private static long lastSave = 0;

	public static void init() {
		// Adds command to registry
		CommandsRegistry.addCommand(new HuntCommand());
		CaptureEvent.registerEvents();
	}

	/**
	 * Called when the server has started: loads configs, stats and saved hunts.
	 * If config.json can't be loaded, Hunt stays paused (no hunts, no rewards) until /hunt reload succeeds.
	 */
	public static void load() {
		loaded = false;
		LoadReport report = loadConfigs(true);
		stats.init();

		if (!report.configOk()) {
			LOGGER.error("==================================================================");
			LOGGER.error("Hunt is PAUSED: config.json could not be loaded. No hunts and no rewards");
			LOGGER.error("until it is fixed and /hunt reload is run. The file was NOT modified.");
			report.errors().forEach(error -> LOGGER.error("  " + error));
			LOGGER.error("==================================================================");
			return;
		}
		start();
	}

	/**
	 * Restores saved hunts and tops them up. Runs once the config is loaded.
	 */
	private static void start() {
		// Start from a clean state (statics survive between worlds in singleplayer), then restore saved hunts.
		hunts = new CurrentHunts(null);
		manager = new HuntManager();
		storage.load(scanPayoutJournal());

		fillHunts();
		loaded = true;
	}

	/**
	 * Reads payouts.log and reports payouts that were started but never finished (the server stopped
	 * during the payout). Nothing is paid again: whether the money arrived can't be known, so an admin
	 * has to check and compensate. Never fails the startup.
	 * @return ids of hunts that were already completed, so they aren't restored from hunts.json.
	 */
	private static Set<UUID> scanPayoutJournal() {
		try {
			PayoutJournal.Scan scan = PayoutJournal.DEFAULT.scan();
			if (scan.unreadable() > 0) {
				LOGGER.warn(scan.unreadable() + " lines in " + PayoutJournal.FILENAME + " could not be read and were skipped.");
			}
			if (!scan.incomplete().isEmpty()) {
				LOGGER.error("==================================================================");
				LOGGER.error("Unfinished hunt payouts: " + scan.incomplete().size() + " (the server stopped during a payout).");
				LOGGER.error("Nothing was paid again. Check the player's balance and reward commands,");
				LOGGER.error("compensate if needed, then see 'Unfinished payouts' in the README to clear this report.");
				scan.incomplete().values().forEach(line -> LOGGER.error("  " + line));
				LOGGER.error("==================================================================");
			}
			return scan.claimedHuntIds();
		} catch (Exception ex) {
			LOGGER.error("Could not read " + PayoutJournal.FILENAME + " to check for unfinished payouts.", ex);
			return Set.of();
		}
	}

	/**
	 * Reloads the configs. A file that can't be loaded is left as it is and the previous version stays
	 * in use. Current hunts are kept, but trimmed or topped up to the configured amount. If Hunt was
	 * paused and the config now loads, Hunt starts.
	 * @return what happened, for the admin.
	 */
	public static LoadReport reload() {
		LoadReport report = loadConfigs(false);
		if (loaded) {
			fillHunts();
		} else if (report.configOk()) {
			start();
			LOGGER.info("config.json loaded, Hunt started.");
			return new LoadReport(true, true, report.errors(), report.warnings());
		}
		return report;
	}

	/**
	 * @return false while Hunt is paused (config.json couldn't be loaded at startup) or not started yet.
	 */
	public static boolean isActive() {
		return loaded;
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

	/**
	 * What a config load did.
	 * @param configOk config.json is loaded (now or from before), so Hunt can run.
	 * @param started Hunt was paused and has just started.
	 * @param errors messages for the admin about files that weren't loaded.
	 * @param warnings messages for the admin about config values that were replaced or do nothing.
	 */
	public record LoadReport(boolean configOk, boolean started, List<String> errors, List<String> warnings) {
	}

	private static LoadReport loadConfigs(boolean startup) {
		List<String> errors = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		boolean configOk = loadConfig(startup, errors, warnings);
		loadLang(startup, errors);
		broadcaster.init();
		spawnRates.init();
		return new LoadReport(configOk, false, errors, warnings);
	}

	/**
	 * Loads config.json. Never overwrites a file that couldn't be read or parsed.
	 * @return true if a config is in use: the file's, the default one (first start), or the previous one (reload).
	 */
	private static boolean loadConfig(boolean startup, List<String> errors, List<String> warnings) {
		// On a reload, a config that was loaded before stays in use if the file can't be loaded.
		boolean keepPrevious = !startup && configLoaded;
		switch (FileStore.read(CONFIG_FILE)) {
			case FileStore.Missing missing -> {
				if (startup) {
					LOGGER.info("No " + CONFIG_FILE + " file found for Hunt, generating one.");
					config = new Config();
					if (!FileStore.writeSync(CONFIG_FILE, config.toJson())) {
						LOGGER.fatal("Could not write " + CONFIG_FILE + " for Hunt.");
					}
					configLoaded = true;
					return true;
				}
				return fail(errors, CONFIG_FILE + " is missing.", keepPrevious);
			}
			case FileStore.Failed failed -> {
				return fail(errors, CONFIG_FILE + " could not be read: " + failed.error(), keepPrevious);
			}
			case FileStore.Ok ok -> {
				try {
					Config parsed = Config.parse(ok.content());
					List<String> problems = new ArrayList<>(parsed.getWarnings());
					problems.addAll(checkSpecies(parsed));
					for (String problem : problems) {
						String message = CONFIG_FILE + ": " + problem;
						LOGGER.warn(message);
						warnings.add(message);
					}
					config = parsed;
					configLoaded = true;
					if (parsed.isUpgraded() && !FileStore.writeSync(CONFIG_FILE, parsed.toJson())) {
						LOGGER.error("Could not write the upgraded " + CONFIG_FILE + ".");
					}
					LOGGER.info("Hunt config file read successfully.");
					return true;
				} catch (ConfigLoadException e) {
					return fail(errors, CONFIG_FILE + " is not valid: " + e.getMessage(), keepPrevious);
				}
			}
		}
	}

	/**
	 * Finds customPrices and blacklist entries for species or forms that Cobblemon doesn't know.
	 */
	private static List<String> checkSpecies(Config cfg) {
		Map<String, Set<String>> catalog = new HashMap<>();
		for (Species species : PokemonSpecies.getSpecies()) {
			Set<String> forms = new HashSet<>();
			forms.add(species.getStandardForm().getName().toLowerCase(Locale.ROOT));
			for (FormData form : species.getForms()) {
				forms.add(form.getName().toLowerCase(Locale.ROOT));
			}
			catalog.put(species.getName().toLowerCase(Locale.ROOT), forms);
		}
		if (catalog.isEmpty()) {
			LOGGER.warn("Cobblemon has no species loaded yet, so the species in customPrices and blacklist weren't checked.");
			return List.of();
		}
		return ConfigValidator.checkSpecies(cfg, catalog);
	}

	/**
	 * Loads lang.json. Never overwrites a file that couldn't be read or parsed: at startup the default
	 * texts are used instead, on a reload the previous texts stay.
	 */
	private static void loadLang(boolean startup, List<String> errors) {
		switch (FileStore.read(LANG_FILE)) {
			case FileStore.Missing missing -> {
				if (startup) {
					LOGGER.info("No " + LANG_FILE + " file found for Hunt, generating one.");
					language = new Lang();
					if (!FileStore.writeSync(LANG_FILE, language.toJson())) {
						LOGGER.fatal("Could not write " + LANG_FILE + " for Hunt.");
					}
				} else {
					langError(errors, LANG_FILE + " is missing.", startup);
				}
			}
			case FileStore.Failed failed ->
					langError(errors, LANG_FILE + " could not be read: " + failed.error(), startup);
			case FileStore.Ok ok -> {
				try {
					Lang parsed = Lang.parse(ok.content());
					language = parsed;
					if (parsed.isUpgraded() && !FileStore.writeSync(LANG_FILE, parsed.toJson())) {
						LOGGER.error("Could not write the upgraded " + LANG_FILE + ".");
					}
					LOGGER.info("Hunt lang file read successfully.");
				} catch (ConfigLoadException e) {
					langError(errors, LANG_FILE + " is not valid: " + e.getMessage(), startup);
				}
			}
		}
	}

	private static boolean fail(List<String> errors, String problem, boolean keepPrevious) {
		String message = problem + (keepPrevious
				? " The previous config is still in use. The file was not modified."
				: " Hunt is paused. The file was not modified.");
		LOGGER.error(message);
		errors.add(message);
		return keepPrevious;
	}

	private static void langError(List<String> errors, String problem, boolean startup) {
		if (startup) {
			language = new Lang();
		}
		String message = problem + (startup
				? " Using the default texts. The file was not modified."
				: " The previous texts are still in use. The file was not modified.");
		LOGGER.error(message);
		errors.add(message);
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
		// Runs in the server tick: every step is guarded, so a broken hunt or config can't crash the server.
		// Missing hunts aren't topped up here (a broken config would spam the log every second);
		// /hunt reload does that once the config is fixed.
		if (config.isIndividualHunts()) {
			try {
				manager.checkOnlinePlayers();
			} catch (Exception ex) {
				LOGGER.error("Failed to check individual hunts", ex);
			}
		} else {
			for (SingleHunt hunt : hunts.getHuntValues()) {
				try {
					hunt.check();
				} catch (Exception ex) {
					LOGGER.error("Failed to check hunt " + hunt.getId(), ex);
				}
			}
		}
		long now = System.currentTimeMillis();
		if (now - lastSave >= SAVE_INTERVAL_MS) {
			lastSave = now;
			try {
				storage.saveIfDirty();
			} catch (Exception ex) {
				LOGGER.error("Failed to save hunts", ex);
			}
			try {
				stats.saveIfDirty();
			} catch (Exception ex) {
				LOGGER.error("Failed to save hunt stats", ex);
			}
		}
	}

	/**
	 * Called when a player joins. Gives them their hunts if individual hunts are enabled.
	 * @param player the player's UUID.
	 */
	public static void onPlayerJoin(UUID player) {
		try {
			manager.addPlayer(player);
		} catch (Exception ex) {
			LOGGER.error("Failed to set up hunts for player " + player, ex);
		}
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
