package fr.isaac.cutcleanreborn.paper;

import java.util.function.BiConsumer;
import java.util.function.Function;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;

/** A real Paper AnvilView used only as a zero-cost server-side text field. */
final class AnvilTextInput<T> implements Listener {
    private final CutCleanRebornPlugin plugin;
    private final String title;
    private final String label;
    private final String initialValue;
    private final Function<String, T> parser;
    private final BiConsumer<Player, T> onConfirm;
    private AnvilView view;

    AnvilTextInput(CutCleanRebornPlugin plugin, String title, String label, String initialValue,
            Function<String, T> parser, BiConsumer<Player, T> onConfirm) {
        this.plugin = plugin;
        this.title = title;
        this.label = label;
        this.initialValue = initialValue;
        this.parser = parser;
        this.onConfirm = onConfirm;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    void open(Player player) {
        // Opening a container inside another inventory-click transaction is not safe on Paper.
        Bukkit.getScheduler().runTask(plugin, () -> {
            view = MenuType.ANVIL.create(player, LegacyComponentSerializer.legacySection().deserialize(title));
            AnvilInventory inventory = view.getTopInventory();
            ItemStack input = new ItemStack(Material.NAME_TAG);
            ItemMeta meta = input.getItemMeta();
            meta.setDisplayName(initialValue);
            input.setItemMeta(meta);
            inventory.setItem(0, input);
            configureCosts(view);
            view.open();
        });
    }

    @EventHandler public void onPrepare(PrepareAnvilEvent event) {
        if (event.getView() != view) return;
        configureCosts(view);
        T parsed = parseRenameText();
        if (parsed == null) {
            event.setResult(null);
            return;
        }
        ItemStack confirm = new ItemStack(Material.LIME_DYE);
        ItemMeta meta = confirm.getItemMeta();
        meta.setDisplayName("§aConfirm " + label);
        confirm.setItemMeta(meta);
        event.setResult(confirm);
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (event.getView() != view) return;
        event.setCancelled(true);
        if (event.getRawSlot() != 2 || !(event.getWhoClicked() instanceof Player player)) return;
        if (!player.hasPermission("cutcleanreborn.config")) {
            player.sendMessage("§cYou do not have permission to change the configuration.");
            player.closeInventory();
            return;
        }
        T parsed = parseRenameText();
        if (parsed == null) {
            player.sendMessage("§cInvalid " + label + ".");
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            discardInput();
            player.closeInventory();
            onConfirm.accept(player, parsed);
        });
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView() == view) event.setCancelled(true);
    }

    @EventHandler public void onClose(InventoryCloseEvent event) {
        if (event.getView() == view) {
            discardInput();
            HandlerList.unregisterAll(this);
        }
    }

    private static void configureCosts(AnvilView anvil) {
        anvil.setRepairCost(0);
        anvil.setRepairItemCountCost(0);
        anvil.setMaximumRepairCost(0);
    }

    private T parseRenameText() {
        String text = view.getRenameText();
        return text == null ? null : parser.apply(text);
    }

    private void discardInput() {
        if (view != null) view.getTopInventory().clear();
    }
}
