package fr.isaac.cutcleanreborn.bukkit;

import java.util.List;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Explicit confirmation for resets, copies that overwrite settings and rule deletion. */
final class ConfirmationMenu extends MenuSupport {
    private final Consumer<Player> confirmed;
    private final Consumer<Player> cancelled;
    private boolean used;

    ConfirmationMenu(CutCleanRebornPlugin plugin, String title, Consumer<Player> confirmed, Consumer<Player> cancelled) {
        super(plugin, 3, "§8CCR • Confirm");
        this.confirmed = confirmed;
        this.cancelled = cancelled;
        inventory.setItem(4, MenuItems.item(Material.PAPER, "§e" + title, List.of("§7Review this change before applying it.")));
        inventory.setItem(11, MenuItems.item(Material.LIME_CONCRETE, "§aConfirm", List.of("§7Apply this change.")));
        inventory.setItem(15, MenuItems.item(Material.RED_CONCRETE, "§cCancel", List.of("§7Keep the current settings.")));
    }

    @Override void handleClick(Player player, int slot, ClickType click) {
        if (used || (slot != 11 && slot != 15)) return;
        used = true;
        if (slot == 11) confirmed.accept(player);
        else cancelled.accept(player);
    }

    @Override protected void goBack(Player player) {
        if (!used) { used = true; cancelled.accept(player); }
    }
}

