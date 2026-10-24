package fr.isaac.cutcleanreborn.fabric;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Persistent item identities, per-player conversion and equipment correction. */
public final class InventoryRules {
  public static final String ROOT = "game-rules.stuff-rules";
  private final RuleRuntime mod;
  private boolean correcting;

  InventoryRules(RuleRuntime mod) {
    this.mod = mod;
  }

  public static String id(Item item) {
    return BuiltInRegistries.ITEM.getKey(item).toString();
  }

  public static Item item(String value) {
    if (value == null || value.isBlank()) return null;
    String key = RuleConfig.itemId(value.trim());
    return BuiltInRegistries.ITEM.stream()
        .filter(item -> id(item).equals(key))
        .findFirst()
        .orElse(null);
  }

  public Registry<Enchantment> enchantments() {
    return mod.server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
  }

  public Holder.Reference<Enchantment> enchantment(String id) {
    String key = RuleConfig.itemId(id);
    return enchantments()
        .listElements()
        .filter(holder -> enchantmentId(holder).equals(key))
        .findFirst()
        .orElse(null);
  }

  public String enchantmentId(Holder<Enchantment> holder) {
    return enchantments().getKey(holder.value()).toString();
  }

  public static CompoundTag data(ItemStack stack) {
    return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
  }

  public static void data(ItemStack stack, CompoundTag tag) {
    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
  }

  public boolean cleanEnabled(ServerPlayer player) {
    return mod.config.bool("clean-stacks.players." + player.getUUID(), false);
  }

  public static String category(Item item) {
    String name = id(item).replace("minecraft:", "");
    if (name.endsWith("_planks")
        || name.endsWith("_log")
        || name.equals("crimson_stem")
        || name.equals("warped_stem")
        || name.equals("stripped_crimson_stem")
        || name.equals("stripped_warped_stem")) return "wood";
    return Set.of(
                "stone",
                "cobblestone",
                "deepslate",
                "cobbled_deepslate",
                "granite",
                "diorite",
                "andesite",
                "tuff",
                "calcite",
                "dripstone_block")
            .contains(name)
        ? "stone"
        : null;
  }

  public ItemStack clean(ItemStack stack) {
    if (stack.isEmpty() || !stack.getComponentsPatch().isEmpty()) return stack;
    Item original = stack.getItem(), current = original;
    String category = category(current);
    Set<Item> seen = new java.util.LinkedHashSet<>();
    while (category != null && seen.add(current)) {
      String fallback =
          category.equals("stone")
              ? "minecraft:stone"
              : id(current).endsWith("_planks") ? "minecraft:oak_planks" : "minecraft:oak_log";
      Item next =
          item(
              mod.config.text(
                  "clean-stacks." + category + "." + RuleConfig.material(id(current)), fallback));
      if (next == null || next == Items.AIR) next = item(fallback);
      if (next == null || next == current) break;
      if (seen.contains(next)) {
        List<Item> cycle = new ArrayList<>(seen);
        current =
            cycle.subList(cycle.indexOf(next), cycle.size()).stream()
                .min(Comparator.comparing(InventoryRules::id))
                .orElse(next);
        break;
      }
      current = next;
      category = category(current);
    }
    return current == original ? stack : new ItemStack(current, stack.getCount());
  }

  public void setCleanTarget(Item source, Item target) {
    String group = category(source);
    if (group == null) return;
    mod.config.set("clean-stacks." + group + "." + RuleConfig.material(id(source)), id(target));
    String targetGroup = category(target);
    if (targetGroup != null)
      mod.config.set(
          "clean-stacks." + targetGroup + "." + RuleConfig.material(id(target)), id(target));
  }

  public static boolean armor(Item item) {
    return id(item).matches(".*_(helmet|chestplate|leggings|boots)$");
  }

  public static boolean tool(Item item) {
    return id(item).matches(".*_(sword|pickaxe|axe|shovel|hoe)$")
        || Set.of(
                Items.MACE,
                Items.BOW,
                Items.CROSSBOW,
                Items.TRIDENT,
                Items.SHEARS,
                Items.FISHING_ROD,
                Items.BRUSH,
                Items.FLINT_AND_STEEL)
            .contains(item);
  }

