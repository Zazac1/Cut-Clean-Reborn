# 0.1.0-1.21.11-(Fabric)

## CutClean Reborn — Minecraft 1.21.11 (Fabric)

This release brings clearer in-game configuration and the full CutClean Reborn ruleset to Fabric on Minecraft 1.21.11.

## Added

- **Fabric support** — customize mob and ore drops, Timber, automatic enchantments, Clean Stacks, sugar cane and equipment rules on this Minecraft version.

## Improved

- Timber settings are grouped into tools, leaves and apples, and breaking controls.
- Stuff Rules makes armor limits, enchantment caps and the unique mace easier to find and configure.
- Better Sugar Cane separates player breaks from support loss, each with its own chance and quantity.
- Press Escape to return to the previous menu or cancel unfinished input.
- Configuration menus use concise English labels.
- Updated the CCR logo.

## Fixed

- Clean Stacks converts only drops from blocks you break, including Timber. Enabling it does not change stored items, command-given items, unrelated pickups or crafting results.

## Compatibility / servers

- Requires Java 21, Fabric Loader 0.19.5 or newer, and Fabric API.
- Install in `mods/` for singleplayer or on a Fabric server. Players can join a dedicated CCR server without installing CCR on their client.
- Server operators and singleplayer world owners can use `/ccr config`.

## Before updating

- Existing configuration paths remain compatible.
- Close your world or stop your server before replacing the previous CCR JAR.
