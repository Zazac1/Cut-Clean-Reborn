# 0.1.0-1.21.11-(Spigot)

## CutClean Reborn — Minecraft 1.21.11 (Spigot)

This release brings clearer in-game configuration and the full CutClean Reborn ruleset to Spigot on Minecraft 1.21.11.

## Added

- **Spigot support** — customize mob and ore drops, Timber, automatic enchantments, Clean Stacks, sugar cane and equipment rules on this Minecraft version.

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

- Requires Java 21 and a Spigot server running Minecraft 1.21.11.
- Install in `plugins/`. Players do not need a client mod.
- Operators can use `/ccr config`; plugin permission: `cutcleanreborn.config`.

## Before updating

- Existing configuration paths remain compatible.
- Close your world or stop your server before replacing the previous CCR JAR.
