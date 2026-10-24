package fr.isaac.cutcleanreborn.bukkit;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.view.AnvilView;

/** A zero-cost text field: Escape returns to the previous menu, invalid inputs explain themselves. */
final class AnvilTextInput<T> implements Listener {
    private static final Set<Inventory> INPUTS = Collections.newSetFromMap(new IdentityHashMap<>());
    private final CutCleanRebornPlugin plugin;
    private final String title, label, initialValue;
    private final Function<String, T> parser;
    private final BiConsumer<Player, T> onConfirm;
    private AnvilView view;
    private MenuSupport parent;
    private boolean submitted;

    AnvilTextInput(CutCleanRebornPlugin plugin, String title, String label, String initialValue,
            Function<String, T> parser, BiConsumer<Player, T> onConfirm) {
        this.plugin = plugin;
        this.title = title;
        this.label = label;
        this.initialValue = initialValue;
        this.parser = parser;
        this.onConfirm = onConfirm;
    }

    static boolean isInput(Inventory inventory) { return INPUTS.contains(inventory); }

    void open(Player player) {
        Inventory previous = player.getOpenInventory().getTopInventory();
        parent = previous.getHolder() instanceof MenuSupport menu ? menu : null;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !plugin.isEnabled() || player.getOpenInventory().getTopInventory() != previous
                    || !player.hasPermission("cutcleanreborn.config")) return;
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            view = MenuType.ANVIL.create(player, MenuItems.component(title));
            INPUTS.add(view.getTopInventory());
            view.getTopInventory().setItem(0, MenuItems.item(Material.NAME_TAG, initialValue,
                    List.of("§7Type a " + label + " in the field above.", "§7Escape: return without saving.")));
            configureCosts();
            player.openInventory(view);
            if (!ours(player.getOpenInventory().getTopInventory())) {
                discard();
                INPUTS.remove(view.getTopInventory());
                HandlerList.unregisterAll(this);
                return;
            }
            updateResult();
        });
    }

    @EventHandler public void onPrepare(PrepareAnvilEvent event) {
        if (!ours(event.getInventory())) return;
        configureCosts();
        event.setResult(resultItem());
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!ours(event.getView().getTopInventory())) return;
        event.setCancelled(true);
        if (submitted || event.getRawSlot() != 2 || !(event.getWhoClicked() instanceof Player player)) return;
        if (!player.hasPermission("cutcleanreborn.config")) {
            submitted = true;
            plugin.getServer().getScheduler().runTask(plugin, () -> BukkitCompat.closeInventory(player));
            return;
        }
        T parsed = parse();
        if (parsed == null) {
            player.sendMessage(CutCleanCommands.PREFIX + "§cInvalid " + label + ". Edit the text, or press Escape to go back.");
            updateResult();
            return;
        }
        submitted = true;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !ours(player.getOpenInventory().getTopInventory())) return;
            if (!player.hasPermission("cutcleanreborn.config")) { BukkitCompat.closeInventory(player); return; }
            discard();
            BukkitCompat.closeInventory(player);
            BukkitCompat.actionBar(player, MenuItems.component("§aSaved • " + label));
            onConfirm.accept(player, parsed);
        });
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (ours(event.getView().getTopInventory())) event.setCancelled(true);
    }

    @EventHandler public void onClose(InventoryCloseEvent event) {
        if (!ours(event.getInventory())) return;
        discard();
        INPUTS.remove(event.getInventory());
        HandlerList.unregisterAll(this);
        if (!submitted && plugin.isEnabled() && event.getPlayer() instanceof Player player && player.isOnline() && parent != null
                && !BukkitCompat.isProgrammaticClose(player)) {
            MenuSupport.returnAfterEscape(plugin, player, () -> parent.open(player));
        }
    }

    private boolean ours(Inventory inventory) { return view != null && inventory == view.getTopInventory(); }

    private void configureCosts() {
        view.setRepairCost(0);
        view.setRepairItemCountCost(0);
        view.setMaximumRepairCost(Integer.MAX_VALUE);
    }

    private T parse() {
        String text = view.getRenameText();
        try { return parser.apply(text == null ? initialValue : text); }
        catch (IllegalArgumentException exception) { return null; }
    }

    private ItemStack resultItem() {
        return parse() == null ? MenuItems.item(Material.BARRIER, "§cInvalid " + label,
                List.of("§7Edit the text in the field above.", "§7Initial example: §e" + initialValue, "§7Escape: return without saving."))
                : MenuItems.item(Material.LIME_DYE, "§aSave " + label,
                List.of("§7Click to save this value.", "§7Escape: return without saving."));
    }

    private void updateResult() { view.getTopInventory().setItem(2, resultItem()); }
    private void discard() { if (view != null) view.getTopInventory().clear(); }
}

