
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
| `/hunt reload` | `hunt.reload` | Reloads the configs. Current hunts are kept and trimmed or topped up to the hunt amount. |

## Notes

- **Blacklist:** an entry blocks a species and all its forms (`"Vulpix"`), or a single form with `Species-Form` (`"Vulpix-Alola"`).
- **Custom prices:** an entry with a `form` wins over an entry for the whole species.
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
