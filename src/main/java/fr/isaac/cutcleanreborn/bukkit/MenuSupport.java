package fr.isaac.cutcleanreborn.bukkit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Common, server-side-safe inventory menu behaviour and presentation helpers. */
abstract class MenuSupport implements InventoryHolder, Listener {
    protected final CutCleanRebornPlugin plugin;
    protected final Inventory inventory;
    private int lastClickTick = -1;
    private final java.util.Set<Integer> informationSlots = new java.util.HashSet<>();

    MenuSupport(CutCleanRebornPlugin plugin, int rows, String title) {
        this.plugin = plugin;
        inventory = Bukkit.createInventory(this, rows * 9, MenuItems.component(title));
    }

    final void open(Player player) {
        Inventory previous = player.getOpenInventory().getTopInventory();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !plugin.isEnabled() || player.getOpenInventory().getTopInventory() != previous) return;
            if (!player.hasPermission("cutcleanreborn.config")) return;
            refresh();
            decorate();
            HandlerList.unregisterAll(this);
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            player.openInventory(inventory);
            if (player.getOpenInventory().getTopInventory() != inventory) HandlerList.unregisterAll(this);
        });
    }

    static boolean isPluginView(InventoryView view) {
        return view.getTopInventory().getHolder() instanceof MenuSupport || AnvilTextInput.isInput(view.getTopInventory());
    }

    private void decorate() {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) != null) continue;
            if (slot < 9 || slot >= inventory.getSize() - 9 || slot % 9 == 0 || slot % 9 == 8)
                inventory.setItem(slot, MenuItems.item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        }
    }

    @Override public final Inventory getInventory() { return inventory; }

    @EventHandler public final void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() != this) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize() || !(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getClick().isLeftClick() && !event.getClick().isRightClick()) return;
        ItemStack item = inventory.getItem(slot);
        if (item == null || item.getType() == Material.GRAY_STAINED_GLASS_PANE || informationSlots.contains(slot)) return;
        int tick = BukkitCompat.currentTick();
        if (lastClickTick == tick) return;
        lastClickTick = tick;
        if (!player.hasPermission("cutcleanreborn.config")) {
            player.sendMessage(CutCleanCommands.PREFIX + "§cYou do not have permission to change the configuration.");
            plugin.getServer().getScheduler().runTask(plugin, () -> BukkitCompat.closeInventory(player));
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.25f, 1.2f);
        handleClick(player, slot, event.getClick());
        decorate();
    }
    @EventHandler public final void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() == this) event.setCancelled(true);
    }
    @EventHandler public final void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() != this) return;
        HandlerList.unregisterAll(this);
        if (event.getPlayer() instanceof Player player && !BukkitCompat.isProgrammaticClose(player))
            returnAfterEscape(plugin, player, () -> goBack(player));
    }

    /** Closing through Escape goes up one semantic level, never through the browsing history. */
    static void returnAfterEscape(CutCleanRebornPlugin plugin, Player player, Runnable back) {
        if (!plugin.isEnabled() || !player.isOnline()) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!plugin.isEnabled() || !player.isOnline() || player.isDead() || !player.hasPermission("cutcleanreborn.config")) return;
            InventoryView current = player.getOpenInventory();
            org.bukkit.event.inventory.InventoryType type = current.getType();
            if ((type == org.bukkit.event.inventory.InventoryType.CRAFTING || type == org.bukkit.event.inventory.InventoryType.CREATIVE)
                    && current.getTopInventory().getHolder() == player) back.run();
        });
    }

    protected MenuSupport parentMenu() { return null; }
    protected void refresh() { }

    protected final void clearMenu() {
        informationSlots.clear();
        inventory.clear();
    }

    protected void goBack(Player player) {
        MenuSupport parent = parentMenu();
        if (parent != null) parent.open(player);
    }

    protected final void information(int slot, ItemStack item) {
        informationSlots.add(slot);
        inventory.setItem(slot, item);
    }

    protected final void header(Material icon, String title, String... description) {
        information(4, MenuItems.item(icon, title, List.of(description)));
    }

    protected final void footer(String destination) {
        int start = inventory.getSize() - 9;
        inventory.setItem(start, MenuItems.item(Material.ARROW, "§eBack • " + destination,
                List.of("§7Esc")));
        inventory.setItem(start + 8, MenuItems.item(Material.BARRIER, "§cClose CCR",
                List.of()));
    }

    protected final boolean handleNavigation(Player player, int slot) {
        if (slot == inventory.getSize() - 9) { goBack(player); return true; }
        if (slot == inventory.getSize() - 1) {
            plugin.getServer().getScheduler().runTask(plugin, () -> BukkitCompat.closeInventory(player));
            return true;
        }
        return false;
    }

    final void confirm(Player player, String title, Runnable action, java.util.function.Consumer<Player> back) {
        new ConfirmationMenu(plugin, title, target -> { action.run(); back.accept(target); }, back).open(player);
    }

    abstract void handleClick(Player player, int slot, ClickType click);
}

