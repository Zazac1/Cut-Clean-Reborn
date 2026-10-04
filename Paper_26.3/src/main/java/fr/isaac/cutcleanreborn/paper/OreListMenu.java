package fr.isaac.cutcleanreborn.paper;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Ore family selection. */
final class OreListMenu extends MenuSupport {
    private static final int AUTO_SMELT_SLOT = 40;
    private static final int FORTUNE_VANILLA_SLOT = 41;
    private static final int FORTUNE_CUSTOM_SLOT = 42;
    private final int page;
    OreListMenu(CutCleanRebornPlugin plugin, int requestedPage) {
        super(plugin, 6, "§8CCR • Ore Drops");
        int pageCount = Math.max(1, (OreCatalog.ORES.size() + MobListMenu.PAGE_SIZE - 1) / MobListMenu.PAGE_SIZE);
        page = Math.clamp(requestedPage, 0, pageCount - 1);
        int start = page * MobListMenu.PAGE_SIZE;
        for (int i = 0; i < MobListMenu.PAGE_SIZE && start + i < OreCatalog.ORES.size(); i++) {
            OreDefinition ore = OreCatalog.ORES.get(start + i);
            inventory.setItem(MobListMenu.MOB_SLOTS[i], MenuItems.item(ore.stone(), "§6" + ore.name(),
                    List.of(ore.deepslate() == null ? "§7One block variant." : "§7Stone and deepslate variants.", "", "§eClick to configure")));
        }
        boolean autoSmelt = plugin.oreAutoSmeltEnabled();
        inventory.setItem(AUTO_SMELT_SLOT, MenuItems.stateItem(Material.FURNACE,
                autoSmelt ? "§aAuto Smelt Ores: ON" : "§cAuto Smelt Ores: OFF",
                List.of("§7Drop iron, gold, copper and ancient debris", "§7already smelted, with furnace XP on the ground.", "",
                        autoSmelt ? "§eClick to disable" : "§eClick to enable"), autoSmelt));
        fortuneToggle(FORTUNE_VANILLA_SLOT, plugin.oreFortuneVanillaEnabled(), "Fortune on vanilla drops");
        fortuneToggle(FORTUNE_CUSTOM_SLOT, plugin.oreFortuneCustomEnabled(), "Fortune on extra drops");
        inventory.setItem(MobListMenu.BACK_SLOT, MenuItems.item(Material.BARRIER, "§cBack", List.of("§7Return to CCR Config.")));
        if (page > 0) inventory.setItem(MobListMenu.PREVIOUS_PAGE_SLOT, MenuItems.item(Material.ARROW, "§ePrevious page", List.of()));
        if (page + 1 < pageCount) inventory.setItem(MobListMenu.NEXT_PAGE_SLOT, MenuItems.item(Material.ARROW, "§eNext page", List.of()));
    }
    @Override void handleClick(Player player, int slot, ClickType click) {
        if (slot == MobListMenu.BACK_SLOT) { new ConfigMainMenu(plugin).open(player); return; }
        if (slot == AUTO_SMELT_SLOT) { plugin.setOreAutoSmeltEnabled(!plugin.oreAutoSmeltEnabled()); new OreListMenu(plugin, page).open(player); return; }
        if (slot == FORTUNE_VANILLA_SLOT) { plugin.setOreFortuneVanillaEnabled(!plugin.oreFortuneVanillaEnabled()); new OreListMenu(plugin, page).open(player); return; }
        if (slot == FORTUNE_CUSTOM_SLOT) { plugin.setOreFortuneCustomEnabled(!plugin.oreFortuneCustomEnabled()); new OreListMenu(plugin, page).open(player); return; }
        if (slot == MobListMenu.PREVIOUS_PAGE_SLOT && page > 0) { new OreListMenu(plugin, page - 1).open(player); return; }
        if (slot == MobListMenu.NEXT_PAGE_SLOT && (page + 1) * MobListMenu.PAGE_SIZE < OreCatalog.ORES.size()) { new OreListMenu(plugin, page + 1).open(player); return; }
        int index = page * MobListMenu.PAGE_SIZE;
        for (int i = 0; i < MobListMenu.MOB_SLOTS.length; i++) if (slot == MobListMenu.MOB_SLOTS[i] && index + i < OreCatalog.ORES.size()) {
            new OreConfigurationMenu(plugin, OreCatalog.ORES.get(index + i), page).open(player); return;
        }
    }
    private void fortuneToggle(int slot, boolean enabled, String label) {
        inventory.setItem(slot, MenuItems.stateItem(Material.BOOK,
                (enabled ? "§a" : "§c") + label + ": " + (enabled ? "ON" : "OFF"),
                List.of("§7Applies the held pickaxe's Fortune", "§7to this configured drop type.", "", enabled ? "§eClick to disable" : "§eClick to enable"), enabled));
    }
}
