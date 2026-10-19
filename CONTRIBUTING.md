# Contributing

Thanks for helping out! Bug reports, ideas, new effects and pull requests are all welcome: open an [issue](../../issues) or a [pull request](../../pulls).

When you report a bug, please include the output of `/de version` (or `/ke version`), the console output from startup, and the files you changed (`config.yml`, `effects.yml`, `messages.yml`). Remove `storage.mysql.password` from `config.yml` first.

## Building

You need a JDK from 17 to 24 (21 recommended; if your default JDK is newer, point `JAVA_HOME` to one of these). No Gradle installation is needed:

```
./gradlew build     # compiles, runs the tests and builds both plugins into build/libs/
./gradlew test      # tests only
```

## Layout

Both plugins are built from one core. `./gradlew build` compiles it once and builds two jars from it, each with its own copy of the core moved into the plugin's package.

| Path | Contents |
|---|---|
| `core/` | everything the plugins do |
| `core/.../EffectsPlugin` | the main class of both plugins, with the developer API |
| `core/.../Variant` | everything that differs between DeathEffects and KillEffects: names, alias, description, trigger |
| `core/.../DeathTrigger`, `KillTrigger` | when each plugin's effects play, and its `triggers` and `compatibility` settings |
| `core/.../command` | the `/de` and `/ke` command |
| `core/.../compat` | other plugins: the other plugin of the pair (`OtherPlugin`) and the death events of fight plugins such as StrikePractice (`FightPlugins`) |
| `core/.../config` | reading files, translating names in files (`ConfigFiles`, `YamlText`), converting files of old versions (`LegacyMigration`) |
| `core/.../effect` | the effects engine: layers, particles (`particle/`), animation shapes (`shape/`), sounds, fireworks |
| `core/.../menu` | the effect menu |
| `core/.../names` | item, sound and particle names of every Minecraft version (`Names`, tables in `resources/names/`) |
| `core/.../platform` | server detection, reflection helpers and the scheduler for Folia and everything else |
| `core/.../storage` | YAML, SQLite and MySQL storage |
| `core/src/main/resources/templates` | `config.yml`, `messages.yml` and `effects.yml` for both plugins; `triggers-death.yml` and `triggers-kill.yml` are each plugin's own part of `config.yml` |
| `core/src/main/templates/plugin.yml` | `plugin.yml` for both plugins, filled in by Gradle from the list in `build.gradle.kts` |
| `deatheffects/`, `killeffects/` | each plugin's main class and API events, the classes other plugins use |
| `buildSrc/` | `RelocatedJar`, which copies the core into each plugin jar |
| `tools/` | scripts that generate the name tables and the test lists of names |

The templates contain tokens that are filled in for each plugin, such as `@PLUGIN@` (DeathEffects), `@PLUGIN_ID@` (deatheffects), `@ALIAS@` (de) and `@KIND@` (death); `Variant.tokens()` lists them all. When the two plugins should differ, put the difference in `Variant`, in a trigger or in a token, never in a copy of a file.

## Rules that keep one jar working everywhere

The plugins run on Minecraft 1.7.10 to 26.3 and on CraftBukkit, Spigot, Paper, Purpur and Folia, from a single jar. The build compiles against the Spigot 1.8.8 API with `--release 8`, which catches most mistakes. The rest needs care:

1. **Java 8.** The plugins must still start on Java 8, so no newer language features or JDK methods (the compiler stops these).
2. **Newer API through reflection.** Anything 1.7.10 does not have yet (added in 1.8 or later) is looked up with `platform.Reflect` after checking that it exists, with a fallback for older servers. The compiler cannot catch 1.8 additions, because it uses the 1.8.8 API.
3. **API that changed shape.** Some types exist in 1.8.8 but became something else later. Linking to them directly compiles fine and then crashes on newer servers:
   - `Sound` is an interface since 1.21.3. Never call `values()`, `valueOf()`, `name()` or `ordinal()` on it, and never use it in a `switch`, `EnumMap` or `EnumSet`. Use `SoundService`.
   - `InventoryView` is an interface since 1.21. Never call its methods; use `MenuHolder` and `event.getInventory()`.
   - `Bukkit.getOnlinePlayers()` returned an array on 1.7.10. Use `Players.online()`.
   - There is no `Particle` class before 1.9. Use `ParticleService`.
4. **Names come from `Names`.** Item, sound and particle names differ between versions. Read them from files through `LayerParser`, `Items` and `Names`, never with `Material.valueOf()` or `Material.getMaterial()` on user input.
5. **Folia.** Never use `Bukkit.getScheduler()` or `BukkitRunnable`. Use `Scheduler`: `executeAt(location, ...)` for anything at a place, `executeFor(entity, ...)` for an entity or player (including sending a player a message or opening a menu), and `execute(...)` for everything else. On Folia, a location or entity may only be touched from the thread that owns it.
6. **Both plugins on one server.** Each plugin jar contains its own copy of the core, moved into the plugin's package. Anything visible to other plugins (permissions, metadata keys, file names, database tables) must include the plugin's id from `Variant`. The two plugins talk to each other only through `wouldPlayEffect` and `skipsWhenOtherEffectPlays`, called by name, so keep those two methods as they are.
7. **No dependencies.** The jars contain no libraries. Database drivers come from the server.