  public static boolean matches(Item item, String target) {
    if (target == null) return false;
    String normal = target.trim().toUpperCase(Locale.ROOT).replace("MINECRAFT:", ""),
        key = RuleConfig.material(id(item));
    if (normal.equals("ALL")) return true;
    if (normal.equals("ARMOR")) return armor(item);
    if (normal.equals("TOOLS"))
      return key.matches(".*_(SWORD|PICKAXE|AXE|SHOVEL|HOE)$")
          || Set.of(Items.MACE, Items.BOW, Items.CROSSBOW, Items.TRIDENT).contains(item);
    if (normal.equals("SWORDS")) return key.endsWith("_SWORD");
    boolean found = false;
    for (String value : target.split(",", -1)) {
      Item exact = item(value.trim());
      if (exact == null || exact == Items.AIR) return false;
      found |= exact == item;
    }
    return found;
  }

  public static boolean matchesArmor(Item item, String target) {
    if (target == null || !armor(item)) return false;
    String normal = target.trim().toUpperCase(Locale.ROOT).replace("MINECRAFT:", "");
    if (normal.equals("ARMOR")) return true;
    if (!normal.contains(",") && RuleConfig.material(id(item)).startsWith(normal + "_"))
      return true;
    boolean found = false;
    for (String value : target.split(",", -1)) {
      Item exact = item(value.trim());
      if (exact == null || !armor(exact)) return false;
      found |= exact == item;
    }
    return found;
  }

  private static String target(Map<String, Object> rule, String fallback) {
    Object value = rule.get("target");
    return value == null || String.valueOf(value).isBlank()
        ? String.valueOf(rule.getOrDefault(fallback, ""))
        : String.valueOf(value);
  }

  public int cap(Item item, Holder<Enchantment> enchantment) {
    if (!mod.config.bool(ROOT + ".enabled", false)) return -1;
    String id = enchantmentId(enchantment);
    int max = Integer.MAX_VALUE;
    for (var rule : mod.config.entries(ROOT + ".enchantment-caps"))
      if (Boolean.TRUE.equals(rule.get("enabled"))
          && RuleMath.number(rule.get("maximum"), 1) >= 1
          && matches(item, target(rule, "item"))
          && id.equals(RuleConfig.itemId(String.valueOf(rule.get("enchantment")).trim())))
        max = Math.min(max, RuleMath.number(rule.get("maximum"), 1));
    return max == Integer.MAX_VALUE ? -1 : max;
  }

  public Map<Holder<Enchantment>, Integer> enforceCaps(ItemStack stack) {
    return enforceCaps(stack, stack.getItem());
  }

  /** The enchanting table converts BOOK after its event-equivalent input has been selected. */
  public Map<Holder<Enchantment>, Integer> enforceCaps(ItemStack stack, Item ruleTarget) {
    Map<Holder<Enchantment>, Integer> removed = new LinkedHashMap<>();
    if (stack.isEmpty()) return removed;
    ItemEnchantments before = EnchantmentHelper.getEnchantmentsForCrafting(stack);
    EnchantmentHelper.updateEnchantments(
        stack,
        builder -> {
          for (var entry : before.entrySet()) {
            int maximum = cap(ruleTarget, entry.getKey());
            if (maximum >= 0 && entry.getIntValue() > maximum) {
              removed.put(entry.getKey(), entry.getIntValue() - maximum);
              builder.set(entry.getKey(), maximum);
            }
          }
        });
    return removed;
  }

  public int refund(
      ServerPlayer player, ItemStack stack, Map<Holder<Enchantment>, Integer> removed, int paid) {
    return refund(player, stack.getItem(), removed, paid);
  }

