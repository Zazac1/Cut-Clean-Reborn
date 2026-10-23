package fr.isaac.cutcleanreborn.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

/** Converts real drops and evaluates each configured override once, independently of vanilla RNG. */
final class MobDropService implements Listener {
    private static final Map<Material, Material> COOKED = Map.of(
            Material.BEEF, Material.COOKED_BEEF, Material.CHICKEN, Material.COOKED_CHICKEN,
            Material.MUTTON, Material.COOKED_MUTTON, Material.PORKCHOP, Material.COOKED_PORKCHOP,
            Material.RABBIT, Material.COOKED_RABBIT, Material.COD, Material.COOKED_COD,
            Material.SALMON, Material.COOKED_SALMON);
    private final CutCleanRebornPlugin plugin;

    MobDropService(CutCleanRebornPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntityType() == EntityType.PLAYER) return;
        EntityType mob = event.getEntityType();
        Player killer = event.getEntity().getKiller();
        int looting = killer == null ? 0 : killer.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.LOOTING);
        int multiplier = isAnimal(mob) ? plugin.getBoundedInt("animal-drops.multiplier", 1, 1, 5) : 1;
        boolean cook = plugin.getMobCookedFood(mob);
        List<CutCleanRebornPlugin.MobDrop> overrides = plugin.getVanillaDropOverrides(mob);
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack original : event.getDrops()) {
            // A burning mob may already have cooked food in its vanilla drops.
            if (overrides.stream().anyMatch(rule -> sameFood(rule.material(), original.getType()))) continue;
            ItemStack adjusted = cook(original, cook);
            if (isPrimaryFood(mob, adjusted.getType())) {
                Material legacy = MenuItems.findItem(plugin.getConfig().getString("animal-drops.item-overrides." + mob.name()));
                if (legacy != null) adjusted.setType(legacy);
            }
            LootSupport.addStacks(result, adjusted, adjusted.getAmount() * multiplier);
        }
        for (var rule : overrides) addRule(result, rule, cook, multiplier, plugin.mobLootingVanillaEnabled() ? looting : 0);
        for (var rule : plugin.getMobDrops(mob)) addRule(result, rule, cook, 1, plugin.mobLootingCustomEnabled() ? looting : 0);
        event.getDrops().clear();
        event.getDrops().addAll(result);
        event.setDroppedExp(GameplayMath.addXp(event.getDroppedExp(), plugin.getMobXpBonus(mob)));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void collect(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        int range = plugin.getBoundedInt("animal-drops.collection-range", 0, 0, 32);
        if (event.getEntityType() == EntityType.PLAYER || killer == null || range == 0
                || !killer.getWorld().equals(event.getEntity().getWorld())
                || killer.getLocation().distanceSquared(event.getEntity().getLocation()) > (double) range * range) return;
        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack drop : event.getDrops()) {
            plugin.autoEnchantments().apply(drop);
            ItemStack converted = plugin.cleanStacks().enabled(killer) ? plugin.cleanStacks().normalise(drop) : drop;
            remaining.addAll(killer.getInventory().addItem(converted).values());
        }
        event.getDrops().clear();
        event.getDrops().addAll(remaining);
        plugin.stuffRules().check(killer);
    }

    private static void addRule(List<ItemStack> output, CutCleanRebornPlugin.MobDrop rule, boolean cook, int multiplier, int looting) {
        int amount = LootSupport.roll(rule);
        if (amount <= 0) return;
        amount += GameplayMath.lootingBonus(looting, ThreadLocalRandom.current());
        ItemStack item = cook(new ItemStack(rule.material()), cook);
        LootSupport.addStacks(output, item, amount * multiplier);
    }

    private static ItemStack cook(ItemStack original, boolean enabled) {
        ItemStack item = original.clone();
        if (enabled && COOKED.containsKey(item.getType())) item.setType(COOKED.get(item.getType()));
        return item;
    }

    private static boolean sameFood(Material a, Material b) {
        return a == b || COOKED.get(a) == b || COOKED.get(b) == a;
    }

    private static boolean isAnimal(EntityType type) {
        return switch (type) { case COW, MOOSHROOM, CHICKEN, PIG, SHEEP, RABBIT, COD, SALMON -> true; default -> false; };
    }

    private static boolean isPrimaryFood(EntityType mob, Material material) {
        return switch (mob) {
            case COW, MOOSHROOM -> sameFood(Material.BEEF, material);
            case CHICKEN -> sameFood(Material.CHICKEN, material);
            case PIG -> sameFood(Material.PORKCHOP, material);
            case SHEEP -> sameFood(Material.MUTTON, material);
            case RABBIT -> sameFood(Material.RABBIT, material);
            case COD -> sameFood(Material.COD, material);
            case SALMON -> sameFood(Material.SALMON, material);
            default -> false;
        };
    }
}
