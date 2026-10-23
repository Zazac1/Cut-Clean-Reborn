# CutClean Reborn

Server-side UHC and quality-of-life rules for Paper 26.2 and 26.3, using Java 25.
Players connect with a normal Minecraft client.

## Features

- Per-mob cooking, bonus XP, edited vanilla drops and additional drops with independent quantities and chances.
- Ore drops by block variant, Fortune controls, automatic smelting with furnace XP, and copying between stone/deepslate variants.
- Timber for natural trees, allowed axes or hand cutting, optional sneaking, scaled break time, leaf harvesting and custom apple chances.
- Automatic enchantments for armor and tools, material-based copying, and explicit refresh of unchanged CCR items.
- Personal Clean Stacks conversion for wood and raw stone families, with individual or bulk material mappings.
- Better Sugar Cane for player and support breaks, with independent chance and quantity settings.
- Equipment rules: equipped armor limits, enchantment caps, verified XP refunds and one unique craftable mace.

## Commands

| Command | Behavior |
| --- | --- |
| `/ccr` or `/ccr config` | Open the admin configuration menu. Requires `cutcleanreborn.config` (operators by default). |
| `/ccr status` | Show the active feature switches. |
| `/ccr reload` | Validate and reload `config.yml`, rebuild caches and close outdated CCR menus. Requires the admin permission. |
| `/ccr help` | Show available commands. |
| `/ccr ping` | Check plugin loading. |
| `/cleanstacks` | Toggle your own conversion preference. |
| `/cleanstacks on`, `off`, `status` | Set or inspect your own preference; `true`/`false` remain supported. |

`/cutclean` and `/crr` remain aliases of `/ccr`.
Menus save changes automatically. Escape goes up exactly one menu level; from CCR Config it closes the menu. In a text field or confirmation, Escape cancels without saving or applying the pending action. The explicit **Close CCR** button exits directly from any of the redesigned pages.

- **Better Sugar Cane:** one master switch, then separate Player Break and Support Break pages with a switch, bonus chance and final drop quantity.
- **Timber:** separate Tools & Activation, Leaves & Apples, and Breaking & Limits pages. Mining effort and processing speed are explained independently.
- **Stuff Rules:** direct access from CCR Config to armor limits, enchantment caps and the unique mace. Rules use clickable target categories, material families or exact item lists; enchantments have a searchable picker and XP refunds have their own page. New and copied rules start disabled.

Switches distinguish **ON**, **OFF** and **PAUSED** (saved as enabled, but waiting for a master switch). Back and Close have fixed positions on the redesigned pages; pagination never adds extra Escape steps.
Both decimal separators are accepted for percentages, e.g. `4.5%` or `4,5%`.
Destructive resets, rule deletion and ore-setting overwrites have a confirmation step.
Global animal and Looting options are available from Mob Customisation → Global Mob Settings.

## Gameplay details

Timber uses normal player block breaks: tool durability, Unbreaking, Silk Touch, drops and other plugins' block protections still apply.
It processes a configurable number of blocks per tick, stops when the player changes tool or leaves, and excludes player-placed logs tracked since installation.
`tree-cutter.max-logs` defaults to 256 (configurable from 2 to 2048); `blocks-per-tick` defaults to 12.
The optional break-time setting retains the repeated-break progress on the initial log.

Custom mob overrides roll once per death, even if Minecraft's own roll produced no corresponding item.
Looting adds a random 0 to the enchantment level to configured quantities; Fortune uses a multiplier up to level + 1.
The unedited ore drops still use the server's actual loot tables. Mob previews are sampled estimates, not an exhaustive guarantee of rare/context-dependent loot.
Silk Touch, unsuitable tools and creative mining do not grant custom ore bonuses.

Automatic enchantments keep stronger existing enchantments and do not introduce conflicts with them.
They obey enabled equipment caps before stamping the item, so the two features do not fight each other.
Refresh changes only CCR items whose enchantments have not subsequently changed; newly processed items also remember their pre-CCR enchantments.

