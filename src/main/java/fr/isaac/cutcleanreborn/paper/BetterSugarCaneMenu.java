package fr.isaac.cutcleanreborn.paper;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Configuration screen for player and support-loss sugar-cane rolls. */
final class BetterSugarCaneMenu extends MenuSupport {
    private static final int ENABLE_SLOT = 10, PLAYER_ENABLE_SLOT = 12, SUPPORT_ENABLE_SLOT = 14;
    private static final int PLAYER_CHANCE_SLOT = 19, PLAYER_RANGE_SLOT = 21, SUPPORT_CHANCE_SLOT = 23, SUPPORT_RANGE_SLOT = 25;
    private static final int RESET_SLOT = 30, BACK_SLOT = 31;
    BetterSugarCaneMenu(CutCleanRebornPlugin plugin) { super(plugin, 4, "§8CCR • Better Sugar Cane"); render(); }
    private BetterSugarCaneService service() { return plugin.betterSugarCane(); }
    private void render() {
        BetterSugarCaneService service = service();
        inventory.setItem(ENABLE_SLOT, toggle("Better Sugar Cane", service.enabled(), "§7Enable all Better Sugar Cane drops."));
        inventory.setItem(PLAYER_ENABLE_SLOT, toggle("Player Break", service.causeEnabled(BetterSugarCaneService.Cause.PLAYER), "§7Apply the roll when a player breaks", "§7a sugar-cane block."));
        inventory.setItem(SUPPORT_ENABLE_SLOT, toggle("Support Break", service.causeEnabled(BetterSugarCaneService.Cause.SUPPORT), "§7Apply the roll when sugar cane", "§7breaks from missing support."));
        setting(PLAYER_CHANCE_SLOT, Material.GOLD_NUGGET, "Player Chance", service.chance(BetterSugarCaneService.Cause.PLAYER));
        range(PLAYER_RANGE_SLOT, "Player Range", BetterSugarCaneService.Cause.PLAYER);
        setting(SUPPORT_CHANCE_SLOT, Material.GOLD_NUGGET, "Support Chance", service.chance(BetterSugarCaneService.Cause.SUPPORT));
        range(SUPPORT_RANGE_SLOT, "Support Range", BetterSugarCaneService.Cause.SUPPORT);
        inventory.setItem(RESET_SLOT, MenuItems.item(Material.CLOCK, "§eReset Defaults", List.of("§7Restore the default chance and ranges.")));
        inventory.setItem(BACK_SLOT, MenuItems.item(Material.BARRIER, "§cBack", List.of("§7Return to CCR Config.")));
    }
    private org.bukkit.inventory.ItemStack toggle(String title, boolean on, String... description) {
        List<String> lore = new java.util.ArrayList<>(List.of(description)); lore.add(""); lore.add(on ? "§eClick to disable" : "§eClick to enable");
        return MenuItems.stateItem(Material.SUGAR_CANE, (on ? "§a" : "§c") + title + ": " + (on ? "ON" : "OFF"), lore, on);
    }
    private void setting(int slot, Material icon, String title, double chance) {
        inventory.setItem(slot, MenuItems.item(icon, "§6" + title, List.of("§7Current: §e" + MenuItems.number(chance) + "%", "", "§eClick to edit")));
    }
    private void range(int slot, String title, BetterSugarCaneService.Cause cause) {
        BetterSugarCaneService service = service();
        inventory.setItem(slot, MenuItems.item(Material.COMPARATOR, "§6" + title, List.of("§7Current: §e" + service.minimum(cause) + "-" + service.maximum(cause), "", "§eClick to edit")));
    }
    @Override void handleClick(Player player, int slot, ClickType click) {
        BetterSugarCaneService service = service();
        if (slot == ENABLE_SLOT) { service.setEnabled(!service.enabled()); render(); }
        else if (slot == PLAYER_ENABLE_SLOT) { service.setCauseEnabled(BetterSugarCaneService.Cause.PLAYER, !service.causeEnabled(BetterSugarCaneService.Cause.PLAYER)); render(); }
        else if (slot == SUPPORT_ENABLE_SLOT) { service.setCauseEnabled(BetterSugarCaneService.Cause.SUPPORT, !service.causeEnabled(BetterSugarCaneService.Cause.SUPPORT)); render(); }
        else if (slot == PLAYER_CHANCE_SLOT) chanceInput(BetterSugarCaneService.Cause.PLAYER).open(player);
        else if (slot == SUPPORT_CHANCE_SLOT) chanceInput(BetterSugarCaneService.Cause.SUPPORT).open(player);
        else if (slot == PLAYER_RANGE_SLOT) rangeInput(BetterSugarCaneService.Cause.PLAYER).open(player);
        else if (slot == SUPPORT_RANGE_SLOT) rangeInput(BetterSugarCaneService.Cause.SUPPORT).open(player);
        else if (slot == RESET_SLOT) { service.reset(); render(); }
        else if (slot == BACK_SLOT) new ConfigMainMenu(plugin).open(player);
    }
    private AnvilTextInput<Double> chanceInput(BetterSugarCaneService.Cause cause) {
        return new AnvilTextInput<>(plugin, "§8CCR • Sugar Cane Chance", "chance (%)", MenuItems.number(service().chance(cause)), BetterSugarCaneMenu::parseChance,
                (target, value) -> { service().setChance(cause, value); new BetterSugarCaneMenu(plugin).open(target); });
    }
    private AnvilTextInput<Range> rangeInput(BetterSugarCaneService.Cause cause) {
        BetterSugarCaneService service = service();
        return new AnvilTextInput<>(plugin, "§8CCR • Sugar Cane Range", "range", service.minimum(cause) + "-" + service.maximum(cause), BetterSugarCaneMenu::parseRange,
                (target, value) -> { service().setRange(cause, value.minimum(), value.maximum()); new BetterSugarCaneMenu(plugin).open(target); });
    }
    private static Double parseChance(String input) { try { double value = Double.parseDouble(input.replace("%", "").trim()); return Double.isFinite(value) && value >= 0 && value <= 100 ? value : null; } catch (NumberFormatException ignored) { return null; } }
    private static Range parseRange(String input) {
        String[] values = input.trim().split("-", -1); if (values.length == 0 || values.length > 2) return null;
        try { int min = Integer.parseInt(values[0].trim()), max = values.length == 1 ? min : Integer.parseInt(values[1].trim()); return min >= 1 && max >= min && max <= 64 ? new Range(min, max) : null; } catch (NumberFormatException ignored) { return null; }
    }
    private record Range(int minimum, int maximum) { }
}
