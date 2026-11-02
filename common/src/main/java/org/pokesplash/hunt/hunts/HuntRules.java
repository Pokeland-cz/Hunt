package org.pokesplash.hunt.hunts;

import java.util.Collection;
import java.util.Locale;

/**
 * Small rules for which Pokemon are hunted and what completes a hunt. Has no Minecraft dependencies,
 * so it can be tested.
 */
public final class HuntRules {
	private HuntRules() {
	}

	/**
	 * @param labels the form's labels (e.g. "legendary").
	 * @param excluded the configured excludedLabels, in lower case.
	 * @return true if the form has one of the excluded labels, ignoring upper and lower case (HUNT-022).
	 */
	public static boolean isExcluded(Collection<String> labels, Collection<String> excluded) {
		if (labels == null || excluded == null) {
			return false;
		}
		for (String label : labels) {
			if (label != null && excluded.contains(label.toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Hunt Pokemon are never shiny, so matchProperties.shiny means "only a shiny catch completes the hunt"
	 * (HUNT-021). Without it, shiny or not doesn't matter.
	 * @param shinyRequired matchProperties.shiny.
	 * @param caughtShiny whether the caught Pokemon is shiny.
	 */
	public static boolean shinyMatches(boolean shinyRequired, boolean caughtShiny) {
		return !shinyRequired || caughtShiny;
	}
}
