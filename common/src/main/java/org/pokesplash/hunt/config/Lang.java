package org.pokesplash.hunt.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.pokesplash.hunt.Hunt;
import org.pokesplash.hunt.util.Utils;

/**
 * Class for customization of language.
 */
public class Lang extends Versioned {
	private String title; // Title of the menu
	private String fillerMaterial; // The material used as the border in the UI.
	private String reloadMessage; // Reload message.
	private String captureHuntBroadcast; // Message broadcast to players when a hunt has been caught.
	private String payMessage; // Message sent when a player gets paid.
	private String payFailedMessage; // Message sent when the economy couldn't pay a hunt reward.
	private String endedHuntMessage; // The message sent when a hunt ends.
	private String newHuntMessage; // The message sent when a new hunt begins.
	private String reward;
	private String timeRemaining;
	private Lore lore;
	private String statsHeader;
	private String statsCompletions;
	private String statsEarned;
	private String statsRarities;
	private String statsNotFound;
	private String statsAmbiguous;
	private String topHeaderCompletions;
	private String topHeaderEarned;
	private String topEntry;
	private String topEmpty;
	private transient boolean upgraded; // Set by parse() if the file should be rewritten in the new format.

	public Lang() {
		super(Hunt.LANG_VERSION);
		title = "Hunt";
		fillerMaterial = "minecraft:white_stained_glass_pane";
		reloadMessage = "§aReloaded Configs!";
		captureHuntBroadcast = "§5[Hunt] §e{player} §ahas successfully hunted §e{pokemon}";
		payMessage = "§5[Hunt] §eYou were paid {price} from hunt!";
		payFailedMessage = "§5[Hunt] §cYour hunt reward could not be paid. Please contact an admin.";
		endedHuntMessage = "§5[Hunt] §3The hunt for {pokemon} has ended!";
		newHuntMessage = "§5[Hunt] §bThe hunt for {pokemon} has begun!";
		reward = "§6Reward: §e";
		timeRemaining = "§9Time Remaining: §b";
		lore = new Lore();
		statsHeader = "§5[Hunt] §eHunt stats for §6{player}§e:";
		statsCompletions = "§7Hunts completed: §a{completions}";
		statsEarned = "§7Money earned: §a{earned}";
		statsRarities = "§7Common: §f{common} §7| Uncommon: §f{uncommon} §7| Rare: §f{rare} §7| Ultra Rare: §f{ultra_rare}";
		statsNotFound = "§5[Hunt] §cNo hunt stats found for {player}.";
		statsAmbiguous = "§5[Hunt] §e{player} §7matches several players: {matches}§7. Use the UUID: /hunt stats <uuid>";
		topHeaderCompletions = "§5[Hunt] §eTop hunters by hunts completed:";
		topHeaderEarned = "§5[Hunt] §eTop hunters by money earned:";
		topEntry = "§6#{rank} §e{player} §7- §a{completions} §7hunts, §a{earned} §7earned";
		topEmpty = "§5[Hunt] §7Nobody has completed a hunt yet.";
	}

	public String getTitle() {
		return title;
	}
	public String getFillerMaterial() {
		return fillerMaterial;
	}
	public String getReloadMessage() {
		return reloadMessage;
	}
	public String getCaptureHuntBroadcast() {
		return captureHuntBroadcast;
	}
	public String getPayMessage() {
		return payMessage;
	}
	public String getPayFailedMessage() {
		return payFailedMessage;
	}
	public String getEndedHuntMessage() {
		return endedHuntMessage;
	}
	public String getNewHuntMessage() {
		return newHuntMessage;
	}
	public String getReward() {
		return reward;
	}
	public String getTimeRemaining() {
		return timeRemaining;
	}
	public String getStatsHeader() {
		return statsHeader;
	}
	public String getStatsCompletions() {
		return statsCompletions;
	}
	public String getStatsEarned() {
		return statsEarned;
	}
	public String getStatsRarities() {
		return statsRarities;
	}
	public String getStatsNotFound() {
		return statsNotFound;
	}
	public String getStatsAmbiguous() {
		return statsAmbiguous;
	}
	public String getTopHeaderCompletions() {
		return topHeaderCompletions;
	}
	public String getTopHeaderEarned() {
		return topHeaderEarned;
	}
	public String getTopEntry() {
		return topEntry;
	}
	public String getTopEmpty() {
		return topEmpty;
	}
	public Lore getLore() {
		return lore;
	}

	/**
	 * Parses the contents of lang.json into a new Lang. Texts from the file are kept, keys the file
	 * doesn't have yet get their default text. Nothing is written here.
	 * @param json the contents of lang.json.
	 * @return the lang; {@link #isUpgraded()} tells whether the file should be rewritten in the new format.
	 * @throws ConfigLoadException if the file can't be parsed.
	 */
	public static Lang parse(String json) throws ConfigLoadException {
		JsonObject root;
		Lang lang;
		try {
			JsonElement element = JsonParser.parseString(json);
			if (!element.isJsonObject()) {
				throw new ConfigLoadException("the file is empty or is not a JSON object");
			}
			root = element.getAsJsonObject();
			lang = Utils.newGson().fromJson(root, Lang.class);
		} catch (JsonParseException | IllegalStateException | NumberFormatException e) {
			throw new ConfigLoadException(e.getMessage(), e);
		}

		// Migrates from what was read: the file's texts stay, only the version and new keys change.
		JsonElement version = root.get("version");
		boolean currentVersion = version != null && version.isJsonPrimitive()
				&& Hunt.LANG_VERSION.equals(version.getAsString());
		if (!currentVersion) {
			lang.setVersion(Hunt.LANG_VERSION);
			lang.upgraded = true;
		}

		// Older lang files don't have the stats messages yet, write them so they can be edited.
		if (!root.has("statsHeader")) {
			lang.upgraded = true;
		}
		// Added in 1.3.4.
		if (!root.has("payFailedMessage")) {
			lang.upgraded = true;
		}
		// Added in 1.3.8.
		if (!root.has("statsAmbiguous")) {
			lang.upgraded = true;
		}
		return lang;
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
}