Clean Stacks keeps quantities and skips items with custom metadata. Cyclic mappings resolve to a stable output.
Placed sugar cane is tracked in chunk data so its origin survives restarts. Naturally grown blocks remain eligible for bonuses.
The unique mace is released on despawn, destruction or breakage, rather than on pickup or chunk unload.
Anvil/enchanting refunds are bounded by the levels actually paid in that transaction; correcting found items never refunds XP.

Existing `config.yml` paths and item UUID markers remain supported. No existing gameplay feature was removed.

## Repository layout and development

The repository follows the same layout as CustomRecipies: one self-contained project per loader and Minecraft version.

```text
CutCleanReborn/
├── Paper_26.2/
│   ├── src/main/          # Plugin sources and default configuration
│   ├── src/test/          # Unit tests
│   ├── src/smoke/         # Isolated native-server tests
│   ├── build.gradle       # Standalone build
│   ├── gradle.properties  # Plugin, Minecraft and Paper API versions
│   ├── gradle/ + gradlew* # Build launcher
│   ├── run-server/        # Local server, worlds and configuration (ignored by Git)
│   ├── build/libs/        # Generated JAR (ignored by Git)
│   ├── CHANGELOG.md
│   ├── LICENSE
│   └── README.md
├── Paper_26.3/             # Same structure, independently buildable
├── validation/            # Cross-version runner and manual test checklist
├── test-all.ps1            # Default: complete validation of both Paper projects
├── Modrinth/               # Existing artwork and model files
└── README.md
```

Each Paper directory can be built on its own without a sibling source folder. Both currently contain the same gameplay implementation; the common validation runner checks for unintended differences in sources, defaults and tests.
`gradle.properties` and `src/main/resources/plugin.yml` identify the target Minecraft/Paper version.
The existing Git history remains at the repository root. No nested repositories or new loader ports were created.
The former Fabric implementation is currently removed from the working tree; it remains in Git history and its historical version branch.

## Git branches

Like [CustomRecipe](https://github.com/Zazac1/CustomRecipe), version branches use `<loader>/<Minecraft>` and contain a buildable project directly at their root:

- `main`: complete workspace, documentation and cross-version validation (default branch).
- `paper/26.2` and `paper/26.3`: the current standalone Paper variants.
- `fabric/1.21.11`: historical Fabric sources, currently paused and not synchronized.

See [BRANCHES.md](BRANCHES.md) for the workflow. After validating and committing changes on `main`, `sync-branches.ps1` previews the updates; `-Mode Publish` synchronizes the Paper branches and publishes them with `main`, without forced updates or branch deletion.

## Build and verification

From the repository root, validate every current Paper variant:

```powershell
.\test-all.ps1
```

See [validation/README.md](validation/README.md) for prerequisites, modes and reports. From either Paper directory:

```powershell
.\gradlew.bat build
.\gradlew.bat runServer
```

The build produces `build/libs/cutcleanreborn-<plugin-version>+<minecraft-version>.jar`, following the same naming convention as Custom Recipe.
For example: `cutcleanreborn-0.1.0-SNAPSHOT+26.3.jar`. Renaming the file does not change the plugin's declared version or its configuration directory.
Superseded CCR JARs from `build/libs/` are preserved under `build/artifact-backups/` to keep the output folder unambiguous.
Install the JAR matching your server version, replacing the previous CCR JAR.
`runServer` downloads the latest Paper build for that game version and starts `run-server/`.
Stop the server before replacing its plugin. Local worlds, logs, caches and generated files are ignored by Git.

`build` runs that project's regression tests against its chosen Paper API. CI builds both independent projects.
A native suite is also available once `run-server/` already contains a Paper JAR and an accepted `eula.txt`:

```powershell
.\gradlew.bat smokeTest
```

It starts a separate local server under `build/smoke-server/`, checks native items, menus, loot previews and configuration reload, writes `smoke-result.txt`, then shuts down. Menu navigation checks simulate player inventory events on the native server, including Escape, cancelled confirmations, text-field cancellation, pagination and explicit closure; they do not replace a visual check in a Minecraft client.
It never opens an existing development world, and its test plugin is not included in release JARs.
For hands-on verification, also test inventory navigation and cancellation, protected Timber blocks, tool breakage, Silk Touch/Fortune, sugar-cane support loss, and crafting/destroying the unique mace with real players.
