
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

| Command | Permission | Description |
| --- | --- | --- |
| `/hunt` | `hunt.base` | Opens the hunt menu. |
| `/hunt stats [player]` | `hunt.stats` | Shows hunts completed, money earned and completions per rarity. Works for offline players. |
| `/hunt top [completions\|earned]` | `hunt.top` | Shows the top 10 hunters. |
| `/hunt refresh` | `hunt.refresh` | Re-rolls all global hunts. |
| `/hunt refresh <player>` | `hunt.refresh` | Re-rolls a player's individual hunts. |
| `/hunt reload` | `hunt.reload` | Reloads the configs. Current hunts are kept and trimmed or topped up to the hunt amount. A file that can't be loaded is left untouched and the previous version stays in use (see *Broken config files*). |

## Placeholders

Messages in `lang.json` and reward commands in `config.json` can use these placeholders:

| Placeholder | Value |
| --- | --- |
| `{player}` | The player's name. |
| `{uuid}` | The player's UUID. |
| `{pokemon}` | The Pokemon's display name. |
| `{price}` | The hunt's price. |

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
  - Older `config.json`/`lang.json` files are upgraded in place, keeping your values and texts. The original is kept as `*.bak.1`.
- **Data safety:** `hunts.json`, `stats.json`, `config.json` and `lang.json` are written atomically (temp file + move). Before a file is overwritten for the first time after a server start, the previous version is kept as `<file>.bak.1`, and older copies move up to `.bak.2` and `.bak.3`. So `.bak.1` is the file as it was at the last start. If `hunts.json` or `stats.json` exists but can't be read (e.g. wrong permissions), Hunt logs an ERROR and doesn't save that file until the next restart.
- **Icons:** `usePackIcons` (on by default) shows species icons from a resource pack that overrides `packIconItem` (default `minecraft:gray_dye`) with `custom_model_data` set to the National Pokedex number, and `0` as a fallback icon. Forms use the base species icon.


## Installation

This mod can be ran on both [Fabric](https://fabricmc.net/) and [Forge](https://files.minecraftforge.net/net/minecraftforge/forge/). It has a few dependencies:

- [Cobblemon](https://modrinth.com/mod/cobblemon)
- [Impactor](https://modrinth.com/mod/impactor)
- [Gooeylibs](https://github.com/NickImpact/GooeyLibs)


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
