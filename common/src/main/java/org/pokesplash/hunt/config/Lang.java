package org.pokesplash.hunt.config;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.pokesplash.hunt.Hunt;
import org.pokesplash.hunt.old.LangOld;
import org.pokesplash.hunt.util.Utils;

import java.util.concurrent.CompletableFuture;

/**
 * Class for customization of language.
 */
public class Lang extends Versioned {
	private String title; // Title of the menu
	private String fillerMaterial; // The material used as the border in the UI.
	private String reloadMessage; // Reload message.
	private String captureHuntBroadcast; // Message broadcast to players when a hunt has been caught.
	private String payMessage; // Message sent when a player gets paid.
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
	private String topHeaderCompletions;
	private String topHeaderEarned;
	private String topEntry;
	private String topEmpty;

	public Lang() {
		super(Hunt.LANG_VERSION);
		title = "Hunt";
		fillerMaterial = "minecraft:white_stained_glass_pane";
		reloadMessage = "§aReloaded Configs!";
		captureHuntBroadcast = "§5[Hunt] §e{player} §ahas successfully hunted §e{pokemon}";
		payMessage = "§5[Hunt] §eYou were paid {price} from hunt!";
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
	 * Reads or creates the lang file.
	 */
	public void init() {
		CompletableFuture<Boolean> futureRead = Utils.readFileAsync("/config/hunt/", "lang.json",
				el -> {
					Gson gson = Utils.newGson();
					Versioned version = gson.fromJson(el, Versioned.class);

					if (version == null ||
							version.getVersion() == null ||
							!version.getVersion().equals(Hunt.LANG_VERSION)) {
						LangOld langOld = new LangOld();
						title = langOld.getTitle();
						fillerMaterial = langOld.getFillerMaterial();
						reloadMessage = langOld.getReloadMessage();
						captureHuntBroadcast = langOld.getCaptureHuntBroadcast();
						payMessage = langOld.getPayMessage();
						endedHuntMessage = langOld.getEndedHuntMessage();
						newHuntMessage = langOld.getNewHuntMessage();
						reward = langOld.getReward();
						timeRemaining = langOld.getTimeRemaining();
						write();
					}

					Lang lang = gson.fromJson(el, Lang.class);
					title = lang.getTitle();
					fillerMaterial = lang.getFillerMaterial();
					reloadMessage = lang.getReloadMessage();
					captureHuntBroadcast = lang.getCaptureHuntBroadcast();
					payMessage = lang.getPayMessage();
					endedHuntMessage = lang.getEndedHuntMessage();
					newHuntMessage = lang.getNewHuntMessage();
					reward = lang.getReward();
					timeRemaining = lang.getTimeRemaining();
					lore = lang.getLore();
					statsHeader = lang.getStatsHeader();
					statsCompletions = lang.getStatsCompletions();
					statsEarned = lang.getStatsEarned();
					statsRarities = lang.getStatsRarities();
					statsNotFound = lang.getStatsNotFound();
					topHeaderCompletions = lang.getTopHeaderCompletions();
					topHeaderEarned = lang.getTopHeaderEarned();
					topEntry = lang.getTopEntry();
					topEmpty = lang.getTopEmpty();

					// Older lang files don't have the stats messages yet, write them so they can be edited.
					if (!JsonParser.parseString(el).getAsJsonObject().has("statsHeader")) {
						write();
					}
				});

		if (!futureRead.join()) {
			Hunt.LOGGER.info("No lang.json file found for Hunt. Attempting to generate one.");

			boolean isSuccess = write();

			if (!isSuccess) {
				Hunt.LOGGER.fatal("Could not write lang.json file for Hunt.");
			}
			Hunt.LOGGER.info("Hunt lang file read successfully.");
		}
	}

	private boolean write() {
		Gson gson = Utils.newGson();
		String data = gson.toJson(this);
		CompletableFuture<Boolean> futureWrite = Utils.writeFileAsync("/config/hunt/", "lang.json",
				data);
		return futureWrite.join();
	}
}
