# CutClean Reborn

<img src="Paper_26.3/src/main/resources/icon.png" width="128" alt="CutClean Reborn logo">

UHC and quality-of-life rules for Minecraft. The multiplatform port targets Paper, Purpur, Spigot, CraftBukkit and Fabric on 1.21.1, 1.21.11, 26.1.2 (26.1.x family), 26.2 and 26.3. Java 21 is used for 1.21.x, Java 25 for 26.x.
The 25 projects target five Minecraft families. Native server, cold restart, standalone build and actual player observations are recorded separately in [the validation reports](validation/README.md) and [the client log](validation/porting/client-tests.md). Plugin players use a normal matching Minecraft client; Fabric also supports single-player.

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

Small Fabric pages use compact, centered layouts instead of a mostly empty six-row inventory. Dashboards separate their main switch, categories and footer utilities. Back stays at the lower left and Close at the lower right; Paper and Bukkit use the same darker frame and dedicated footer positions. Large catalogues keep their pagination and all entries.

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

Clean Stacks converts only drops from blocks you break, including Timber cuts. Enabling it never rewrites your inventory; commands, unrelated pickups, mob drops and crafting results keep their original materials. Quantities and custom metadata are preserved. Cyclic mappings resolve to a stable output.
Placed sugar cane is tracked in chunk data so its origin survives restarts. Naturally grown blocks remain eligible for bonuses.
The unique mace is released on despawn, destruction or breakage, rather than on pickup or chunk unload.
Anvil/enchanting refunds are bounded by the levels actually paid in that transaction; correcting found items never refunds XP.

Existing `config.yml` paths and item UUID markers remain supported. No existing gameplay feature was removed.

## Repository layout and development

Each `<Loader>_<Minecraft>` directory is an autonomous project, following CustomRecipies. Its sources, resources, tests, wrapper and build script are local; no version reads code from another directory.

```text
CutCleanReborn/
├── Paper_<Minecraft>/
├── Purpur_<Minecraft>/
├── Spigot_<Minecraft>/
├── Bukkit_<Minecraft>/
├── Fabric_<Minecraft>/
│   ├── src/                  # Sources, configuration, logo and tests
│   ├── build.gradle
│   ├── gradle.properties
│   ├── settings.gradle
│   ├── gradle/ + gradlew*
│   ├── build/libs/            # This version's installable JAR
│   ├── build/artifact-backups/ # Superseded generated artifacts, if any
│   └── README.md + LICENSE
├── validation/               # Workspace checks, reports and preserved technical archives
│   ├── git/                  # Version branch workflow
│   ├── porting/              # Platform notes and artifact reports
│   └── results/artifacts/    # Compatibility index pointing to version JARs
├── Modrinth/                 # Existing artwork and model files
├── test-all.ps1
└── README.md
```

Spigot and Bukkit also have their own `tools/` with the official BuildTools bootstrap. Existing development servers, worlds and configuration remain in their original version directories. The old central build outputs and shared directories are preserved in the ignored archive `validation/build/layout-backup-20261024-221357/`; current builds do not use them. There is no central `dist/`.

All 25 projects contain the supplied logo at `src/main/resources/icon.png`. Fabric declares it in `fabric.mod.json`; plugins package the same resource. The original artwork is preserved without a background change.

Paper variants retain strict implementation parity with documented API adaptations. Each Purpur project contains its corresponding Paper implementation locally. The ten Spigot/Bukkit copies are also checked for drift. These checks help keep independent projects consistent without sharing files between them.

## Git branches

Version branches follow [CustomRecipe](https://github.com/Zazac1/CustomRecipe):

- `main` contains the complete workspace.
- `<paper|purpur|spigot|bukkit|fabric>/<Minecraft>` contains exactly the corresponding version project's files at its root.

See [the branch workflow](validation/git/BRANCHES.md). After validation and a commit on main:

```powershell
.\validation\git\sync-branches.ps1              # Read-only plan
.\validation\git\sync-branches.ps1 -Mode Local  # Update local version branches
.\validation\validate-exports.ps1               # Compile fresh isolated branch archives
.\validation\git\sync-branches.ps1 -Mode Publish
```

Publication preserves branch ancestry and uses an atomic push, without forced updates or branch deletion. GitHub publication is authorized; Modrinth publication is outside this workflow.

## Build and verification

From any version directory:

```powershell
.\gradlew.bat build
```

Install its `build/libs/cutcleanreborn-0.1.0+<Minecraft>.jar`, for example `cutcleanreborn-0.1.0+26.3.jar`. Renaming or adding the logo does not increment the plugin version. Fabric source JARs are under `build/sources/`, and test JARs are under `build/verification/`.

From the workspace root, run the required complete native validation:

```powershell
.\test-all.ps1
.\validation\run-persistence-tests.ps1
```

The default Full run checks all 25 variants on their real platform servers, including Fabric's distribution JAR outside Loom. The separate persistence run checks the 20 plugins across three cold JVMs each; Full already includes the five Fabric cold triplets. Read [the prerequisites and reports](validation/README.md).

Tests use fresh worlds under each project's `build/`; they do not reuse `run-server/` worlds. Simulated menu interactions on a native server are not a visual Minecraft check. Actual client observations retain the hashes of the builds used at the time; this directory and logo update does not relabel them as new player tests. See [the isolated client QA environment](validation/client-server/README.md).

After native verification:

```powershell
.\validation\porting\collect-artifacts.ps1
```

This writes `validation/results/artifacts/compatibility.json` and `README.md`, linking the JAR in each version directory only when its evidence still matches. It does not copy binaries into a central output directory. `-RequireComplete` additionally requires a complete valid player traversal for every variant.

`validation/porting/organize-artifacts.ps1 -Preview` previews archival of superseded generated CCR artifacts inside each version. It preserves user servers, worlds and Modrinth profiles.
