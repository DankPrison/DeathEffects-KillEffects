# Changelog

## 2.0.1

### Fixed

- No effect played for deaths that another plugin had cancelled. Duel and practice plugins such as StrikePractice cancel the death on Paper to end a fight without the death screen, so kills in their fights showed nothing, while the old versions did play an effect. These deaths play effects again. If a plugin cancels deaths to revive players and no effect should play then, set the new option `triggers.cancelled-deaths: false`.
- Effects play where the player died, even when another plugin teleports the player away while handling the death. `enabled-worlds` and `disabled-worlds` go by that place as well.

### New

- StrikePractice: deaths in its fights are also taken from its own event, so they play effects however StrikePractice handles them. A death that is reported both ways plays one effect.
- `settings.debug` says when it is switched on, and also explains deaths that play no kill effect because no player killed the victim or another plugin cancelled the death.
- `plugin.yml` names the GitHub page as the plugins' website, and the release notes on GitHub come from this changelog.

## 2.0.0

A rewrite of both plugins. Configurations and saved selections of the old versions (0.x) are converted automatically on first start.

### Compatibility

- One jar per plugin for Minecraft 1.7.10 to 26.3, on CraftBukkit, Spigot, Paper, Purpur and Folia. Runs on Java 8 and newer.
- EffectLib is no longer needed, and neither is anything else: the effects run on a built-in engine.
- Item, sound and particle names in the files are written for the server's Minecraft version and translated in place, comments and all, when the server moves to another version. A backup of each changed file is kept.
- DeathEffects and KillEffects can run on the same server without conflicts. When one player kills another, you choose whether both effects play or only one.
- Both plugins are built from one shared core in a single project: one `./gradlew build` makes both jars, so they always get the same fixes and features.

### New

- `effects.yml`: change any of the 53 effects or add your own without code. Effects are built from particle, firework, lightning, sound and animation layers, with 13 animated shapes, colours, delays and repeats.
- `messages.yml` with every message, including hex colours on 1.16+.
- Previews: right click an effect in the menu or use `/de preview <effect>`, with a cooldown and an option to show previews only to the player.
- New commands: `help`, `list`, `select`, `preview`, `info` and `version`, plus tab completion. Admins can select, clear, open the menu and look up effects for other players.
- A permission for everything: opening the menu, each command (`deatheffects.select`, `.list`, `.info`, `.help`, `.version`, ...) and each effect. `/de help` and tab completion only show what a player may use.
- Menu: any number of effects over several pages, configurable slots, buttons, items and sounds, locked effects shown or hidden, and a glow on the selected effect.
- Storage in YAML, SQLite or MySQL/MariaDB. If the database cannot be reached, or the connection is lost while the server runs, selections are kept in a file and copied to the database once it is back.
- Settings for worlds, a default effect, when effects play (KillEffects can now play for mobs and ignore some of them), and the permission defaults.
- Performance settings: view distance, a limit on running animations and a particle multiplier.
- Developer API: cancellable events before an effect plays or a selection changes, and methods to read and change selections and to play effects.
- Mistakes in the files, misspelled options included, are reported with their exact place, and the rest of the file keeps working.
- `settings.debug` shows in the console why an effect did or did not play.

### Changed

- Selections are saved by player UUID, so they survive name changes, and no longer in `config.yml`.
- Database work runs on a background thread instead of the main thread.
- Fireworks and lightning are purely visual: they never cause damage or fire.
- `deatheffects.*` and `killeffects.*` now grant every permission of the plugin, including the admin commands. To let players use every effect, give them `deatheffects.alleffects` or `killeffects.alleffects`.
- The menu, `/de clear` and previews are available to everyone by default (`deatheffects.gui`, `.clear` and `.preview`).
- `deatheffects.effects.others` is now called `deatheffects.select.others`; the old name still works.
