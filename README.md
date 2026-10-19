# DeathEffects & KillEffects

[![Build](https://github.com/DankPrison/DeathEffects-KillEffects/actions/workflows/build.yml/badge.svg)](https://github.com/DankPrison/DeathEffects-KillEffects/actions/workflows/build.yml)

Two Minecraft server plugins that make deaths and kills look good:

- **DeathEffects**: players pick an effect that plays where *they* die.
- **KillEffects**: players pick an effect that plays where *their victims* die.

Both come with 53 effects: particle bursts, fireworks, harmless lightning, sounds, and animated shapes such as a vortex, a DNA helix, a disco ball and a dragon's breath. Players choose an effect in a menu. Everything can be changed in YAML files, and you can add your own effects without writing code.

Use either plugin on its own or run both together. They are built to work side by side.

## Compatibility

| | |
|---|---|
| **Minecraft** | 1.7.10 to 26.3 (each plugin is a single jar for every version) |
| **Server software** | CraftBukkit, Spigot, Paper, Purpur, Folia, and forks of these |
| **Java** | whatever your server needs (the plugins themselves run on Java 8+) |
| **Dependencies** | none |

Names of items, sounds and particles changed many times between Minecraft versions. The plugins translate them automatically:

- config files are written with the names your version uses (for example `GRAY_STAINED_GLASS_PANE` on 1.13+ and `STAINED_GLASS_PANE:7` on 1.8);
- names from any other version are understood as well;
- when you move a server to a different Minecraft version, the names in your files are translated in place, comments and all. A backup of the old file is kept in the `backups` folder.

Where a newer sound or particle has no counterpart on an older version, the default effects name an alternative (`sound: [BLOCK_AMETHYST_BLOCK_CHIME, BLOCK_NOTE_BLOCK_CHIME]`): the first name your version knows is used.

On Folia, effects run on the region thread that owns the death location, and menus run on the player's thread.

## Installation

1. Download `DeathEffects-<version>.jar` and/or `KillEffects-<version>.jar` from the [releases](../../releases).
2. Put the jar(s) into your server's `plugins` folder and restart the server.
3. Configure `plugins/DeathEffects` and `plugins/KillEffects` to your liking, then run `/de reload` or `/ke reload`.

Upgrading from the old versions (0.x) is automatic. On first start the plugin:

- converts the old `config.yml` and keeps a backup as `config-v1-backup.yml`;
- carries over what you had changed: effect names and icons, menu texts, sounds, messages and MySQL settings;
- imports the players' saved selections, including the old MySQL table `currentEffects`. The old versions saved some selections by player name; these are kept in `legacy-selections.yml` and applied when a player with that name next joins.

One thing to check by hand: `deatheffects.*` used to grant only the effects and now grants everything, including the admin commands. Give players `deatheffects.alleffects` if they should be able to use every effect.

## Commands

`/deatheffects` (aliases `/de`, `/deatheffect`) and `/killeffects` (aliases `/ke`, `/killeffect`) work the same way:

| Command | Description | Permission |
|---|---|---|
| `/de` | open the effect menu (`/de <number>` opens that page) | `deatheffects.gui` |
| `/de help` | list the commands you may use | `deatheffects.help` |
| `/de list` | list all effects | `deatheffects.list` |
| `/de select <effect>` | pick an effect (`/de <effect>` also works) | `deatheffects.select` and the effect's permission |
| `/de preview <effect>` | play an effect in front of you | `deatheffects.preview` |
| `/de clear` | remove your effect | `deatheffects.clear` |
| `/de info` | show your effect | `deatheffects.info` |
| `/de select <effect> <player>` | pick an effect for someone else | `deatheffects.select.others` |
| `/de clear <player>` | clear someone else's effect | `deatheffects.clear.others` |
| `/de open <player> [page]` | open the menu for someone else | `deatheffects.gui.others` |
| `/de info <player>` | show someone else's effect | `deatheffects.info.others` |
| `/de reload` | reload all files | `deatheffects.reload` |
| `/de version` | show version and server details | `deatheffects.version` |

In the menu, left click selects an effect (this needs only the effect's permission), right click previews it (`deatheffects.preview`) and the clear button needs `deatheffects.clear`. `/de help` and tab completion only show what a player may use.

## Permissions

Everything has its own permission: opening the menu, each command, and each effect. KillEffects has the same permissions, starting with `killeffects.` instead of `deatheffects.`.

**Players.** Everybody has these. Take one away with your permission plugin to turn that feature off for a group.

| Permission | Allows |
|---|---|
| `deatheffects.gui` | opening the menu: `/de`, `/de open`, `/de <page>` |
| `deatheffects.select` | picking an effect by command: `/de select <effect>`, `/de <effect>` |
| `deatheffects.clear` | clearing your own effect: `/de clear` and the button in the menu |
| `deatheffects.preview` | previewing effects: right click in the menu, `/de preview <effect>` |
| `deatheffects.list` | `/de list` |
| `deatheffects.info` | `/de info` |
| `deatheffects.help` | `/de help` |

**Effects.** Operators have these by default.

| Permission | Allows |
|---|---|
| `deatheffects.<effect>` | using one effect, for example `deatheffects.vortex` |
| `deatheffects.alleffects` | using every effect |
| `deatheffects.preview.bypass` | previewing without waiting for the cooldown |

Every effect gets its permission when the plugin starts, so permission plugins can suggest them. In `effects.yml` an effect can be given another permission, or none at all. Who has the effect permissions by default (`op`, everybody, nobody) is set with `settings.effect-permission-default`. By default an effect stops playing when the player loses its permission, for example when a rank expires; turn this off with `settings.require-permission`.

**Admin commands.** Operators have these by default.

| Permission | Allows |
|---|---|
| `deatheffects.reload` | `/de reload` |
| `deatheffects.version` | `/de version` |
| `deatheffects.gui.others` | `/de open <player>` |
| `deatheffects.select.others` | `/de select <effect> <player>` and `/de <effect> <player>` |
| `deatheffects.clear.others` | `/de clear <player>` |
| `deatheffects.info.others` | `/de info <player>` |
| `deatheffects.admin` | all of the admin commands above |
| `deatheffects.*` | everything: every permission on this page |

`deatheffects.effects.others`, the name the old versions used for `deatheffects.select.others`, still works.

Some examples with LuckPerms:

```
lp group vip permission set deatheffects.alleffects true      # VIPs may use every effect
lp group default permission set deatheffects.select false     # players pick effects in the menu only
lp group helper permission set deatheffects.info.others true  # helpers may look up effects
```

## Configuration

Each plugin has three files:

| File | Contents |
|---|---|
| `config.yml` | general settings, triggers, compatibility, previews, the menu, performance and storage |
| `effects.yml` | all effects. Change them, delete them or add your own. A reference of every option is at the bottom of the file |
| `messages.yml` | every message. Supports `&` colour codes and `&#RRGGBB` hex colours (1.16+). Set a message to `''` to disable it |

Mistakes are reported in the console with the exact place, for example `effects.yml: effects.flame.layers[0]: unknown option 'cuont' (ignored)`, and the rest keeps working. Misspelled options are reported too, and their default is used.

If a file is not valid YAML, it is never rewritten. `/de reload` then keeps the previous settings. On startup, the default settings are used until the file is fixed; for `config.yml` this includes the storage, so selections are kept in `players.yml` meanwhile and copied to your database when you fix the file and reload.

Set `settings.debug: true` to see in the console why an effect did or did not play.

**Triggers.** DeathEffects can play for deaths by other players and/or all other deaths. KillEffects can play for killing players, killing mobs (with a list of mobs to ignore) and killing yourself; by default it only plays for killing players.

**Storage.** Selections are saved in `players.yml` by default. SQLite and MySQL/MariaDB are also supported, using the drivers that come with Spigot and Paper. Use MySQL to share selections between servers. Each plugin uses its own tables (`deatheffects_players` and `deatheffects_players_meta`, `killeffects_players` and `killeffects_players_meta`; change the names with `storage.table`), so both can share one database.

- When you switch from `players.yml` to a database, the selections in `players.yml` are copied into it and the file is renamed to `players.yml.imported`.
- If the database cannot be reached, on startup or while the server runs, the plugin keeps working: selections are saved in `players-fallback.yml`, the database is tried again every five minutes and on `/de reload`, and once it can be reached, everything saved in the meantime is copied to it and the effects of players who joined meanwhile are loaded from it. (After a `/de reload` that switches from `players.yml` to a database that cannot be reached, `players.yml` stays in use instead.)
- A `players.yml` or `players-fallback.yml` that cannot be read is never overwritten: it is renamed to `<file>.broken-<date>` and a new file is started.

**Performance.** You can set:

- how far away players see effects;
- how many animations may run at the same time;
- a multiplier for the amount of particles.

Particles are only sent to players who are close enough to see them.

### Making your own effects

Add a section under `effects:` in `effects.yml` and run `/de reload`:

```yaml
effects:
  stars:
    name: '&eStar &aEffect'
    icon: NETHER_STAR
    lore:
      - '&7Seeing stars.'
    layers:
      - type: particle
        particle: FIREWORK
        count: 40
        offset: 0.5
        speed: 0.05
      - type: animation
        shape: RING
        particle: DUST
        color: '#FFD700'
        radius: 1.5
        duration: 40
      - type: sound
        sound: ENTITY_PLAYER_LEVELUP
        pitch: 1.5
```

There are five layer types: `particle`, `firework`, `lightning`, `sound` and `animation`. Every layer can start later (`delay`) and repeat (`repeat`, `interval`). Animations come in 13 shapes: helix cone, shockwave, ring wave, vortex, DNA, disco ball, dragon breath, polygon, sphere, ring, helix, tornado and fountain. All of their options are listed at the end of `effects.yml`.

Colours can be written as `'#FF0000'`, `'255,0,0'`, a name such as `RED`, a list of colours to cycle through, `RAINBOW` or `RANDOM`. Sounds can also be vanilla keys (`entity.player.levelup`) or resource pack sounds (`mypack:my.sound`).

## Running both plugins

DeathEffects and KillEffects are independent. Each has its own commands, permissions, files, database tables and menu. Each contains its own copy of the shared code, so they never interfere, even when different versions are installed.

When one player kills another, both effects play by default: the victim's death effect and the killer's kill effect. To show only one of them, use the `compatibility` section of either `config.yml`:

- DeathEffects `skip-when-kill-effect-plays: true`: the death effect is skipped whenever the killer's kill effect plays.
- KillEffects `skip-when-death-effect-plays: true`: the kill effect is skipped whenever the victim's death effect plays.

If both are enabled, the kill effect plays.

## Duel and practice plugins

Duel, practice and minigame plugins often end a fight without a real death: on Paper they cancel the death, so the player skips the death screen. Effects play for these deaths as well, where the player died, even if the plugin teleports the player away at once (`enabled-worlds` and `disabled-worlds` also go by that place). If a plugin on your server cancels deaths to revive players and no effect should play then, set `triggers.cancelled-deaths: false`.

StrikePractice also reports the deaths in its fights through its own event. When StrikePractice is installed, both plugins use that event too, so deaths in its fights play effects however StrikePractice handles them. A death that is reported both ways plays one effect.

If effects still don't play in fights, set `settings.debug: true`: deaths then show in the console with the reason why an effect played or not.

## Bugs and ideas

Report bugs and suggest effects or features in the [issues](../../issues). [CONTRIBUTING.md](CONTRIBUTING.md) says what to include in a bug report, and how to build and change the plugins.

## For developers

Both plugins fire cancellable events:

| Event | When | Can change |
|---|---|---|
| `com.dank420girl.deatheffects.api.DeathEffectPlayEvent` | before a death effect plays (cancel it in minigame arenas, ...) | effect, location |
| `com.dank420girl.deatheffects.api.DeathEffectSelectEvent` | before a player's death effect changes | |
| `com.dank420girl.killeffects.api.KillEffectPlayEvent` | before a kill effect plays | effect, location |
| `com.dank420girl.killeffects.api.KillEffectSelectEvent` | before a player's kill effect changes | |

The play events tell you the player and killer (`getPlayer()`, `getKiller()`) or killer and victim (`getKiller()`, `getVictim()`); the select events give the previous and the new effect id (`null` means none).

The plugin instances also offer a few methods:

```java
DeathEffectsPlugin deathEffects = (DeathEffectsPlugin) Bukkit.getPluginManager().getPlugin("DeathEffects");
String effect = deathEffects.getSelectedEffectId(player.getUniqueId()); // null = none, or not online
boolean changed = deathEffects.setSelectedEffect(player, "vortex");     // fires the select event
deathEffects.playEffect("dna", location, player);                       // play any effect anywhere
Set<String> ids = deathEffects.getEffectIds();                          // all effects, in menu order
```

- `getSelectedEffectId` knows the selections of online players, once they have been loaded after joining.
- `setSelectedEffect` returns `false` if there is no such effect or the select event was cancelled. It does not check the effect's permission, and `null` clears the selection.
- `playEffect` does not fire the play event.

`KillEffectsPlugin` offers the same methods. Call them from the main thread, or on Folia from the thread that owns the player or location. To compile against a plugin, add its jar as a `compileOnly` dependency and add `softdepend: [DeathEffects]` to your `plugin.yml`.

## Building

You need a JDK from 17 to 24 to run the build (21 recommended; if your default JDK is newer, point `JAVA_HOME` to one of these). Then run:

```
git clone https://github.com/DankPrison/DeathEffects-KillEffects.git
cd DeathEffects-KillEffects
./gradlew build
```

This builds both plugins: `build/libs/DeathEffects-<version>.jar` and `build/libs/KillEffects-<version>.jar`. The build compiles against the Spigot 1.8.8 API with Java 8 bytecode, so one jar runs everywhere. Newer features are used through reflection when the server supports them.

Both plugins are built from the same core. The two plugin folders only hold what other plugins can use: the main class and the two API events.

| Folder | Contents |
|---|---|
| `core` | everything else: effects engine (an EffectLib replacement), menus, commands, storage, version compatibility, the triggers of both plugins, and the templates of `config.yml`, `effects.yml`, `messages.yml` and `plugin.yml` |
| `deatheffects` | `DeathEffectsPlugin` and the DeathEffects events |
| `killeffects` | `KillEffectsPlugin` and the KillEffects events |

The tests check, among other things, that the default files load without a single warning with the item, sound and particle names of seven Minecraft versions from 1.7.10 to 26.3. See [CONTRIBUTING.md](CONTRIBUTING.md) for details.

## License

[MIT](LICENSE). Most of the animation shapes are based on [EffectLib](https://github.com/elBukkit/EffectLib), and the version name tables on [XSeries](https://github.com/CryptoMorin/XSeries); see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
