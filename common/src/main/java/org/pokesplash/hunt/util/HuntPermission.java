package org.pokesplash.hunt.util;

import com.cobblemon.mod.common.api.permission.Permission;
import com.cobblemon.mod.common.api.permission.PermissionLevel;
import net.minecraft.resources.ResourceLocation;

/**
 * A Hunt permission whose node is used as written, e.g. "hunt.reload". Cobblemon's own CobblemonPermission
 * would turn it into "cobblemon.hunt.reload" (HUNT-033).
 */
public final class HuntPermission implements Permission {
	private final String node;
	private final PermissionLevel level;
	private final ResourceLocation identifier;

	/**
	 * @param node the permission node, e.g. "hunt.reload".
	 * @param level the OP level needed when no permissions mod decides.
	 */
	public HuntPermission(String node, PermissionLevel level) {
		this.node = node;
		this.level = level;
		this.identifier = ResourceLocation.fromNamespaceAndPath("hunt", node);
	}

	@Override
	public ResourceLocation getIdentifier() {
		return identifier;
	}

	@Override
	public String getLiteral() {
		return node;
	}

	@Override
	public PermissionLevel getLevel() {
		return level;
	}

	@Override
	public String toString() {
		return node + " (OP " + level.getNumericalValue() + ")";
	}
}
