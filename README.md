
# Cobblemon Hunt

![GitHub](https://img.shields.io/github/license/PokeSplashDEV/Hunt)

A hunt mod for [Cobblemon](https://cobblemon.com/). Allows players to gain money from hunting specific Pokemon.
## Features

- Get paid for capturing Pokemon.
- Configurations for:
    - Hunt duration
    - Amount of Hunt Pokemon
    - Prices per rarity
    - Properties (Ability, Gender, Nature, Shiny)
    - Custom prices for specific Pokemon
- Regional and alternate forms (e.g. Alolan Vulpix) are hunted when they spawn in the world.
- Hunts are saved to `config/hunt/hunts.json` and survive restarts.
- Hunt stats and a leaderboard, saved to `config/hunt/stats.json`.
- Optional species icons from a resource pack instead of Pokemon models.

## Commands

`/hunts` is an alias of `/hunt` and works exactly the same, subcommands included.

| Command | Permission node | Default | Description |
| --- | --- | --- | --- |
| `/hunt` | `hunt.base` | everyone | Opens the hunt menu. Without this permission the menu doesn't open; the subcommands below are not affected. |
| `/hunt stats [player\|uuid]` | `hunt.stats` | everyone | Shows hunts completed, money earned and completions per rarity. Works for offline players and accepts a UUID (see *Finding a player*). |
| `/hunt top [completions\|earned]` | `hunt.top` | everyone | Shows the top 10 hunters. |
| `/hunt refresh` | `hunt.refresh` | OP 2 | Re-rolls all global hunts. |
| `/hunt refresh <player>` | `hunt.refresh` | OP 2 | Re-rolls a player's individual hunts. |
| `/hunt admin save` | `hunt.admin.save` | OP 2 | Writes `hunts.json` and `stats.json` now instead of waiting up to 30 seconds. While Hunt is paused only `stats.json` is written. |
| `/hunt admin stats reset <player\|uuid> [confirm]` | `hunt.admin.reset` | OP 2 | Deletes one player's stats (see *Resetting stats*). |
| `/hunt admin stats resetall [confirm]` | `hunt.admin.resetall` | OP 2 | Deletes the stats of all players, e.g. for a new leaderboard season (see *Resetting stats*). |
| `/hunt reload` | `hunt.reload` | OP 2 | Reloads the configs. Current hunts are kept and trimmed or topped up to the hunt amount. A file that can't be loaded is left untouched and the previous version stays in use (see *Broken config files*). |

`hunt.notify` (everyone) controls the individual-hunt messages.

Use the nodes as written above in LuckPerms, e.g. `lp group admin permission set hunt.reload true`. Without a permissions mod, or if a node isn't set, the OP level in the *Default* column applies. (Hunt 1.3.5 used `cobblemon.hunt.*` by mistake; since 1.3.6 the nodes are `hunt.*` again.)

Permissions are checked for whoever runs the command. Players are checked by their permissions. The console (OP 4) and command blocks (OP 2) can run every command. Anything else, such as `/execute as <mob> run hunt refresh`, needs the OP level from the *Default* column, so `/execute` can't be used to get around a permission.

### Finding a player

`/hunt stats` and `/hunt admin stats reset` take a name or a UUID. A name is matched in this order: an online player with exactly that name, then the last known name in the stats with exactly that name, then any of them ignoring upper and lower case. If several players match (e.g. `Steve` and `steve` on an offline-mode server, or a name used by two accounts), they are listed with their UUIDs (lang key `statsAmbiguous`); use the UUID then.

### Resetting stats

A reset only happens when the same command is run again with `confirm` within 30 seconds, by the same player (or from the console). The first run shows what would be deleted.
- `reset`: the player's stats are deleted (they also leave `/hunt top`) and `stats.json` is saved right away. The server log gets a WARN with who did it and the deleted stats as JSON.
- `resetall`: the current stats are first written to `config/hunt/stats-reset-<yyyyMMdd-HHmmss>.json` (same format as `stats.json`). If that copy can't be written, nothing is reset. Then all stats are deleted and `stats.json` is saved.
- To undo a reset, stop the server, put the record from the log (or the copy) back into `stats.json`, and start it again. Don't edit `stats.json` or `hunts.json` while the server runs: Hunt overwrites them with the data in memory.
- If `stats.json` couldn't be read at startup, resets are refused, because they couldn't be saved.

## Placeholders

Messages in `lang.json` and reward commands in `config.json` can use these placeholders:

| Placeholder | Value |
| --- | --- |
| `{player}` | The player's name. |
| `{uuid}` | The player's UUID. |
| `{pokemon}` | The Pokemon's display name. |
| `{price}` | The hunt's price, with at most two decimals and always a dot (`1.5`), whatever the server's language. |

Reward commands run as the server. **Use `{uuid}` instead of `{player}` wherever the command accepts an entity** (vanilla `give`, `effect`, `tp`, …), for example `give {uuid} minecraft:diamond 1`. For commands from other mods (e.g. economy commands), check first that they accept a UUID.

A command that contains `{player}` is skipped, with a warning in the log, if the player's name has characters other than `A-Z`, `a-z`, `0-9` and `_` or is longer than 16 characters. Such names can only come from offline-mode servers or proxies, and could otherwise be used to change the command (e.g. a player named `@a`). Commands without `{player}` still run.

## Notes

- **Blacklist:** an entry blocks a species and all its forms (`"Vulpix"`), or a single form with `Species-Form` (`"Vulpix-Alola"`).
- **Custom prices:** an entry with a `form` wins over an entry for the whole species.
- **Payout log:** `config/hunt/payouts.log` is append-only (never overwritten), one JSON object per line. Every completed hunt writes two lines with the same `completionId`:
  - `"phase":"PENDING"`, written and flushed to disk **before** anything is paid: `time` (UTC), `completionId`, `uuid`, `name`, `huntId`, `target` (`species/form`), `bucket`, `price` and `commands` (the command templates).
  - `"phase":"DONE"`, after the payout, with the same fields plus `economy`, `outcome`, `paid`, `error`, `caughtPokemon` (the caught Pokemon's UUID) and `commands` (each with `command`, `status` and `result`). The server log gets a `Hunt payout: …` line.
  - `outcome` is `PAID`, `DECLINED` (the economy refused), `ERROR` (the economy failed, see `error`) or `NO_PRICE`. A command's `status` is `RAN`, `SKIPPED_UNSAFE_NAME`, `SKIPPED_PAYMENT_FAILED` or `FAILED`. Lines from before 1.3.4 have no `phase` and are one line per completion.
  - To list payouts only (one line per completion, old lines included): `jq -c 'select(.outcome != null)' config/hunt/payouts.log`. Only `DONE` lines and lines from before 1.3.4 have `outcome`; `PENDING` lines and manually added `"resolved"` lines don't. A player's payouts: `jq -c 'select(.outcome != null and .name == "Steve")' config/hunt/payouts.log`.
- **Failed payments:** if the economy refuses or fails, the reward commands are **not** run (they are logged as `SKIPPED_PAYMENT_FAILED`) and the player gets `payFailedMessage` from `lang.json`. Hunts with no price still run their commands.
- **Unfinished payouts:** if the server stops between the `PENDING` and the `DONE` line (crash, `kill -9`), the server log shows an ERROR `Unfinished hunt payouts` with the `PENDING` line at every start. Nothing is paid again, because Hunt can't tell whether the money already arrived. Check the player's balance and the reward commands, compensate them if needed, then mark the payout as resolved by appending a `DONE` line with its `completionId`:
  `echo '{"phase":"DONE","completionId":"<completionId>","resolved":"manually"}' >> config/hunt/payouts.log`
- **Completed hunts stay completed:** `hunts.json` is saved right after a hunt is completed, before the payout. Saved hunts that `payouts.log` lists as completed are not restored at startup.
- **Broken config files:** Hunt never overwrites a file it couldn't read or parse.
  - `config.json` broken or unreadable **at startup**: Hunt is **paused**. There are no hunts and no rewards, and the server log shows an ERROR saying `Hunt is PAUSED` with the reason (for JSON errors, the line and column). Fix the file, then run `/hunt reload` to start Hunt. Saved hunts and stats are kept.
  - `config.json` broken, unreadable or missing **on `/hunt reload`**: the previous config stays in use, and the admin sees the reason in chat and in the log.
  - `lang.json` broken or unreadable: the default texts are used at startup and the previous texts on reload. Rewards are not affected.
  - A missing `config.json` or `lang.json` is only generated with the defaults at startup.
  - Older `config.json`/`lang.json` files are upgraded in place, keeping your values and texts. The original is kept as `*.bak.1`. A `config.json` that only lacks `"version"` keeps its economy and currency. Since 1.3.9 a config from Hunt 1.2.x (with `impactorCurrencyName` and no `economy`) is no longer migrated: Hunt pauses with an error that says so and the file is left alone. Replace `impactorCurrencyName` with `"economy": "IMPACTOR"` and `"currencyName"`, then restart or `/hunt reload`.
- **Config checks:** `config.json` is also checked for values that make no sense. All problems are listed at once.
  - **Errors** are treated like a broken file (paused at startup, previous config on reload): a missing or `null` `economy`, `rarity`, `rewards` or `customPrices`; a missing reward tier (`rewards.common`, `uncommon`, `rare`, `ultraRare`) or its `price`; a negative, `NaN` or infinite price (0 is allowed); `huntDuration` or `huntAmount` below 1; a negative rarity weight or all weights 0; a `customPrices` entry without `species` or `reward` (or without `reward.price`); an unknown `economy`; an empty `currencyName` when it is used (Blanket, or Impactor with `useImpactorDefaultCurrency: false`).
  - **Warnings** use a safe value and Hunt keeps running: `huntAmount` above 28 (28 is used), a negative `bufferDuration` (0), missing `commands` (none run), empty entries in `commands` or `blacklist` (ignored), a missing `matchProperties` or `blacklist` (defaults), a `customPrices` species and form listed twice (the first is used), an empty `packIconItem` (the default), a missing `version`, and species or forms in `customPrices` or `blacklist` that Cobblemon doesn't know (the entry does nothing, usually a typo).
  - Warnings are logged as WARN at startup and on reload, and `/hunt reload` also shows them in yellow in chat.
- **Data safety:** `hunts.json`, `stats.json`, `config.json` and `lang.json` are written atomically (temp file + move). Before a file is overwritten for the first time after a server start, the previous version is kept as `<file>.bak.1`, and older copies move up to `.bak.2` and `.bak.3`. So `.bak.1` is the file as it was at the last start. If `hunts.json` or `stats.json` exists but can't be read (e.g. wrong permissions), Hunt logs an ERROR and doesn't save that file until the next restart.
- **Icons:** `usePackIcons` (on by default) shows species icons from a resource pack that overrides `packIconItem` (default `minecraft:gray_dye`) with `custom_model_data` set to the National Pokedex number, and `0` as a fallback icon. Forms use the base species icon.


## Installation

Hunt is a server-side [Fabric](https://fabricmc.net/) mod for Minecraft 1.21.1 (dedicated servers only, it doesn't load in singleplayer). The NeoForge build was dropped in 1.3.9. It needs:

- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Cobblemon](https://modrinth.com/mod/cobblemon) 1.8.4 or a later 1.8.x (1.9 is refused until Hunt is checked against it)
- An economy: [Impactor](https://modrinth.com/mod/impactor) (default), Pebbles or Blanket, see `economy` in `config.json`
- [GooeyLibs](https://github.com/NickImpact/GooeyLibs) is bundled in the jar


## Screenshots

![Hunt Screen](https://i.imgur.com/Dncduqx.png)
![Hunt Pokemon](https://i.imgur.com/yEf6sBO.png)
## Documentation

[See the wiki.](https://github.com/PokeSplashDEV/Hunt/wiki)


## License

[MIT](https://choosealicense.com/licenses/mit/)


## Authors

- [@bencrow11](https://www.github.com/bencrow11)
- [@poqdavid](https://www.github.com/poqdavid)
