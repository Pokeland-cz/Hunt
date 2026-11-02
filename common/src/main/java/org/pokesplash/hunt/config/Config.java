package org.pokesplash.hunt.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.pokesplash.hunt.Hunt;
import org.pokesplash.hunt.enumeration.Economy;
import org.pokesplash.hunt.old.ConfigOld;
import org.pokesplash.hunt.util.Utils;

import java.util.ArrayList;

/**
 * Config file.
 */
public class Config extends Versioned {
	private Economy economy; // The economy mod to use. Options are Impactor, Pebbles, Blanket.
	private boolean useImpactorDefaultCurrency; // Should Hunt use Impactor's default currency.
	private String currencyName; // The name of the currency that should be used.
	private boolean individualHunts; // if hunts should be individual for each player.
	private boolean sendHuntEndMessage; // Should the mod send a message when a hunt ends.
	private boolean sendHuntBeginMessage; // Should the mod send a message when a hunt begins.
	private boolean timerCooldowns; // Should the mod wait before sending the broadcasts.
	private int bufferDuration; // The duration the buffer should wait to collect broadcasts.
	private int huntDuration; // How long each hunt should last, in minutes.
	private int huntAmount; // How many hunts should there be at once.
	private RarityConfig rarity; // The rarity borders.
	private RewardsConfig rewards; // The rewards for the hunts.
	private Properties matchProperties; // What properties should be checked to complete the hunt.
	private ArrayList<CustomPrice> customPrices; // List of custom prices.
	private ArrayList<String> blacklist; // List if Pokemon that shouldn't be added to Hunt.
	private boolean usePackIcons; // Show species icons from a resource pack instead of Pokemon models in /hunt.
	private String packIconItem; // The item the resource pack overrides with custom_model_data = dex number.
	private transient boolean upgraded; // Set by parse() if the file should be rewritten in the new format.

	public Config() {
		super(Hunt.CONFIG_VERSION);
		economy = Economy.IMPACTOR;
		useImpactorDefaultCurrency = true;
		currencyName = "impactor:huntcoins";
		individualHunts = false;
		sendHuntEndMessage = true;
		sendHuntBeginMessage = true;
		timerCooldowns = true;
		bufferDuration = 5;
		huntDuration = 60;
		huntAmount = 7;
		rarity = new RarityConfig();
		rewards = new RewardsConfig();
		matchProperties = new Properties();
		customPrices = new ArrayList<>();
		customPrices.add(new CustomPrice());
		blacklist = new ArrayList<>();
		usePackIcons = true;
		packIconItem = "minecraft:gray_dye";
	}

	/**
	 * Parses the contents of config.json into a new Config. Nothing is written here: the caller
	 * decides whether the file is (re)written, so a file that can't be parsed is never overwritten.
	 * @param json the contents of config.json.
	 * @return the config; {@link #isUpgraded()} tells whether the file should be rewritten in the new format.
	 * @throws ConfigLoadException if the file can't be parsed.
	 */
	public static Config parse(String json) throws ConfigLoadException {
		Gson gson = Utils.newGson();
		JsonObject root;
		Config cfg;
		try {
			JsonElement element = JsonParser.parseString(json);
			if (!element.isJsonObject()) {
				throw new ConfigLoadException("the file is empty or is not a JSON object");
			}
			root = element.getAsJsonObject();
			cfg = gson.fromJson(root, Config.class);
		} catch (JsonParseException | IllegalStateException | NumberFormatException e) {
			throw new ConfigLoadException(e.getMessage(), e);
		}

		requireSection(cfg.economy, "economy");
		requireSection(cfg.rarity, "rarity");
		requireSection(cfg.rewards, "rewards");
		requireSection(cfg.matchProperties, "matchProperties");
		requireSection(cfg.customPrices, "customPrices");
		requireSection(cfg.blacklist, "blacklist");

		if (cfg.huntAmount > 28) {
			cfg.huntAmount = 28;
			Hunt.LOGGER.error("Hunt amount can not be higher than 28");
		}
		if (cfg.packIconItem == null || cfg.packIconItem.isBlank()) {
			cfg.packIconItem = "minecraft:gray_dye";
		}

		// Older configs don't have the icon options yet, write them so they are visible.
		if (!root.has("usePackIcons")) {
			cfg.upgraded = true;
		}

		// The version comes from the file itself: the constructor fills in the current one for a missing key.
		JsonElement version = root.get("version");
		boolean currentVersion = version != null && version.isJsonPrimitive()
				&& Hunt.CONFIG_VERSION.equals(version.getAsString());
		if (!currentVersion) {
			ConfigOld cfgOld;
			try {
				cfgOld = gson.fromJson(root, ConfigOld.class);
			} catch (JsonParseException | IllegalStateException | NumberFormatException e) {
				throw new ConfigLoadException(e.getMessage(), e);
			}
			cfg.economy = Economy.IMPACTOR;
			if (cfgOld.getImpactorCurrencyName() != null) {
				cfg.currencyName = cfgOld.getImpactorCurrencyName();
			}
			cfg.setVersion(Hunt.CONFIG_VERSION);
			cfg.upgraded = true;
		}
		return cfg;
	}

	private static void requireSection(Object value, String key) throws ConfigLoadException {
		if (value == null) {
			throw new ConfigLoadException("\"" + key + "\" is missing or null");
		}
	}

	/**
	 * @return true if the file was in an older format and should be rewritten.
	 */
	public boolean isUpgraded() {
		return upgraded;
	}

	public String toJson() {
		return Utils.newGson().toJson(this);
	}

	public boolean isUsePackIcons() {
		return usePackIcons;
	}

	public String getPackIconItem() {
		return packIconItem;
	}

	public int getBufferDuration() {
		return bufferDuration;
	}

	public boolean isTimerCooldowns() {
		return timerCooldowns;
	}

	public boolean isIndividualHunts() {
		return individualHunts;
	}

	public int getHuntDuration() {
		return huntDuration;
	}

	public int getHuntAmount() {
		return huntAmount;
	}


	public Properties getMatchProperties() {
		return matchProperties;
	}

	public ArrayList<CustomPrice> getCustomPrices() {
		return customPrices;
	}

	public boolean isSendHuntEndMessage() {
		return sendHuntEndMessage;
	}

	public boolean isSendHuntBeginMessage() {
		return sendHuntBeginMessage;
	}

	public ArrayList<String> getBlacklist() {
		return blacklist;
	}

	public RarityConfig getRarity() {
		return rarity;
	}

	public RewardsConfig getRewards() {
		return rewards;
	}

	public boolean blacklistContains(String pokemon) {
		for (String name : blacklist) {
			if (name.equalsIgnoreCase(pokemon)) return true;
		}
		return false;
	}

	public boolean isUseImpactorDefaultCurrency() {
		return useImpactorDefaultCurrency;
	}

	public String getCurrencyName() {
		return currencyName;
	}

	public Economy getEconomy() {
		return economy;
	}
}
