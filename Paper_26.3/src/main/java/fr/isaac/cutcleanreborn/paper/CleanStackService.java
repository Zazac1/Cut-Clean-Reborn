package fr.isaac.cutcleanreborn.paper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;

/** Personal stack normalisation for wood and decorative stone families. */
final class CleanStackService implements Listener {
    enum Category { WOOD("Wood"), STONE("Stone"); private final String title; Category(String title) { this.title = title; } String title() { return title; } }
    private static final List<Material> WOOD = materials(Category.WOOD);
    private static final List<Material> STONE = materials(Category.STONE);
    private final CutCleanRebornPlugin plugin;
    CleanStackService(CutCleanRebornPlugin plugin) { this.plugin = plugin; plugin.getServer().getPluginManager().registerEvents(this, plugin); }
    boolean enabled(Player player) { return plugin.getConfig().getBoolean("clean-stacks.players." + player.getUniqueId(), false); }
    void setEnabled(Player player, boolean enabled) {
        plugin.setConfigValue("clean-stacks.players." + player.getUniqueId(), enabled);
        if (enabled) normaliseInventory(player);
    }
    List<Material> sources(Category category) { return category == Category.WOOD ? WOOD : STONE; }
    Material target(Category category, Material source) {
        String configured = plugin.getConfig().getString(path(category, source));
        Material result = configured == null ? defaultTarget(category, source) : MenuItems.findItem(configured);
        return result == null ? defaultTarget(category, source) : result;
    }
    void setTarget(Category category, Material source, Material target) { plugin.setConfigValue(path(category, source), target.getKey().toString()); }
    void resetTarget(Category category, Material source) { plugin.setConfigValue(path(category, source), null); }
    ItemStack normalise(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return stack;
        Category category = categoryOf(stack.getType());
        if (category == null) return stack;
        ItemStack output = stack.clone(); output.setType(target(category, stack.getType())); return output;
    }
    @EventHandler(ignoreCancelled = true)
    public void onBlockDrops(BlockDropItemEvent event) {
        if (!enabled(event.getPlayer())) return;
        event.getItems().forEach(item -> item.setItemStack(normalise(item.getItemStack())));
    }
    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && enabled(player)) event.getItem().setItemStack(normalise(event.getItem().getItemStack()));
    }
    @EventHandler
    public void onCraftPreview(PrepareItemCraftEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player) || !enabled(player)) return;
        ItemStack result = event.getInventory().getResult();
        if (result != null && categoryOf(result.getType()) == Category.WOOD && result.getType().name().endsWith("_PLANKS"))
            event.getInventory().setResult(normalise(result));
    }
    private void normaliseInventory(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) contents[i] = normalise(contents[i]);
        player.getInventory().setContents(contents);
    }
    private static String path(Category category, Material source) { return "clean-stacks." + category.name().toLowerCase() + "." + source.name(); }
    private static Material defaultTarget(Category category, Material source) {
        return category == Category.STONE ? Material.STONE : source.name().endsWith("_PLANKS") ? Material.OAK_PLANKS : Material.OAK_LOG;
    }
    private static Category categoryOf(Material material) {
        if (WOOD.contains(material)) return Category.WOOD;
        return STONE.contains(material) ? Category.STONE : null;
    }
    private static List<Material> materials(Category category) {
        List<Material> result = new ArrayList<>();
        for (Material material : Material.values()) {
            String name = material.name();
            // Keep the actual natural tree blocks, but not crafted *_WOOD blocks or pumpkin/melon stems.
            boolean wood = name.endsWith("_PLANKS") || switch (name) {
                case "OAK_LOG", "STRIPPED_OAK_LOG", "SPRUCE_LOG", "STRIPPED_SPRUCE_LOG",
                        "BIRCH_LOG", "STRIPPED_BIRCH_LOG", "JUNGLE_LOG", "STRIPPED_JUNGLE_LOG",
                        "ACACIA_LOG", "STRIPPED_ACACIA_LOG", "DARK_OAK_LOG", "STRIPPED_DARK_OAK_LOG",
                        "MANGROVE_LOG", "STRIPPED_MANGROVE_LOG", "CHERRY_LOG", "STRIPPED_CHERRY_LOG",
                        "PALE_OAK_LOG", "STRIPPED_PALE_OAK_LOG", "CRIMSON_STEM", "STRIPPED_CRIMSON_STEM",
                        "WARPED_STEM", "STRIPPED_WARPED_STEM" -> true;
                default -> false;
            };
            boolean stone = switch (name) {
                // Only raw Overworld stone variants are unified. Finished variants and Nether/End blocks stay untouched.
                case "STONE", "COBBLESTONE", "DEEPSLATE", "COBBLED_DEEPSLATE", "GRANITE", "DIORITE", "ANDESITE", "TUFF", "CALCITE", "DRIPSTONE_BLOCK" -> true;
                default -> false;
            };
            if ((category == Category.WOOD && wood) || (category == Category.STONE && stone)) result.add(material);
        }
        if (category == Category.WOOD) {
            result.sort(Comparator.comparing(CleanStackService::woodFamily)
                    .thenComparingInt(CleanStackService::woodVariantOrder)
                    .thenComparing(material -> material.getKey().toString()));
        } else result.sort(Comparator.comparing(material -> material.getKey().toString()));
        return List.copyOf(result);
    }
    private static String woodFamily(Material material) {
        String name = material.name().replaceFirst("^STRIPPED_", "");
        return name.replaceFirst("_(LOG|STEM|PLANKS)$", "");
    }
    private static int woodVariantOrder(Material material) {
        String name = material.name();
        if (name.endsWith("_LOG") || name.endsWith("_STEM")) return name.startsWith("STRIPPED_") ? 1 : 0;
        return 2; // planks
    }
}
