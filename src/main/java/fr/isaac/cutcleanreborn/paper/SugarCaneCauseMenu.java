package fr.isaac.cutcleanreborn.paper;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** A single cause with three independent controls: enable, probability and final quantity. */
final class SugarCaneCauseMenu extends MenuSupport {
    private final BetterSugarCaneService.Cause cause;

    SugarCaneCauseMenu(CutCleanRebornPlugin plugin, BetterSugarCaneService.Cause cause) {
        super(plugin, 3, cause == BetterSugarCaneService.Cause.PLAYER ? "§8Cane • Player Break" : "§8Cane • Support Break");
        this.cause = cause;
        refresh();
    }

    @Override protected MenuSupport parentMenu() { return new BetterSugarCaneMenu(plugin); }

    @Override protected void refresh() {
        clearMenu();
        BetterSugarCaneService service = plugin.betterSugarCane();
        boolean playerBreak = cause == BetterSugarCaneService.Cause.PLAYER;
        header(playerBreak ? Material.IRON_HOE : Material.DIRT, playerBreak ? "§bPlayer Break" : "§bSupport Break",
                playerBreak ? "§7A player directly breaks the cane." : "§7The cane loses support and falls.",
                "§7The roll is evaluated for each naturally grown block.");
        inventory.setItem(11, MenuItems.toggle("Enable This Break Type", service.causeEnabled(cause), service.enabled(), "Sugar Cane Bonuses",
                "§7Only controls the break type named above."));
        inventory.setItem(13, MenuItems.item(Material.GOLD_NUGGET, "§6Bonus Chance §8• §e" + MenuItems.number(service.chance(cause)) + "%",
                List.of("§7Chance to use the configured quantity.", "§7An unsuccessful roll gives 1 cane.", "", "§eClick to edit (0-100%).")));
        inventory.setItem(15, MenuItems.item(Material.CHEST, "§6Final Quantity §8• §e" + service.minimum(cause) + "-" + service.maximum(cause),
                List.of("§7Total cane dropped on a successful roll.", "§7This replaces the normal drop; it is not added.", "§7Example: 2-4, or 3 for a fixed quantity.", "", "§eClick to edit (1-64).")));
        footer("Better Sugar Cane");
    }

    @Override void handleClick(Player player, int slot, ClickType click) {
        if (handleNavigation(player, slot)) return;
        BetterSugarCaneService service = plugin.betterSugarCane();
        if (slot == 11) { service.setCauseEnabled(cause, !service.causeEnabled(cause)); refresh(); }
        else if (slot == 13) new AnvilTextInput<Double>(plugin, "§8Cane • Bonus Chance", "chance (0-100%)",
                MenuItems.number(service.chance(cause)), GameplayMath::percentage,
                (target, value) -> { service.setChance(cause, value); new SugarCaneCauseMenu(plugin, cause).open(target); }).open(player);
        else if (slot == 15) new AnvilTextInput<GameplayMath.AmountRange>(plugin, "§8Cane • Final Quantity", "quantity (1-64)",
                service.minimum(cause) + "-" + service.maximum(cause), text -> GameplayMath.range(text, 1, 64),
                (target, value) -> { service.setRange(cause, value.minimum(), value.maximum()); new SugarCaneCauseMenu(plugin, cause).open(target); }).open(player);
    }
}
