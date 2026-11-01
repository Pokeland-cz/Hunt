package org.pokesplash.hunt.hunts;

import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Species;

/**
 * A species and form that can be hunted, e.g. Vulpix or Alolan Vulpix.
 */
public record HuntTarget(Species species, FormData form) {

	/**
	 * A stable identifier for this target, e.g. "cobblemon:vulpix/alola".
	 */
	public String key() {
		return key(species, form);
	}

	public static String key(Species species, FormData form) {
		return species.getResourceIdentifier() + "/" + form.getName().toLowerCase();
	}
}
