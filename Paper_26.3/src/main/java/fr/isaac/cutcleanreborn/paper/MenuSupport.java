package fr.isaac.cutcleanreborn.paper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Common, server-side-safe inventory menu behaviour and presentation helpers. */
abstract class MenuSupport implements InventoryHolder, Listener {
    protected final CutCleanRebornPlugin plugin;
    protected final Inventory inventory;

    MenuSupport(CutCleanRebornPlugin plugin, int rows, String title) {
        this.plugin = plugin;
        inventory = Bukkit.createInventory(this, rows * 9, title);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    final void open(Player player) { player.openInventory(inventory); }
    @Override public final Inventory getInventory() { return inventory; }

    @EventHandler public final void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() != this) return;
        // This covers shift, double clicks, hotbar/number keys and offhand swaps too.
        event.setCancelled(true);
        if (event.getRawSlot() >= inventory.getSize() || !(event.getWhoClicked() instanceof Player player)) return;
        if (!player.hasPermission("cutcleanreborn.config")) {
            player.sendMessage("§cYou do not have permission to change the configuration.");
            player.closeInventory();
            return;
        }
        handleClick(player, event.getRawSlot(), event.getClick());
    }
    @EventHandler public final void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() == this) event.setCancelled(true);
    }
    @EventHandler public final void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() == this) HandlerList.unregisterAll(this);
    }
    abstract void handleClick(Player player, int slot, ClickType click);
}

final class MenuItems {
    private MenuItems() { }
    static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }
    /** Keeps the meaningful item icon and uses the enchantment glint as the ON state. */
    static ItemStack stateItem(Material material, String name, List<String> lore, boolean enabled) {
        ItemStack stack = item(material, name, lore);
        if (enabled) {
            ItemMeta meta = stack.getItemMeta();
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        }
        return stack;
    }
    static ItemStack mobItem(EntityType type, List<String> lore) {
        ItemStack stack = item(mobIcon(type), "§6" + pretty(type), lore);
        ItemMeta meta = stack.getItemMeta();
        // A translatable component lets each client render the Minecraft entity name in its own language.
        meta.displayName(Component.translatable("entity." + type.getKey().getNamespace() + "." + type.getKey().getKey())
                .color(NamedTextColor.GOLD));
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
