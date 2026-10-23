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
import net.minecraft.resources.ResourceLocation;
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
  private final CutCleanReborn mod;
  private final Set<UUID> scheduled = new java.util.HashSet<>();
  private final Set<UUID> correcting = new java.util.HashSet<>();

  InventoryRules(CutCleanReborn mod) {
    this.mod = mod;
  }

  public static String id(Item item) {
    return BuiltInRegistries.ITEM.getKey(item).toString();
  }

  public static Item item(String value) {
    if (value == null || value.isBlank()) return null;
    ResourceLocation key = ResourceLocation.tryParse(value.trim().toLowerCase(Locale.ROOT));
    return key != null && BuiltInRegistries.ITEM.containsKey(key)
        ? BuiltInRegistries.ITEM.get(key)
        : null;
  }

  public Registry<Enchantment> enchantments() {
    return mod.server().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
  }

  public Holder.Reference<Enchantment> enchantment(String id) {
    if (id == null || id.isBlank()) return null;
    ResourceLocation key = ResourceLocation.tryParse(id.trim().toLowerCase(Locale.ROOT));
    return key == null ? null : enchantments().getHolder(key).orElse(null);
  }

  public static CompoundTag data(ItemStack stack) {
    return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
  }

  public static void data(ItemStack stack, CompoundTag tag) {
    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
  }

  public boolean cleanEnabled(ServerPlayer player) {
    return mod.settings().bool("clean-stacks.players." + player.getUUID(), false);
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
              mod.settings()
                  .text(
                      "clean-stacks." + category + "." + Settings.materialKey(id(current)),
                      fallback));
      if (next == null) next = item(fallback);
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
    mod.settings()
        .set("clean-stacks." + group + "." + Settings.materialKey(id(source)), id(target));
    String targetGroup = category(target);
    if (targetGroup != null)
      mod.settings()
          .set("clean-stacks." + targetGroup + "." + Settings.materialKey(id(target)), id(target));
    mod.save();
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
        key = Settings.materialKey(id(item));
    if (normal.equals("ALL")) return true;
    if (normal.equals("ARMOR")) return armor(item);
    if (normal.equals("TOOLS"))
      return key.matches(".*_(SWORD|PICKAXE|AXE|SHOVEL|HOE)$")
          || Set.of(Items.MACE, Items.BOW, Items.CROSSBOW, Items.TRIDENT).contains(item);
    if (normal.equals("SWORDS")) return key.endsWith("_SWORD");
    boolean found = false;
    for (String value : normal.split(",", -1)) {
      Item parsed = item(value);
      if (parsed == null || parsed == Items.AIR) return false;
      found |= parsed == item;
    }
    return found;
  }

  public static boolean matchesArmor(Item item, String target) {
    if (!armor(item) || target == null) return false;
    String normal = target.trim().toUpperCase(Locale.ROOT).replace("MINECRAFT:", "");
    if (normal.equals("ARMOR")) return true;
    if (!normal.contains(",") && Settings.materialKey(id(item)).startsWith(normal + "_"))
      return true;
    for (String value : normal.split(",", -1)) if (!armorOrFalse(item(value))) return false;
    return matches(item, normal);
  }

  private static boolean armorOrFalse(Item item) {
    return item != null && armor(item);
  }

  public static String ruleTarget(Map<String, Object> rule, String legacy) {
    Object target = rule.get("target");
    return target instanceof String value && !value.isBlank()
        ? value
        : String.valueOf(rule.getOrDefault(legacy, ""));
  }

  public int cap(Item item, Holder<Enchantment> enchantment) {
    if (!mod.settings().bool(ROOT + ".enabled", false)) return -1;
    int max = Integer.MAX_VALUE;
    for (var rule : mod.settings().entries(ROOT + ".enchantment-caps"))
      if (Settings.enabled(rule)
          && Settings.number(rule, "maximum", 1) >= 1
          && matches(item, ruleTarget(rule, "item"))
          && enchantment.equals(enchantment(String.valueOf(rule.get("enchantment")))))
        max = Math.min(max, Settings.number(rule, "maximum", 1));
    return max == Integer.MAX_VALUE ? -1 : max;
  }

  public Map<Holder<Enchantment>, Integer> enforceCaps(ItemStack stack) {
    return enforceCaps(stack, stack.getItem());
  }

  /**
   * Enchanting a BOOK changes its output type; rules still target the item the player paid to
   * enchant.
   */
  public Map<Holder<Enchantment>, Integer> enforceCaps(ItemStack stack, Item ruleItem) {
    Map<Holder<Enchantment>, Integer> removed = new LinkedHashMap<>();
    if (stack.isEmpty()) return removed;
    ItemEnchantments before = EnchantmentHelper.getEnchantmentsForCrafting(stack);
    EnchantmentHelper.updateEnchantments(
        stack,
        builder -> {
          for (var entry : before.entrySet()) {
            int maximum = cap(ruleItem, entry.getKey());
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
      ServerPlayer player, Item ruleItem, Map<Holder<Enchantment>, Integer> removed, int paid) {
    if (paid <= 0 || player.isCreative()) return 0;
    long wanted = 0;
    for (var entry : removed.entrySet()) {
      int perLevel = 0, maximum = cap(ruleItem, entry.getKey());
      for (var rule : mod.settings().entries(ROOT + ".enchantment-caps"))
        if (Settings.enabled(rule)
            && Boolean.TRUE.equals(rule.get("refund-xp"))
            && matches(ruleItem, ruleTarget(rule, "item"))
            && entry.getKey().equals(enchantment(String.valueOf(rule.get("enchantment"))))
            && Settings.number(rule, "maximum", 1) == maximum)
          perLevel =
              Math.max(perLevel, Math.max(0, Settings.number(rule, "xp-per-removed-level", 0)));
      wanted += (long) entry.getValue() * perLevel;
    }
    int levels = (int) Math.min(paid, Math.min(Integer.MAX_VALUE, wanted));
    if (levels > 0) {
      player.giveExperienceLevels(levels);
      player.sendSystemMessage(
          Component.literal("§6CCR §8» §aRemboursement : +" + levels + " niveau(x)."));
    }
    return levels;
  }

  public boolean auto(ItemStack stack) {
    if (stack.isEmpty() || !mod.settings().bool("automatic-enchantments.enabled", true))
      return false;
    String group = armor(stack.getItem()) ? "armor" : tool(stack.getItem()) ? "tools" : null;
    if (group == null) return false;
    Map<String, Object> raw =
        mod.settings()
            .section(
                "automatic-enchantments."
                    + group
                    + "."
                    + Settings.materialKey(id(stack.getItem())));
    Map<Holder.Reference<Enchantment>, Integer> configured = new LinkedHashMap<>();
    raw.forEach(
        (key, value) -> {
          Holder.Reference<Enchantment> enchantment = enchantment(key);
          int level = value instanceof Number n ? n.intValue() : 0;
          if (enchantment != null
              && level > 0
              && level <= 255
              && enchantment.value().canEnchant(stack)) configured.put(enchantment, level);
        });
    if (configured.isEmpty()) return false;
    CompoundTag data = data(stack);
    int version =
        mod.settings().integer("automatic-enchantments.refresh-version", 0, 0, Integer.MAX_VALUE);
    String current = snapshot(stack);
    boolean previous = data.contains("ccr:auto_enchant_id", net.minecraft.nbt.Tag.TAG_STRING);
    if (previous
        && (!data.contains("ccr:auto_enchant_refresh_version", net.minecraft.nbt.Tag.TAG_INT)
            || !data.contains("ccr:auto_enchant_state", net.minecraft.nbt.Tag.TAG_STRING)
            || data.getInt("ccr:auto_enchant_refresh_version") >= version
            || !current.equals(data.getString("ccr:auto_enchant_state")))) return false;
    if (previous) {
      if (data.contains("ccr:auto_enchant_original", net.minecraft.nbt.Tag.TAG_STRING))
        restore(stack, data.getString("ccr:auto_enchant_original"));
    } else {
      data.putString("ccr:auto_enchant_original", current);
      data.putString("ccr:auto_enchant_id", UUID.randomUUID().toString());
    }
    EnchantmentHelper.updateEnchantments(
        stack,
        builder -> {
          for (var entry : configured.entrySet()) {
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
        .sorted(
            Comparator.comparing(e -> e.getKey().unwrapKey().orElseThrow().location().toString()))
        .map(e -> e.getKey().unwrapKey().orElseThrow().location() + "=" + e.getIntValue())
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
            Integer level = GameplayMath.integer(fields[1], 1, 255);
            if (enchantment != null && level != null) builder.set(enchantment, level);
          }
        });
  }

  /** Inventory mutations and equipment use are corrected by the following server tick. */
  public void schedule(ServerPlayer player) {
    if (!CutCleanReborn.ready()
        || player.connection == null
        || correcting.contains(player.getUUID())
        || !scheduled.add(player.getUUID())) return;
    mod.next(
        () -> {
          scheduled.remove(player.getUUID());
          if (!player.isRemoved() && player.getServer() == mod.server()) tick(player);
        });
  }

  void clearTransientState() {
    scheduled.clear();
    correcting.clear();
  }

  public void tick(ServerPlayer player) {
    if (!correcting.add(player.getUUID())) return;
    try {
      correct(player);
    } finally {
      correcting.remove(player.getUUID());
    }
  }

  private void correct(ServerPlayer player) {
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
        player.displayClientMessage(
            Component.literal("§eCCR • Enchantement ramené au plafond configuré"), true);
    }
    if (mod.settings().bool(ROOT + ".enabled", false))
      for (var rule : mod.settings().entries(ROOT + ".armor-rules")) {
        if (!Settings.enabled(rule)) continue;
        int kept = 0, maximum = Math.clamp(Settings.number(rule, "maximum", 4), 0, 4);
        String target = ruleTarget(rule, "material");
        for (EquipmentSlot slot :
            List.of(
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
          ItemStack stack = player.getItemBySlot(slot);
          if (stack.isEmpty() || !matchesArmor(stack.getItem(), target) || ++kept <= maximum)
            continue;
          player.setItemSlot(slot, ItemStack.EMPTY);
          if (!player.getInventory().add(stack)) player.drop(stack, false);
          player.displayClientMessage(
              Component.literal("§eCCR • Armure " + target + " : maximum " + maximum), true);
        }
      }
    player.getInventory().setChanged();
  }

  public boolean maceBlocked() {
    return mod.settings().bool(ROOT + ".enabled", false)
        && mod.settings().bool(ROOT + ".unique-mace.enabled", false)
        && mod.settings().bool(ROOT + ".unique-mace.exists", false);
  }

  public void crafted(ServerPlayer player, ItemStack stack) {
    if (stack.is(Items.MACE)
        && mod.settings().bool(ROOT + ".enabled", false)
        && mod.settings().bool(ROOT + ".unique-mace.enabled", false)) {
      if (maceBlocked()) {
        stack.setCount(0);
        player.sendSystemMessage(Component.literal("§cCCR • La masse unique existe déjà."));
        return;
      }
      String identity = UUID.randomUUID().toString();
      CompoundTag data = data(stack);
      data.putString("ccr:unique_mace_id", identity);
      data(stack, data);
      mod.settings().set(ROOT + ".unique-mace.exists", true);
      mod.settings().set(ROOT + ".unique-mace.id", identity);
      mod.save();
      player.sendSystemMessage(Component.literal("§6CCR • Vous avez fabriqué la masse unique !"));
    }
    auto(stack);
    enforceCaps(stack);
  }

  public void destroyed(ItemStack stack) {
    if (!stack.is(Items.MACE) || !mod.settings().bool(ROOT + ".unique-mace.exists", false)) return;
    String identity = data(stack).getString("ccr:unique_mace_id"),
        active = mod.settings().text(ROOT + ".unique-mace.id", "");
    if (identity.isBlank() || (!active.isBlank() && !active.equals(identity))) return;
    mod.settings().set(ROOT + ".unique-mace.exists", false);
    mod.settings().set(ROOT + ".unique-mace.id", null);
    mod.save();
    mod.server()
        .getPlayerList()
        .broadcastSystemMessage(
            Component.literal(
                "§6CCR • La masse unique a été détruite. Une nouvelle peut être fabriquée."),
            false);
  }
}
