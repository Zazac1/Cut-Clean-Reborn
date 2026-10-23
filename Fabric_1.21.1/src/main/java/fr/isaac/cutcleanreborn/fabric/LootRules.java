package fr.isaac.cutcleanreborn.fabric;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class LootRules {
  private static final Map<Item, Item> COOKED =
      Map.of(
          Items.BEEF,
          Items.COOKED_BEEF,
          Items.CHICKEN,
          Items.COOKED_CHICKEN,
          Items.MUTTON,
          Items.COOKED_MUTTON,
          Items.PORKCHOP,
          Items.COOKED_PORKCHOP,
          Items.RABBIT,
          Items.COOKED_RABBIT,
          Items.COD,
          Items.COOKED_COD,
          Items.SALMON,
          Items.COOKED_SALMON);
  private static final Map<Item, Item> SMELTED =
      Map.of(
          Items.RAW_IRON,
          Items.IRON_INGOT,
          Items.RAW_GOLD,
          Items.GOLD_INGOT,
          Items.RAW_COPPER,
          Items.COPPER_INGOT,
          Items.ANCIENT_DEBRIS,
          Items.NETHERITE_SCRAP);
  private static final Set<String> ANIMALS =
      Set.of("COW", "MOOSHROOM", "CHICKEN", "PIG", "SHEEP", "RABBIT", "COD", "SALMON");
  public static final Map<String, Item> ORES =
      Map.ofEntries(
          Map.entry("coal_ore", Items.COAL),
          Map.entry("deepslate_coal_ore", Items.COAL),
          Map.entry("iron_ore", Items.RAW_IRON),
          Map.entry("deepslate_iron_ore", Items.RAW_IRON),
          Map.entry("copper_ore", Items.RAW_COPPER),
          Map.entry("deepslate_copper_ore", Items.RAW_COPPER),
          Map.entry("gold_ore", Items.RAW_GOLD),
          Map.entry("deepslate_gold_ore", Items.RAW_GOLD),
          Map.entry("redstone_ore", Items.REDSTONE),
          Map.entry("deepslate_redstone_ore", Items.REDSTONE),
          Map.entry("lapis_ore", Items.LAPIS_LAZULI),
          Map.entry("deepslate_lapis_ore", Items.LAPIS_LAZULI),
          Map.entry("diamond_ore", Items.DIAMOND),
          Map.entry("deepslate_diamond_ore", Items.DIAMOND),
          Map.entry("emerald_ore", Items.EMERALD),
          Map.entry("deepslate_emerald_ore", Items.EMERALD),
          Map.entry("nether_quartz_ore", Items.QUARTZ),
          Map.entry("nether_gold_ore", Items.GOLD_NUGGET),
          Map.entry("ancient_debris", Items.ANCIENT_DEBRIS));
  private final CutCleanReborn mod;
  private final ThreadLocal<ArrayDeque<Death>> deaths = ThreadLocal.withInitial(ArrayDeque::new);
  private final ThreadLocal<Boolean> probe = ThreadLocal.withInitial(() -> false);
  private final ThreadLocal<Integer> dropping = ThreadLocal.withInitial(() -> 0);

  LootRules(CutCleanReborn mod) {
    this.mod = mod;
  }

  void clearTransientState() {
    deaths.remove();
    probe.remove();
    dropping.remove();
  }

  public int enchantmentLevel(ItemStack stack, ResourceKey<Enchantment> key) {
    Holder.Reference<Enchantment> holder = mod.inventory().enchantments().getHolderOrThrow(key);
    return EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
  }

  public void beginDeath(LivingEntity entity, DamageSource source) {
    if (!(entity instanceof ServerPlayer))
      deaths.get().push(new Death(entity, source, new ArrayList<>()));
  }

  public void beginBlockDrops() {
    dropping.set(dropping.get() + 1);
  }

  public void endBlockDrops() {
    dropping.set(Math.max(0, dropping.get() - 1));
  }

  public boolean capture(Entity entity, ItemStack stack) {
    Death death = deaths.get().peek();
    if (death == null || death.entity() != entity) return false;
    death.drops().add(stack.copy());
    return true;
  }

  public void finishDeath(LivingEntity entity) {
    if (entity instanceof ServerPlayer) return;
    Death death = deaths.get().poll();
    if (death == null
        || death.entity() != entity
        || entity instanceof ServerPlayer
        || !(entity.level() instanceof ServerLevel level)) return;
    String mob =
        Settings.materialKey(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
    String root = "mobs." + mob;
    boolean animal = ANIMALS.contains(mob),
        cook =
            mod.settings()
                .bool(
                    root + ".cooked-food",
                    animal && mod.settings().bool("animal-drops.cook", true));
    int multiplier = animal ? mod.settings().integer("animal-drops.multiplier", 1, 1, 5) : 1;
    ServerPlayer killer =
        death.source().getEntity() instanceof ServerPlayer p
            ? p
            : entity.getKillCredit() instanceof ServerPlayer p ? p : null;
    int looting =
        killer == null ? 0 : enchantmentLevel(killer.getMainHandItem(), Enchantments.LOOTING);
    List<Map<String, Object>> overrides = rules(root + ".vanilla-overrides");
    List<ItemStack> output = new ArrayList<>();
    for (ItemStack original : death.drops()) {
      if (overrides.stream()
          .anyMatch(
              rule ->
                  sameFood(
                      InventoryRules.item(
                          String.valueOf(rule.getOrDefault("source", rule.get("item")))),
                      original.getItem()))) continue;
      ItemStack adjusted = cook(original, cook);
      Item legacy =
          InventoryRules.item(mod.settings().text("animal-drops.item-overrides." + mob, ""));
      if (legacy != null && primaryFood(mob, adjusted.getItem()))
        adjusted = adjusted.transmuteCopy(legacy);
      stacks(output, adjusted, adjusted.getCount() * multiplier);
    }
    for (var rule : overrides)
      addRule(
          output,
          rule,
          cook,
          multiplier,
          mod.settings().bool("mob-drops.looting-vanilla", true) ? looting : 0,
          false);
    for (var rule : rules(root + ".drops"))
      addRule(
          output,
          rule,
          cook,
          1,
          mod.settings().bool("mob-drops.looting-custom", false) ? looting : 0,
          false);
    int range = mod.settings().integer("animal-drops.collection-range", 0, 0, 32);
    for (ItemStack stack : output) {
      if (killer != null
          && range > 0
          && killer.serverLevel() == level
          && killer.distanceToSqr(entity) <= (double) range * range) {
        mod.inventory().auto(stack);
        if (mod.inventory().cleanEnabled(killer)) stack = mod.inventory().clean(stack);
        if (killer.getInventory().add(stack)) continue;
      }
      if (!stack.isEmpty()) {
        ItemEntity drop = new ItemEntity(level, entity.getX(), entity.getY(), entity.getZ(), stack);
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);
      }
    }
    int xp =
        mod.settings()
            .integer(
                root + ".xp-bonus",
                animal ? mod.settings().integer("animal-drops.xp-bonus", 0, 0, 50) : 0,
                0,
                1_000_000);
    if (xp > 0) ExperienceOrb.award(level, entity.position(), xp);
  }

  public List<ItemStack> block(
      BlockState state,
      ServerLevel level,
      BlockPos pos,
      BlockEntity blockEntity,
      Entity entity,
      ItemStack tool,
      List<ItemStack> original) {
    if (probe.get() || dropping.get() == 0) return original;
    String block = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
    List<ItemStack> output = new ArrayList<>(original);
    ServerPlayer player = entity instanceof ServerPlayer p ? p : null;
    if (state.is(Blocks.SUGAR_CANE)) {
      String cause = player == null ? "support" : "player";
      boolean natural = !mod.origins().contains(level, pos, "cane");
      if ((player == null || player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL)
          && (player != null || !state.canSurvive(level, pos))
          && mod.settings().bool("better-sugar-cane.enabled", false)
          && mod.settings().bool("better-sugar-cane." + cause + ".enabled", true)
          && natural) {
        int amount = caneQuantity(cause);
        output.removeIf(stack -> stack.is(Items.SUGAR_CANE));
        stacks(output, new ItemStack(Items.SUGAR_CANE), amount);
      }
      mod.origins().remove(level, pos, "cane");
    }
    if (player != null && player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL) {
      int silk = enchantmentLevel(tool, Enchantments.SILK_TOUCH),
          fortune = enchantmentLevel(tool, Enchantments.FORTUNE);
      if (ORES.containsKey(block) && silk == 0) {
        String root = "ore-drops." + block;
        Item normal = ORES.get(block);
        if (!mod.settings().bool("ore-drops.fortune-vanilla", true) && fortune > 0) {
          ItemStack withoutFortune = tool.copy();
          Holder.Reference<Enchantment> enchantment =
              mod.inventory().enchantments().getHolderOrThrow(Enchantments.FORTUNE);
          EnchantmentHelper.updateEnchantments(
              withoutFortune, builder -> builder.set(enchantment, 0));
          probe.set(true);
          try {
            output =
                new ArrayList<>(
                    Block.getDrops(state, level, pos, blockEntity, entity, withoutFortune));
          } finally {
            probe.set(false);
          }
        }
        List<Map<String, Object>> overrides = rules(root + ".vanilla-overrides");
        boolean suitable = output.stream().anyMatch(stack -> stack.is(normal));
        if (suitable) {
          if (!overrides.isEmpty()) {
            output.removeIf(stack -> stack.is(normal));
            addRule(
                output,
                overrides.getFirst(),
                false,
                1,
                mod.settings().bool("ore-drops.fortune-vanilla", true) ? fortune : 0,
                true);
          }
          for (var rule : rules(root + ".drops"))
            addRule(
                output,
                rule,
                false,
                1,
                mod.settings().bool("ore-drops.fortune-custom", false) ? fortune : 0,
                true);
          int xp = mod.settings().integer(root + ".xp-bonus", 0, 0, 1_000_000);
          if (mod.settings().bool("ore-drops.auto-smelt", false))
            for (int index = 0; index < output.size(); index++) {
              ItemStack stack = output.get(index);
              Item converted = SMELTED.get(stack.getItem());
              if (converted == null) continue;
              double experience =
                  stack.getCount()
                      * (stack.is(Items.RAW_GOLD) ? 1 : stack.is(Items.ANCIENT_DEBRIS) ? 2 : 0.7);
              int whole = (int) experience;
              xp =
                  GameplayMath.addXp(
                      xp,
                      whole
                          + (ThreadLocalRandom.current().nextDouble() < experience - whole
                              ? 1
                              : 0));
              output.set(index, stack.transmuteCopy(converted));
            }
          if (xp > 0 && level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS))
            ExperienceOrb.award(level, net.minecraft.world.phys.Vec3.atCenterOf(pos), xp);
        }
      }
      if ((state.is(Blocks.OAK_LEAVES) || state.is(Blocks.DARK_OAK_LEAVES))
          && mod.settings().contains("tree-cutter.apple-chance")
          && !tool.is(Items.SHEARS)
          && silk == 0) {
        output.removeIf(stack -> stack.is(Items.APPLE));
        if (ThreadLocalRandom.current().nextDouble(100)
            < mod.settings().decimal("tree-cutter.apple-chance", 0.5, 0, 100))
          output.add(new ItemStack(Items.APPLE));
      }
    }
    if (player != null && mod.inventory().cleanEnabled(player))
      output.replaceAll(mod.inventory()::clean);
    return output;
  }

  private int caneQuantity(String cause) {
    String root = "better-sugar-cane." + cause;
    if (ThreadLocalRandom.current().nextDouble(100)
        >= mod.settings().decimal(root + ".chance", 100, 0, 100)) return 1;
    int min = mod.settings().integer(root + ".minimum", 1, 1, 64),
        max = mod.settings().integer(root + ".maximum", 3, min, 64);
    return ThreadLocalRandom.current().nextInt(min, max + 1);
  }

  private static boolean sameFood(Item a, Item b) {
    return a != null && b != null && (a == b || COOKED.get(a) == b || COOKED.get(b) == a);
  }

  private static ItemStack cook(ItemStack stack, boolean enabled) {
    Item target = enabled ? COOKED.get(stack.getItem()) : null;
    return target == null ? stack.copy() : stack.transmuteCopy(target);
  }

  private static boolean primaryFood(String mob, Item item) {
    Item primary =
        switch (mob) {
          case "COW", "MOOSHROOM" -> Items.BEEF;
          case "CHICKEN" -> Items.CHICKEN;
          case "PIG" -> Items.PORKCHOP;
          case "SHEEP" -> Items.MUTTON;
          case "RABBIT" -> Items.RABBIT;
          case "COD" -> Items.COD;
          case "SALMON" -> Items.SALMON;
          default -> null;
        };
    return primary != null && sameFood(primary, item);
  }

  private List<Map<String, Object>> rules(String path) {
    return mod.settings().entries(path).stream().filter(LootRules::validRule).toList();
  }

  static boolean validRule(Map<String, Object> rule) {
    Item item = InventoryRules.item(String.valueOf(rule.get("item")));
    int minimum = Settings.number(rule, "minimum", 1),
        maximum = Settings.number(rule, "maximum", minimum);
    double chance = Settings.number(rule, "chance", 100.0);
    return item != null
        && item != Items.AIR
        && minimum >= 0
        && maximum >= minimum
        && maximum <= 64
        && Double.isFinite(chance)
        && chance >= 0
        && chance <= 100
        && (!rule.containsKey("source")
            || InventoryRules.item(String.valueOf(rule.get("source"))) != null);
  }

  private static void addRule(
      List<ItemStack> output,
      Map<String, Object> rule,
      boolean cooked,
      int multiplier,
      int bonus,
      boolean fortune) {
    Item item = InventoryRules.item(String.valueOf(rule.get("item")));
    int min = Settings.number(rule, "minimum", 1), max = Settings.number(rule, "maximum", min);
    double chance = Settings.number(rule, "chance", 100.0);
    if (item == null
        || min < 0
        || max < min
        || max > 64
        || !Double.isFinite(chance)
        || chance < 0
        || chance > 100
        || ThreadLocalRandom.current().nextDouble(100) >= chance) return;
    int amount = ThreadLocalRandom.current().nextInt(min, max + 1);
    if (amount == 0) return;
    amount =
        fortune
            ? amount * GameplayMath.fortuneMultiplier(bonus, ThreadLocalRandom.current())
            : amount + GameplayMath.lootingBonus(bonus, ThreadLocalRandom.current());
    ItemStack result = cook(new ItemStack(item), cooked);
    stacks(output, result, amount * multiplier);
  }

  public static void stacks(List<ItemStack> output, ItemStack template, int amount) {
    for (int remaining = amount; remaining > 0; ) {
      int count = Math.min(remaining, template.getMaxStackSize());
      output.add(template.copyWithCount(count));
      remaining -= count;
    }
  }

  private record Death(LivingEntity entity, DamageSource source, List<ItemStack> drops) {}
}
