package org.pokesplash.hunt.old;

/**
 * Class for customization of language.
 */
public class LangOld {
	private String title; // Title of the menu
	private String fillerMaterial; // The material used as the border in the UI.
	private String reloadMessage; // Reload message.
	private String captureHuntBroadcast; // Message broadcast to players when a hunt has been caught.
	private String payMessage; // Message sent when a player gets paid.
	private String endedHuntMessage; // The message sent when a hunt ends.
	private String newHuntMessage; // The message sent when a new hunt begins.
	private String reward;
	private String timeRemaining;

	public LangOld() {
		title = "Hunt";
		fillerMaterial = "minecraft:white_stained_glass_pane";
		reloadMessage = "§aReloaded Configs!";
		captureHuntBroadcast = "§5[Hunt] §e{player} §ahas successfully hunted §e{pokemon}";
		payMessage = "§5[Hunt] §eYou were paid {price} from hunt!";
		endedHuntMessage = "§5[Hunt] §3The hunt for {pokemon} has ended!";
		newHuntMessage = "§5[Hunt] §bThe hunt for {pokemon} has begun!";
		reward = "§6Reward: §e";
		timeRemaining = "§9Time Remaining: §b";
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
}
