package fr.isaac.cutcleanreborn.paper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;

/** Reads the server's own entity loot table, without maintaining a hard-coded mob/drop list. */
final class VanillaDropCatalog {
    private static final int SAMPLES = 64;
    private static final Map<EntityType, List<CutCleanRebornPlugin.MobDrop>> CACHE = new HashMap<>();
    private VanillaDropCatalog() { }

    static List<CutCleanRebornPlugin.MobDrop> preview(EntityType mob) {
        if (CACHE.containsKey(mob)) return CACHE.get(mob);
        if (Bukkit.getWorlds().isEmpty() || !LivingEntity.class.isAssignableFrom(mob.getEntityClass())) return List.of();
        World world = Bukkit.getWorlds().getFirst();
        Location origin = world.getSpawnLocation().clone().add(0.5D, world.getMinHeight() + 1.0D, 0.5D);
        Map<Material, Sample> samples = new HashMap<>();
        LivingEntity entity = null;
        try {
            @SuppressWarnings("unchecked")
            Class<? extends LivingEntity> entityClass = (Class<? extends LivingEntity>) mob.getEntityClass();
            entity = world.spawn(origin, entityClass, SpawnReason.CUSTOM, false, spawned -> {
                spawned.setAI(false);
                spawned.setSilent(true);
            });
            if (!(entity instanceof Mob lootable) || lootable.getLootTable() == null) return List.of();
            LootTable table = lootable.getLootTable();
            for (int seed = 0; seed < SAMPLES; seed++) {
                LootContext context = new LootContext.Builder(origin).lootedEntity(entity).build();
                // Small consecutive java.util.Random seeds share their first high bits, which
                // made variable loot (e.g. sheep mutton) appear as a fixed 2-2 range.
                long mixedSeed = 0x9E3779B97F4A7C15L * (seed + 1L);
                for (ItemStack stack : table.populateLoot(new Random(mixedSeed), context)) {
                    if (stack.getType().isItem() && stack.getAmount() > 0)
                        samples.computeIfAbsent(stack.getType(), ignored -> new Sample()).observe(stack.getAmount());
                }
            }
        } catch (RuntimeException ignored) {
            // An unsupported entity simply has no discoverable base drops; custom drops still work.
        } finally {
            if (entity != null && entity.isValid()) entity.remove();
        }
        List<CutCleanRebornPlugin.MobDrop> result = new ArrayList<>();
        for (Map.Entry<Material, Sample> entry : samples.entrySet()) {
            Sample sample = entry.getValue();
            result.add(new CutCleanRebornPlugin.MobDrop(entry.getKey(), sample.minimum, sample.maximum,
                    sample.hits * 100.0D / SAMPLES));
        }
        result.sort(Comparator.comparing(drop -> drop.material().getKey().toString()));
        CACHE.put(mob, List.copyOf(result));
        return CACHE.get(mob);
    }
    /**
     * Paper's deprecated LootContext looting modifier no longer changes modern data-driven
     * loot tables. Vanilla Looting adds 0..level items to the normal quantity of affected drops.
     */
    static List<CutCleanRebornPlugin.MobDrop> previewLooting(EntityType mob, int level) {
        return preview(mob).stream().map(drop -> lootingAffects(drop.material())
                ? new CutCleanRebornPlugin.MobDrop(drop.material(), drop.minimum(), Math.min(64, drop.maximum() + level), drop.chance())
                : drop).toList();
    }
    private static boolean lootingAffects(Material material) {
        return switch (material) {
            case BEEF, CHICKEN, COD, COOKED_BEEF, COOKED_CHICKEN, COOKED_COD, COOKED_MUTTON, COOKED_PORKCHOP,
                    COOKED_RABBIT, COOKED_SALMON, LEATHER, MUTTON, PORKCHOP, RABBIT, RABBIT_HIDE, SALMON,
                    ROTTEN_FLESH, BONE, ARROW, STRING, SPIDER_EYE, GUNPOWDER, ENDER_PEARL, BLAZE_ROD,
                    MAGMA_CREAM, SLIME_BALL, GHAST_TEAR, PRISMARINE_SHARD, PRISMARINE_CRYSTALS, INK_SAC,
                    GLOW_INK_SAC, PHANTOM_MEMBRANE, SHULKER_SHELL, NAUTILUS_SHELL -> true;
            default -> false;
        };
    }

    private static final class Sample {
        private int minimum = Integer.MAX_VALUE;
        private int maximum;
        private int hits;
        void observe(int amount) { minimum = Math.min(minimum, amount); maximum = Math.max(maximum, amount); hits++; }
    }
}
