package fr.isaac.cutcleanreborn.bukkit;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Makes inherited animal defaults visible instead of hiding them exclusively in YAML. */
final class MobGlobalSettingsMenu extends MenuSupport {
    @Override protected MenuSupport parentMenu() { return new MobListMenu(plugin, page); }

    private final int page;

    MobGlobalSettingsMenu(CutCleanRebornPlugin plugin, int page) {
        super(plugin, 4, "§8CCR • Global Mob Settings");
        this.page = page;
        render();
    }

    private void render() {
        boolean cook = plugin.getConfig().getBoolean("animal-drops.cook", true);
        inventory.setItem(10, MenuItems.stateItem(Material.COOKED_BEEF, "§6Animal Cooking: " + CutCleanCommands.state(cook),
                List.of("§7Default for food animals and fish.", "§7Per-mob settings take priority."), cook));
        inventory.setItem(12, MenuItems.item(Material.CHEST, "§6Animal Drop Multiplier", List.of("§7Current: §e" + plugin.getBoundedInt("animal-drops.multiplier", 1, 1, 5), "§7Valid range: 1 to 5.")));
        inventory.setItem(14, MenuItems.item(Material.EXPERIENCE_BOTTLE, "§6Animal XP Bonus", List.of("§7Current: §e" + plugin.getBoundedInt("animal-drops.xp-bonus", 0, 0, 50), "§7Default bonus; per-mob XP takes priority.")));
        inventory.setItem(16, MenuItems.item(Material.HOPPER, "§6Automatic Collection Range", List.of("§7Current: §e" + plugin.getBoundedInt("animal-drops.collection-range", 0, 0, 32), "§7All mob drops go to their nearby killer.", "§70 disables collection; max 32 blocks.")));
        inventory.setItem(20, MenuItems.stateItem(Material.BOOK, "§6Looting • Edited Vanilla: " + CutCleanCommands.state(plugin.mobLootingVanillaEnabled()),
                List.of("§7Global setting for all mob overrides.", "§7Adds a random 0 to Looting level."), plugin.mobLootingVanillaEnabled()));
        inventory.setItem(24, MenuItems.stateItem(Material.BOOK, "§6Looting • Extra Drops: " + CutCleanCommands.state(plugin.mobLootingCustomEnabled()),
                List.of("§7Global setting for all extra mob drops.", "§7Adds a random 0 to Looting level."), plugin.mobLootingCustomEnabled()));
        inventory.setItem(31, MenuItems.item(Material.BARRIER, "§cBack", List.of("§7Return to mob selection.")));
    }

    @Override void handleClick(Player player, int slot, ClickType click) {
        if (slot == 10) { plugin.setConfigValue("animal-drops.cook", !plugin.getConfig().getBoolean("animal-drops.cook", true)); render(); }
        else if (slot == 12) input(player, "multiplier", 1, 1, 5);
        else if (slot == 14) input(player, "xp-bonus", 0, 0, 50);
        else if (slot == 16) input(player, "collection-range", 0, 0, 32);
        else if (slot == 20) { plugin.setMobLootingVanillaEnabled(!plugin.mobLootingVanillaEnabled()); render(); }
        else if (slot == 24) { plugin.setMobLootingCustomEnabled(!plugin.mobLootingCustomEnabled()); render(); }
        else if (slot == 31) new MobListMenu(plugin, page).open(player);
    }

    private void input(Player player, String setting, int fallback, int min, int max) {
        new AnvilTextInput<Integer>(plugin, "§8CCR • Animal Setting", setting + " (" + min + "-" + max + ")",
                String.valueOf(plugin.getBoundedInt("animal-drops." + setting, fallback, min, max)), value -> GameplayMath.integer(value, min, max),
                (target, value) -> { plugin.setConfigValue("animal-drops." + setting, value); new MobGlobalSettingsMenu(plugin, page).open(target); }).open(player);
    }
}