## Permissions

Everything a player or admin can do has its own permission. `Perm` lists them with their defaults and children, and `core/src/main/templates/plugin.yml` declares them with a description; `PluginFilesTest` fails if the two differ. A new command or action gets a new entry in both, a check in the code, a line in `/de help` if it is a command, and a row in the README. Use `Variant.permission(Perm)` rather than writing nodes by hand.

## Configuration files

- Every option in the default files has a comment that explains it. Option names use `kebab-case`.
- Unknown options are reported as possible typos. Read every option of a section through one `OptionReader`, call `reportUnused()` when done, and get sub-sections with `OptionReader.section()`.
- Existing files do not get new options automatically. A missing option in `config.yml` or `messages.yml` uses the default from the jar, so every new option needs a sensible default. If an option is renamed or changes meaning, raise `config-version` and convert old files when they are loaded.
- Write names as they are on the newest Minecraft version (`GRAY_STAINED_GLASS_PANE`, `ENTITY_PLAYER_LEVELUP`, `DUST`). When a name does not exist on older versions, add an alternative: `sound: [BLOCK_AMETHYST_BLOCK_CHIME, BLOCK_NOTE_BLOCK_CHIME]`.
- The default files must load without a single warning on every version the tests know. `DefaultFilesTest` and each plugin's `PluginFilesTest` check this.

## Tests

The tests run without a server and live in the core, where they check both plugins. `core/src/testFixtures` contains `TestServer`, which loads the files with the item, sound and particle names of a real Minecraft version from `core/src/testFixtures/resources/versions/`:

- `DefaultFilesTest`: the default `effects.yml` loads without warnings on every version and `messages.yml` has every message, names are translated correctly when a server moves to another version and back, and broken files are never rewritten.
- `PluginConfigTest`: each plugin's `config.yml` loads without warnings on every version, has its own options and not the other plugin's, has every token filled in, and misspelled options in it are reported.
- `PluginFilesTest` (in each plugin folder): the `plugin.yml` Gradle made for the plugin has the right name, description, command, aliases and permissions.
- `LegacyMigrationTest` (in both plugins): configurations of the old versions are converted, keeping what the owner changed.
- `StorageServiceTest`: the storage, including a database that cannot be reached or goes down while it is used.
- Unit tests for YAML editing, name lookups, colours, menu slots and messages.

To test against another Minecraft version, list its names from the API jar and add the version to `TestServer.VERSIONS`:

```
tools/list-api-names.sh paper-api-1.21.8.jar "the Paper 1.21.8 API" > core/src/testFixtures/resources/versions/1.21.8.txt
```

Before a release, also try the plugins on real servers. A good set is Spigot 1.8.8, Spigot 1.12.2, Paper 1.16.5, Paper 1.20.x, the latest Paper and the latest Folia, and 1.7.10 if you can. Check that `/de version` shows the expected particle output and storage, that the menu, previews and death and kill effects work, and that the console shows no warnings.

## Name tables

`core/src/main/resources/names/` lists the names every item, sound and particle had over the years. The tables are generated from [XSeries](https://github.com/CryptoMorin/XSeries). When Minecraft renames things, regenerate them:

```
git clone --depth 1 https://github.com/CryptoMorin/XSeries.git /tmp/xseries
python3 tools/generate-name-tables.py /tmp/xseries
```

Names that are not in the tables still work on servers that know them, so the tables only need updating for renames.

## Adding an effect or a shape

New effects only need `core/src/main/resources/templates/effects.yml`; both plugins get them. Keep the menu order in mind and make sure the effect loads without warnings on every version (`./gradlew test`).

A new animation shape goes in `core/.../effect/shape/`: extend `ShapeDefinition`, add it to the `Shape` enum, and document its options in the reference at the end of `effects.yml`.

## Releases

1. Update `version` in `gradle.properties` and add the changes to `CHANGELOG.md` under a heading with the version, for example `## 2.0.1`.
2. Commit, then tag the commit with the version, for example `v2.0.1`, and push the tag.
3. GitHub Actions builds both plugins and publishes them in a release for that tag, with that version's part of `CHANGELOG.md` as the release notes. The release fails if the tag does not match `version` or the changelog has nothing for it; fix that, delete the tag (`git push --delete origin v2.0.1`, `git tag -d v2.0.1`) and tag again.

## Code style

Four spaces, no tabs, and lines that mostly stay under 120 characters (`.editorconfig` has the details). Explain *why* in comments rather than *what*. Log details that help to find problems with `Debug.log` (shown with `settings.debug: true`), and errors that can repeat every frame with `Debug.logRepeating`. Keep messages for server owners short, specific and in plain words, and say what they can do about a problem.

By contributing you agree that your contributions are licensed under the [MIT License](LICENSE).
