package fr.isaac.cutcleanreborn.fabric;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Native loot-table samples are descriptions only: no entity is spawned and no CCR drop hook is
 * invoked.
 */
public final class VanillaCatalog {
  private static final int SAMPLES = 256;
  private final RuleRuntime mod;
  private final Map<String, List<Map<String, Object>>> cache = new HashMap<>();

  VanillaCatalog(RuleRuntime mod) {
    this.mod = mod;
  }

  public void clear() {
    cache.clear();
  }

  public List<Map<String, Object>> mob(String id, boolean looting) {
    String key = id + "/" + looting;
    if (cache.containsKey(key)) return cache.get(key);
    Map<Item, Sample> found = new HashMap<>();
    var level = mod.server.overworld();
    try {
      var type =
          BuiltInRegistries.ENTITY_TYPE.stream()
              .filter(
                  value ->
                      BuiltInRegistries.ENTITY_TYPE
                          .getKey(value)
                          .toString()
                          .equals(RuleConfig.itemId(id)))
              .findFirst()
              .orElse(null);
      if (type == null) return List.of();
      if (!(type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND)
          instanceof LivingEntity entity)) return List.of();
      entity.setPos(0.5, 100, 0.5);
      var killer =
          new ServerPlayer(
              mod.server,
              level,
              new GameProfile(
                  UUID.fromString("b99cac65-b001-45a5-aa5f-000000000001"), "CCRPreview"),
              ClientInformation.createDefault());
      ItemStack tool = new ItemStack(Items.DIAMOND_SWORD);
      if (looting) tool.enchant(mod.inventory.enchantments().getOrThrow(Enchantments.LOOTING), 3);
      killer.getInventory().setItem(0, tool);
      var source = level.damageSources().playerAttack(killer);
      LootParams params =
          new LootParams.Builder(level)
              .withParameter(LootContextParams.THIS_ENTITY, entity)
              .withParameter(LootContextParams.ORIGIN, entity.position())
              .withParameter(LootContextParams.DAMAGE_SOURCE, source)
              .withParameter(LootContextParams.ATTACKING_ENTITY, killer)
              .withParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, killer)
              .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
              .create(LootContextParamSets.ENTITY);
      var keyTable = entity.getLootTable();
      if (keyTable.isEmpty()) return List.of();
      var table = mod.server.reloadableRegistries().getLootTable(keyTable.get());
      for (int seed = 0; seed < SAMPLES; seed++) {
        Map<Item, Integer> amounts = new HashMap<>();
        for (ItemStack item : table.getRandomItems(params, 0x9E3779B97F4A7C15L * (seed + 1L)))
          if (!item.isEmpty()) amounts.merge(item.getItem(), item.getCount(), Integer::sum);
        amounts.forEach(
            (item, amount) -> found.computeIfAbsent(item, ignored -> new Sample()).observe(amount));
      }
    } catch (RuntimeException exception) {
      CutCleanReborn.LOG.debug("CCR unavailable vanilla preview for {}", id, exception);
    }
    List<Map<String, Object>> result = new ArrayList<>();
    found.entrySet().stream()
        .sorted(Comparator.comparing(entry -> InventoryRules.id(entry.getKey())))
        .forEach(
            entry -> {
              Sample sample = entry.getValue();
              Map<String, Object> value = new LinkedHashMap<>();
              value.put("item", InventoryRules.id(entry.getKey()));
              value.put("minimum", sample.min);
              value.put("maximum", sample.max);
              value.put("chance", sample.hits * 100.0 / SAMPLES);
              result.add(value);
            });
    cache.put(key, List.copyOf(result));
    return cache.get(key);
  }

  public static Map<String, Object> ore(String id, boolean fortune) {
    Item item = LootRules.ORES.get(id);
    if (item == null) return Map.of();
    int min =
        item == Items.RAW_COPPER || item == Items.GOLD_NUGGET
            ? 2
            : item == Items.REDSTONE || item == Items.LAPIS_LAZULI ? 4 : 1;
    int max =
        item == Items.RAW_COPPER || item == Items.REDSTONE
            ? 5
            : item == Items.LAPIS_LAZULI ? 9 : item == Items.GOLD_NUGGET ? 6 : 1;
    if (fortune && item != Items.ANCIENT_DEBRIS) max = item == Items.REDSTONE ? 8 : max * 4;
    return Map.of("item", InventoryRules.id(item), "minimum", min, "maximum", max, "chance", 100.0);
  }

  private static final class Sample {
    int min = Integer.MAX_VALUE, max, hits;

    void observe(int amount) {
      min = Math.min(min, amount);
      max = Math.max(max, amount);
      hits++;
    }
  }
}
