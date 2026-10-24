package fr.isaac.cutcleanreborn.bukkit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;

/** Works on successful block drops so cancelled/protected breaks never create items or XP. */
final class OreDropService implements Listener {
    private final CutCleanRebornPlugin plugin;
    private final Map<Block, MiningContext> pending = new HashMap<>();

    OreDropService(CutCleanRebornPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        OreDefinition ore = OreCatalog.find(block.getType());
        if (ore == null || !event.isDropItems() || event.getPlayer().getGameMode() != GameMode.SURVIVAL) return;
        if (!plugin.hasOreCustomisation(block.getType()) && !plugin.oreAutoSmeltEnabled() && plugin.oreFortuneVanillaEnabled()) return;
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand().clone();
        if (tool.containsEnchantment(Enchantment.SILK_TOUCH)) return;
        int fortune = tool.getEnchantmentLevel(Enchantment.FORTUNE);
        tool.removeEnchantment(Enchantment.FORTUNE);
        List<ItemStack> withoutFortune = new ArrayList<>(block.getDrops(tool, event.getPlayer()));
        if (withoutFortune.stream().noneMatch(item -> item.getType() == ore.normalDrop())) return;
        MiningContext context = new MiningContext(block.getType(), fortune, withoutFortune, event);
        pending.put(block, context);
        plugin.getServer().getScheduler().runTask(plugin, () -> pending.remove(block, context));
        event.setExpToDrop(GameplayMath.addXp(event.getExpToDrop(), plugin.getOreXpBonus(block.getType())));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent event) {
        MiningContext context = pending.remove(event.getBlock());
        if (context == null || context.event().isCancelled()) return;
        Material normal = OreCatalog.find(context.ore()).normalDrop();
        List<ItemStack> vanilla = new ArrayList<>();
        for (var entity : event.getItems()) {
            ItemStack item = entity.getItemStack().clone();
            if (plugin.oreFortuneVanillaEnabled() || item.getType() != normal) vanilla.add(item);
        }
        if (!plugin.oreFortuneVanillaEnabled()) vanilla.addAll(context.withoutFortune());
        List<ItemStack> output = new ArrayList<>();
        var override = plugin.getOreVanillaOverride(context.ore());
        for (ItemStack item : vanilla) {
            if (override == null || item.getType() != normal) output.add(item);
        }
        if (override != null) addRule(output, override, plugin.oreFortuneVanillaEnabled() ? context.fortune() : 0);
        for (var rule : plugin.getOreDrops(context.ore())) addRule(output, rule, plugin.oreFortuneCustomEnabled() ? context.fortune() : 0);
        int xp = 0;
        if (plugin.oreAutoSmeltEnabled()) {
            for (ItemStack item : output) {
                Material smelted = smelted(item.getType());
                if (smelted == null) continue;
                double experience = furnaceXp(item.getType()) * item.getAmount();
                int whole = (int) experience;
                xp = GameplayMath.addXp(xp, whole + (ThreadLocalRandom.current().nextDouble() < experience - whole ? 1 : 0));
                item.setType(smelted);
            }
        }
        // Emit the replacement stacks only after every listener has had a chance to cancel the event.
        event.getItems().clear();
        int furnaceExperience = xp;
        Location location = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (event.isCancelled() || context.event().isCancelled() || !context.event().isDropItems()) return;
            for (ItemStack item : output) {
                ItemStack converted = plugin.cleanStacks().enabled(event.getPlayer()) ? plugin.cleanStacks().normalise(item) : item;
                List<ItemStack> stacks = new ArrayList<>();
                LootSupport.addStacks(stacks, converted, converted.getAmount());
                stacks.forEach(stack -> location.getWorld().dropItemNaturally(location, stack));
            }
            if (furnaceExperience > 0) location.getWorld().spawn(location, ExperienceOrb.class, orb -> orb.setExperience(furnaceExperience));
        });
    }

    private static void addRule(List<ItemStack> output, CutCleanRebornPlugin.MobDrop rule, int fortune) {
        int amount = LootSupport.roll(rule);
        if (amount > 0) LootSupport.addStacks(output, new ItemStack(rule.material()), amount * GameplayMath.fortuneMultiplier(fortune, ThreadLocalRandom.current()));
    }

    static Material smelted(Material material) {
        return switch (material) {
            case RAW_IRON -> Material.IRON_INGOT;
            case RAW_GOLD -> Material.GOLD_INGOT;
            case RAW_COPPER -> Material.COPPER_INGOT;
            case ANCIENT_DEBRIS -> Material.NETHERITE_SCRAP;
            default -> null;
        };
    }

    private static double furnaceXp(Material material) {
        return switch (material) { case RAW_IRON, RAW_COPPER -> 0.7; case RAW_GOLD -> 1.0; case ANCIENT_DEBRIS -> 2.0; default -> 0; };
    }

    private record MiningContext(Material ore, int fortune, List<ItemStack> withoutFortune, BlockBreakEvent event) { }
}