  public int refund(
      ServerPlayer player, Item ruleTarget, Map<Holder<Enchantment>, Integer> removed, int paid) {
    if (paid <= 0 || player.isCreative()) return 0;
    long wanted = 0;
    for (var entry : removed.entrySet()) {
      int perLevel = 0, maximum = cap(ruleTarget, entry.getKey());
      String key = enchantmentId(entry.getKey());
      for (var rule : mod.config.entries(ROOT + ".enchantment-caps"))
        if (Boolean.TRUE.equals(rule.get("enabled"))
            && Boolean.TRUE.equals(rule.get("refund-xp"))
            && matches(ruleTarget, target(rule, "item"))
            && key.equals(RuleConfig.itemId(String.valueOf(rule.get("enchantment")).trim()))
            && RuleMath.number(rule.get("maximum"), 1) == maximum)
          perLevel =
              Math.max(perLevel, Math.max(0, RuleMath.number(rule.get("xp-per-removed-level"), 0)));
      wanted += (long) entry.getValue() * perLevel;
    }
    int levels = (int) Math.min(paid, Math.min(Integer.MAX_VALUE, wanted));
    if (levels > 0) {
      player.giveExperienceLevels(levels);
      player.sendSystemMessage(
          Component.literal("§6CCR §8» §aRefund: +" + levels + " XP level(s)."));
    }
    return levels;
  }

  public boolean auto(ItemStack stack) {
    if (stack.isEmpty() || !mod.config.bool("automatic-enchantments.enabled", true)) return false;
    String group = armor(stack.getItem()) ? "armor" : tool(stack.getItem()) ? "tools" : null;
    if (group == null) return false;
    Map<String, Object> configured =
        mod.config.section(
            "automatic-enchantments." + group + "." + RuleConfig.material(id(stack.getItem())));
    if (configured.isEmpty()) return false;
    Map<Holder.Reference<Enchantment>, Integer> valid = new LinkedHashMap<>();
    configured.forEach(
        (id, value) -> {
          var enchantment = enchantment(id);
          int level = value instanceof Number number ? number.intValue() : 0;
          if (enchantment != null
              && level >= 1
              && level <= 255
              && enchantment.value().canEnchant(stack)) valid.put(enchantment, level);
        });
    if (valid.isEmpty()) return false;
    CompoundTag data = data(stack);
    int version =
        mod.config.integer("automatic-enchantments.refresh-version", 0, 0, Integer.MAX_VALUE);
    String current = snapshot(stack);
    boolean previous = data.getString("ccr:auto_enchant_id").isPresent();
    if (previous
        && (data.getInt("ccr:auto_enchant_refresh_version").isEmpty()
            || data.getString("ccr:auto_enchant_state").isEmpty()
            || data.getIntOr("ccr:auto_enchant_refresh_version", 0) >= version
            || !current.equals(data.getStringOr("ccr:auto_enchant_state", "")))) return false;
    if (previous) {
      if (data.getString("ccr:auto_enchant_original").isPresent())
        restore(stack, data.getStringOr("ccr:auto_enchant_original", ""));
    } else {
      data.putString("ccr:auto_enchant_original", current);
      data.putString("ccr:auto_enchant_id", UUID.randomUUID().toString());
    }
    EnchantmentHelper.updateEnchantments(
        stack,
        builder -> {
          for (var entry : valid.entrySet()) {
            Holder.Reference<Enchantment> enchantment = entry.getKey();
            int level = entry.getValue();
            if (builder.keySet().stream()
                .anyMatch(
                    existing ->
                        !existing.equals(enchantment)
                            && !Enchantment.areCompatible(existing, enchantment))) continue;
            int max = cap(stack.getItem(), enchantment);
            level = Math.max(builder.getLevel(enchantment), level);
            if (max >= 0) level = Math.min(max, level);
            builder.set(enchantment, level);
          }
        });
    data.putString("ccr:auto_enchant_state", snapshot(stack));
    data.putInt("ccr:auto_enchant_refresh_version", version);
    data(stack, data);
    return true;
  }

  private String snapshot(ItemStack stack) {
    return stack
        .getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
        .entrySet()
        .stream()
        .sorted(Comparator.comparing(e -> enchantmentId(e.getKey())))
        .map(e -> enchantmentId(e.getKey()) + "=" + e.getIntValue())
        .collect(java.util.stream.Collectors.joining(";"));
  }

