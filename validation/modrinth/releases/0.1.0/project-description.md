# ⚔️ CutClean Reborn

**CutClean Reborn** brings configurable UHC and quality-of-life rules to **Paper, Purpur, Spigot, CraftBukkit and Fabric**. Fabric also supports **singleplayer**.

It lets server administrators configure common UHC mechanics directly in-game: custom mob and ore drops, automatic smelting, timber, automatic enchantments, equipment restrictions, unique items, quality-of-life features, and more.

Dedicated servers accept normal Minecraft clients; players do not need CCR installed locally.

---

## ✨ Features

### 🐄 Mob Drops

Customize how mobs behave when they die.

- Custom drops per mob
- Automatic cooking for supported drops
- Configurable animal drop multiplier
- Bonus experience
- Direct-to-inventory pickup
- Configurable Looting behavior

---

### ⛏️ Ore Drops

Control how ores behave when mined.

- Keep vanilla drops or define custom drops
- Automatic smelting
- Configurable Fortune behavior
- Bonus experience rewards
- Different settings for each ore

---

### 🌲 Timber

Cut entire trees without breaking every log manually.

- Configurable allowed tools
- Optional sneak requirement
- Configurable leaf handling
- Adjustable tree-breaking delay
- Designed to remain configurable for different UHC rulesets

---

### ✨ Automatic Enchantments

Automatically apply configured enchantments when specific equipment enters a player's inventory.

Rules can be configured separately for:

- Armor
- Tools
- Individual items
- Items using the same material

Automatic enchantments are applied only once to each item, so players can still modify their equipment afterward without the plugin constantly overwriting it.

---

### 📦 Clean Stacks

Players can convert supported wood and stone drops from blocks they break, including Timber. Enabling Clean Stacks never changes items already stored in the inventory. Command-given items, unrelated pickups and crafting results are unchanged.

Each player controls this feature individually with:

`/cleanstacks on` or `/cleanstacks off` (`true` and `false` also remain supported).

---

### 🎋 Better Sugar Cane

Sugar cane can drop additional items when broken.

This works both when:

- A player directly breaks the sugar cane
- Sugar cane breaks because its supporting block was removed

---

## 🛡️ Stuff Rules

CutClean Reborn includes configurable rules for equipment and special items.

### Unique Mace

Only **one mace can exist on the server at a time**.

Once the first mace is created, further crafts are blocked until that specific mace is permanently destroyed.

Moving it between inventories, storing it, dropping it, or dying with it does not free the slot.

### Armor Limits

Limit how much equipment of a specific type a player can wear at the same time.

Rules can target:

- A complete material, such as Diamond
- A specific armor item
- Different limits depending on your UHC rules

These limits only apply to **equipped armor slots**. Players can still carry additional equipment in their inventory unless another rule explicitly prevents it.

### Enchantment Limits

Set maximum enchantment levels for:

- Equipment categories
- Specific items

If an item exceeds the configured limit, the enchantment is automatically reduced to the allowed level.

Players are notified when a correction is applied.

Rules can also optionally refund experience when an enchantment or anvil operation is adjusted, without allowing XP duplication.

### Rule Management

Each rule can be:

- Enabled or disabled independently
- Configured for a specific target
- Copied to another compatible target

This makes it easier to configure complete armor sets or multiple similar tools without recreating every rule manually.

---

## ⚙️ In-game Configuration

Most plugin settings can be managed directly through the administrator interface:

`/ccr config`

The configuration menu is designed to avoid manual configuration-file editing for normal server setup.

---

<details>
<summary><strong>⌨️ Commands & Permissions</strong></summary>

### Commands

| Command | Description |
| --- | --- |
| `/ccr config` | Opens the administrator configuration menu |
| `/ccr ping` | Checks whether CutClean Reborn is loaded correctly |
| `/cleanstacks <true\|false>` | Enables or disables Clean Stacks for the player |

### Permissions

| Permission | Description |
| --- | --- |
| `cutcleanreborn.config` | Access to the administrator configuration interface |

`cutcleanreborn.config` is available to operators by default.

</details>

---

## 🖥️ Installation and compatibility

| Minecraft | Platforms | Java |
| --- | --- | --- |
| 1.21.1, 1.21.11 | Paper, Purpur, Spigot, Bukkit/CraftBukkit, Fabric | 21+ |
| 26.1.2, 26.2, 26.3 | Paper, Purpur, Spigot, Bukkit/CraftBukkit, Fabric | 25+ |

Download the file matching your exact Minecraft version and platform.

- **Paper / Purpur / Spigot / CraftBukkit:** put the matching JAR in `plugins/`.
- **Fabric:** install Fabric Loader and the required Fabric API, then put CCR in `mods/`. It works in singleplayer and on dedicated servers.
- **Multiplayer players:** CCR is not required on clients joining a CCR server.

Use `/ccr config` as an operator. On Fabric, the singleplayer world owner also has access. Escape returns to the previous menu and cancels unfinished input.

---

## 🎯 Designed for UHC

CutClean Reborn is built around configurable UHC mechanics rather than forcing a single ruleset.

You can enable only the systems you need and configure them independently depending on how your server runs its games.

---

## 🐛 Issues & Suggestions

If you encounter a bug, compatibility problem, or have an idea for a new configurable rule, please open an issue on the project's GitHub repository.

When reporting a problem, include:

- Your Minecraft version and server platform or mod loader
- Your CutClean Reborn version
- Any relevant error or console log
- Steps to reproduce the issue