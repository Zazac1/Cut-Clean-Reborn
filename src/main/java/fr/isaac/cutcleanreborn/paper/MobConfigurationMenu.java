package fr.isaac.cutcleanreborn.paper;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** The focused configuration menu for one mob. */
final class MobConfigurationMenu extends MenuSupport {
    static final int CENTER_SLOT = 22;
    static final int DROPS_SLOT = 20;
    static final int XP_SLOT = 24;
    static final int COOKED_FOOD_SLOT = 31;
    static final int BACK_SLOT = 40;
    private final MobMenuContext context;

    MobConfigurationMenu(CutCleanRebornPlugin plugin, MobMenuContext context) {
        super(plugin, 5, "§8CCR • " + MenuItems.pretty(context.mob()));
        this.context = context;
        render();
    }
    private void render() {
        boolean cooked = plugin.getMobCookedFood(context.mob());
        int xp = plugin.getMobXpBonus(context.mob());
        int dropCount = plugin.getMobDrops(context.mob()).size();
        String name = MenuItems.pretty(context.mob());
        inventory.setItem(CENTER_SLOT, MenuItems.item(MenuItems.mobIcon(context.mob()), "§6" + name,
                List.of("§7XP Bonus: §e" + xp + " XP", "§7Cooked food: " + (cooked ? "§aEnabled" : "§cDisabled"),
                        "§7Custom drops: §e" + dropCount, "", "§eClick to return to mob selection")));
        inventory.setItem(DROPS_SLOT, MenuItems.item(Material.CHEST, "§6Drops",
                List.of("§7Manage custom item drops.", "", "§eClick to open")));
        inventory.setItem(XP_SLOT, MenuItems.item(Material.EXPERIENCE_BOTTLE, "§6XP Bonus",
                List.of("§7Current value: §e" + xp + " XP", "", "§eLeft click to edit")));
        inventory.setItem(COOKED_FOOD_SLOT, MenuItems.item(cooked ? Material.COOKED_BEEF : Material.COAL, "§6Cooked Food",
                List.of("§7Status: " + (cooked ? "§aEnabled" : "§cDisabled"), "", "§eClick to " + (cooked ? "disable" : "enable"))));
        inventory.setItem(BACK_SLOT, MenuItems.item(Material.BARRIER, "§cBack", List.of("§7Return to mob selection.")));
    }
    @Override void handleClick(Player player, int slot, ClickType click) {
        if (slot == DROPS_SLOT) new DropListMenu(plugin, context).open(player);
        else if (slot == XP_SLOT) new AnvilTextInput<Integer>(plugin, "§8CCR • XP Bonus", "XP bonus", String.valueOf(plugin.getMobXpBonus(context.mob())),
                MobConfigurationMenu::positiveInteger, (target, value) -> {
                    plugin.setMobXpBonus(context.mob(), value);
                    new MobConfigurationMenu(plugin, context).open(target);
                }).open(player);
        else if (slot == COOKED_FOOD_SLOT) {
            plugin.setMobCookedFood(context.mob(), !plugin.getMobCookedFood(context.mob()));
            render();
        } else if (slot == CENTER_SLOT || slot == BACK_SLOT) new MobListMenu(plugin, context.page()).open(player);
    }
    static Integer positiveInteger(String input) {
        try {
            int value = Integer.parseInt(input.trim());
            return value >= 0 && value <= 1_000_000 ? value : null;
        } catch (NumberFormatException ignored) { return null; }
    }
}
