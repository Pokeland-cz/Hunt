package org.pokesplash.hunt.util;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.permission.Permission;
import com.cobblemon.mod.common.api.permission.PermissionLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.function.Predicate;

/**
 * Class that deals with permission handling.
 */
public class Permissions {
	// Store all created permissions in a hashmap.
	private HashMap<String, Permission> permissions;

	/**
	 * Constructor to create a permission object. Add your permissions here using the private method.
	 */
	public Permissions() {
		permissions = new HashMap<>();
		// Add you permissions here.
		createPermission("HuntBase", "hunt.base", 0);
		createPermission("HuntNotify", "hunt.notify", 0);
		createPermission("HuntReload", "hunt.reload", 2);
		createPermission("HuntRefresh", "hunt.refresh", 2);
		createPermission("HuntDebug", "hunt.debug", 2);
		createPermission("HuntStats", "hunt.stats", 0);
		createPermission("HuntTop", "hunt.top", 0);
	}

	/**
	 * Method to add a new permission to the hashmap.
	 * @param name The reference of the permission.
	 * @param permissionNode The permission node.
	 * @param permissionLevel The permission level.
	 */
	private void createPermission(String name, String permissionNode, int permissionLevel) {
		permissions.put(name, new HuntPermission(permissionNode, parsePermission(permissionLevel)));
	}

	/**
	 * Method to fetch a permission from its reference.
	 * @param name The reference of the permission.
	 * @return
	 */
	public Permission getPermission(String name) {
		return permissions.get(name);
	}

	public boolean hasPermission(ServerPlayer player, Permission permission) {
		return Cobblemon.INSTANCE.getPermissionValidator().hasPermission(player, permission);
	}

	/**
	 * Checks a permission for whoever runs a command. A player is checked by their permissions
	 * (LuckPerms, or their OP level). Anything else (console, command block, "execute as" a non-player)
	 * needs the permission's OP level, so "execute as" a mob doesn't get around the permission.
	 */
	public boolean hasPermission(CommandSourceStack source, Permission permission) {
		return Cobblemon.INSTANCE.getPermissionValidator().hasPermission(source, permission);
	}

	/**
	 * A check for a command's requires(...), on whoever runs the command.
	 * @param name the reference of the permission.
	 * @throws IllegalArgumentException if there's no such permission, so a typo fails at registration.
	 */
	public Predicate<CommandSourceStack> requires(String name) {
		Permission permission = permissions.get(name);
		if (permission == null) {
			throw new IllegalArgumentException("Unknown Hunt permission: " + name);
		}
		return source -> hasPermission(source, permission);
	}

	private PermissionLevel parsePermission(int permLevel) {
		for (PermissionLevel value : PermissionLevel.values()) {
			if (value.ordinal() == permLevel) {
				return value;
			}
		}
		return PermissionLevel.CHEAT_COMMANDS_AND_COMMAND_BLOCKS;
	}
}