final class MenuItems {
    private MenuItems() { }
    static String component(String text) { return text; }
    static ItemStack pageInfo(int page, int pages) {
        return item(Material.BOOK, "§6Page " + (page + 1) + " / " + pages, List.of());
    }
    static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName("§r" + name);
        meta.setLore(lore.stream().map(line -> "§r" + line).toList());
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        stack.setItemMeta(meta);
        return stack;
    }
    /** Keeps the meaningful item icon and uses the enchantment glint as the ON state. */
    static ItemStack stateItem(Material material, String name, List<String> lore, boolean enabled) {
        ItemStack stack = item(material, name, lore);
        if (enabled) {
            ItemMeta meta = stack.getItemMeta();
            meta.setEnchantmentGlintOverride(true);
            stack.setItemMeta(meta);
        }
        return stack;
    }
    static ItemStack toggle(String title, boolean enabled, boolean available, String requirement, String... description) {
        List<String> lore = new ArrayList<>(List.of(description));
        lore.add("");
        lore.add("§7Saved setting: " + CutCleanCommands.state(enabled));
        if (enabled && !available) lore.add("§ePaused • enable " + requirement + " first.");
        lore.add(enabled ? "§eClick to disable." : "§eClick to enable.");
        Material icon = !enabled ? Material.GRAY_DYE : available ? Material.LIME_DYE : Material.ORANGE_DYE;
        return item(icon, "§6" + title + " §8• " + effectiveState(enabled, available), lore);
    }
    static String effectiveState(boolean enabled, boolean available) {
        return !enabled ? "§cOFF" : available ? "§aON" : "§ePAUSED";
    }
    static ItemStack mobItem(EntityType type, List<String> lore) {
        ItemStack stack = item(mobIcon(type), "§6" + pretty(type), lore);
        ItemMeta meta = stack.getItemMeta();
        // A translatable component lets each client render the Minecraft entity name in its own language.
        java.util.Map<String, Object> serialized = new java.util.HashMap<>(meta.serialize());
        serialized.put("==", "ItemMeta");
        serialized.put("display-name", "{\"translate\":\"entity." + type.getKey().getNamespace() + "." + type.getKey().getKey() + "\",\"color\":\"gold\",\"italic\":false}");
        meta = (ItemMeta) org.bukkit.configuration.serialization.ConfigurationSerialization.deserializeObject(serialized);
        stack.setItemMeta(meta);
        return stack;
    }
    static Material mobIcon(EntityType type) {
        Material egg = Material.matchMaterial(type.name() + "_SPAWN_EGG");
        return egg != null && egg.isItem() ? egg : Material.SPAWNER;
    }
    static String pretty(EntityType type) {
        String[] words = type.getKey().getKey().replace('-', '_').split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
    static String prettyName(Material material) {
        String[] words = material.getKey().getKey().split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
    static String prettyName(Enchantment enchantment) {
        String[] words = enchantment.getKey().getKey().split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
    static List<EntityType> configurableMobs() {
        List<EntityType> result = new ArrayList<>();
        for (EntityType type : EntityType.values()) {
            Class<?> entityClass = type.getEntityClass();
            // Keep technical/non-natural living entities configurable too (armor stands, giants, etc.),
            // but the menu ordering puts them after normal UHC mobs.
            if (type != EntityType.PLAYER && entityClass != null
                    && LivingEntity.class.isAssignableFrom(entityClass)) result.add(type);
        }
        result.sort(Comparator.comparing(type -> type.getKey().toString()));
        return result;
    }
    static EntityType findMob(String input) {
        if (input == null) return null;
        String query = input.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "").replace(' ', '_');
        List<EntityType> choices = configurableMobs();
        for (EntityType type : choices) if (type.getKey().getKey().equals(query)) return type;
        List<EntityType> matches = choices.stream().filter(type -> type.getKey().getKey().contains(query)).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }
    /** Natural UHC mobs first; technical or non-natural entities deliberately go at the end. */
    static int mobPriority(EntityType type) {
        if (!type.isSpawnable()) return 5;
        return switch (type) {
            // The animals whose food/leather drops are normally configured in UHC.
            case COW, SHEEP, PIG, CHICKEN, RABBIT, MOOSHROOM, GOAT, COD, SALMON -> 0;
            // Usual overworld, Nether and End hostile drops.
            case ZOMBIE, SKELETON, CREEPER, SPIDER, CAVE_SPIDER, ENDERMAN, WITCH, SLIME, MAGMA_CUBE,
                    BLAZE, GHAST, WITHER_SKELETON, PIGLIN, ZOMBIFIED_PIGLIN, HOGLIN, DROWNED, HUSK,
                    STRAY, PHANTOM, SILVERFISH, ENDERMITE, PILLAGER, VINDICATOR, EVOKER, RAVAGER -> 1;
            // Other naturally occurring passive/neutral mobs.
            case HORSE, DONKEY, MULE, LLAMA, TRADER_LLAMA, WOLF, CAT, OCELOT, FOX, PANDA, POLAR_BEAR,
                    TURTLE, DOLPHIN, SQUID, GLOW_SQUID, BEE, CAMEL, SNIFFER, FROG, TADPOLE, PARROT,
                    AXOLOTL, ALLAY -> 2;
            // Less common natural mobs and bosses remain before technical entities.
            default -> 3;
        };
    }
    static Material findItem(String query) {
        if (query == null || query.isBlank()) return null;
        String value = query.trim().toLowerCase(Locale.ROOT);
        Material direct = Material.matchMaterial(value);
        if (direct == null && value.startsWith("minecraft:")) direct = Material.matchMaterial(value.substring("minecraft:".length()));
        if (direct != null && direct.isItem()) return direct;
        String simple = value.contains(":") ? value.substring(value.indexOf(':') + 1) : value;
        Material candidate = null;
        for (Material material : Material.values()) {
            if (!material.isItem()) continue;
            String key = material.getKey().getKey();
            if (key.equals(simple)) return material;
            if (key.contains(simple)) {
                if (candidate != null) return null; // avoid silently choosing an ambiguous search result
                candidate = material;
            }
        }
        return candidate;
    }
    static String number(double value) {
        if (value == Math.rint(value)) return String.valueOf((int) value);
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}

record MobMenuContext(EntityType mob, int page) { }