  private void restore(ItemStack stack, String snapshot) {
    stack.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
    EnchantmentHelper.updateEnchantments(
        stack,
        builder -> {
          for (String part : snapshot.split(";")) {
            String[] fields = part.split("=", 2);
            if (fields.length != 2) continue;
            Holder.Reference<Enchantment> enchantment = enchantment(fields[0]);
            Integer level = RuleMath.integer(fields[1], 1, 255);
            if (enchantment != null && level != null) builder.set(enchantment, level);
          }
        });
  }

  public void correct(ServerPlayer player) {
    if (correcting || player.isRemoved()) return;
    correcting = true;
    try {
      correctNow(player);
    } finally {
      correcting = false;
    }
  }

  public boolean correcting() {
    return correcting;
  }

  private void correctNow(ServerPlayer player) {
    for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
      ItemStack stack = player.getInventory().getItem(slot);
      if (cleanEnabled(player)) {
        ItemStack cleaned = clean(stack);
        if (cleaned != stack) {
          player.getInventory().setItem(slot, cleaned);
          stack = cleaned;
        }
      }
      auto(stack);
      Map<Holder<Enchantment>, Integer> removed = enforceCaps(stack);
      if (!removed.isEmpty())
        NativeAccess.overlay(
            player, Component.literal("§eCCR • Enchantment reduced to its limit."));
    }
    if (mod.config.bool(ROOT + ".enabled", false))
      for (var rule : mod.config.entries(ROOT + ".armor-rules")) {
        if (!Boolean.TRUE.equals(rule.get("enabled"))) continue;
        int kept = 0, maximum = Math.clamp(RuleMath.number(rule.get("maximum"), 4), 0, 4);
        String target = target(rule, "material");
        for (EquipmentSlot slot :
            List.of(
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
          ItemStack stack = player.getItemBySlot(slot);
          if (stack.isEmpty() || !matchesArmor(stack.getItem(), target) || ++kept <= maximum)
            continue;
          player.setItemSlot(slot, ItemStack.EMPTY);
          if (!player.getInventory().add(stack)) NativeAccess.drop(player, stack);
          NativeAccess.overlay(
              player, Component.literal("§eCCR • Armor " + target + ": maximum " + maximum));
        }
      }
    player.getInventory().setChanged();
  }

  public boolean maceBlocked() {
    return mod.config.bool(ROOT + ".enabled", false)
        && mod.config.bool(ROOT + ".unique-mace.enabled", false)
        && mod.config.bool(ROOT + ".unique-mace.exists", false);
  }

  public void crafted(ServerPlayer player, ItemStack stack) {
    if (stack.is(Items.MACE)
        && mod.config.bool(ROOT + ".enabled", false)
        && mod.config.bool(ROOT + ".unique-mace.enabled", false)) {
      if (maceBlocked()) {
        stack.setCount(0);
        player.sendSystemMessage(Component.literal("§cCCR • The unique mace already exists."));
        return;
      }
      String identity = UUID.randomUUID().toString();
      CompoundTag data = data(stack);
      data.putString("ccr:unique_mace_id", identity);
      data(stack, data);
      mod.config.batch(
          Map.of(ROOT + ".unique-mace.exists", true, ROOT + ".unique-mace.id", identity));
      player.sendSystemMessage(Component.literal("§6CCR • You crafted the unique mace!"));
    }
    auto(stack);
    enforceCaps(stack);
  }

  public void destroyed(ItemStack stack) {
    if (!stack.is(Items.MACE) || !mod.config.bool(ROOT + ".unique-mace.exists", false)) return;
    String identity = data(stack).getStringOr("ccr:unique_mace_id", ""),
        active = mod.config.text(ROOT + ".unique-mace.id", "");
    if (identity.isBlank() || (!active.isBlank() && !active.equals(identity))) return;
    Map<String, Object> reset = new LinkedHashMap<>();
    reset.put(ROOT + ".unique-mace.exists", false);
    reset.put(ROOT + ".unique-mace.id", null);
    mod.config.batch(reset);
    mod.server
        .getPlayerList()
        .broadcastSystemMessage(
            Component.literal(
                "§6CCR • The unique mace was destroyed. A new one can now be crafted."),
            false);
  }
}
