package fr.isaac.cutcleanreborn.fabric;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/** Semantic pages, not click history: pagination and edits do not add Escape levels. */
public final class RuleMenus {
  private final RuleRuntime mod;
  private final Map<UUID, Boolean> opening = new HashMap<>();
  private long generation;

  RuleMenus(RuleRuntime mod) {
    this.mod = mod;
  }

  public void root(ServerPlayer player) {
    main(player);
  }

  public void closeAll() {
    generation++;
    for (ServerPlayer player : mod.server.getPlayerList().getPlayers()) {
      if (player.containerMenu instanceof Panel panel) {
        panel.closed = true;
        player.closeContainer();
      } else if (player.containerMenu instanceof TextInput input) {
        input.closed = true;
        player.closeContainer();
      }
    }
  }

  public int submit(ServerPlayer player, String text) {
    if (!(player.containerMenu instanceof TextInput input)) return 0;
    String error = input.submit.apply(text);
    if (error == null) {
      input.closed = true;
      open(player, input.parent.get());
      return 1;
    }
    player.sendSystemMessage(Component.literal("CCR • " + error));
    return 0;
  }

  public void cancel(ServerPlayer player) {
    if (player.containerMenu instanceof TextInput) player.closeContainer();
  }

  public record Entry(
      Item icon,
      String title,
      List<String> lore,
      Consumer<ServerPlayer> click,
      Consumer<ServerPlayer> rightClick) {
    Entry(Item icon, String title, List<String> lore, Consumer<ServerPlayer> click) {
      this(icon, title, lore, click, null);
    }
  }

  public record Page(
      String title, Supplier<Page> parent, Supplier<List<Entry>> entries, int index) {
    Page(String title, Supplier<Page> parent, Supplier<List<Entry>> entries) {
      this(title, parent, entries, -1);
    }
  }

  public void main(ServerPlayer player) {
    open(player, mainPage());
  }

  private Entry entry(Item icon, String title, Runnable action, String... lore) {
    return new Entry(icon, title, List.of(lore), player -> action.run());
  }

  private Entry entry(Item icon, String title, Consumer<ServerPlayer> action, String... lore) {
    return new Entry(icon, title, List.of(lore), action);
  }

  private Entry folder(Item icon, String title, Supplier<Page> page, String... lore) {
    return entry(icon, title, player -> open(player, page.get()), lore);
  }

  private Entry toggle(
      Item icon, String title, String path, boolean fallback, Supplier<Page> page) {
    boolean enabled = mod.config.bool(path, fallback);
    return entry(
        icon,
        (enabled ? "§a" : "§c") + title + ": " + (enabled ? "ON" : "OFF"),
        player -> {
          writeSetting(path, !mod.config.bool(path, fallback));
          open(player, page.get());
        },
        "§7Click to toggle.");
  }

  private Entry number(
      Item icon, String title, String path, int fallback, int min, int max, Supplier<Page> page) {
    return entry(
        icon,
        title + ": §e" + mod.config.integer(path, fallback, min, max),
        player ->
            input(
                player,
                title,
                String.valueOf(mod.config.integer(path, fallback, min, max)),
                page,
                text -> {
                  Integer value = RuleMath.integer(text, min, max);
                  if (value == null) return "Enter a whole number from " + min + " to " + max;
                  writeSetting(path, value);
                  return null;
                }),
        "§7Range: " + min + " to " + max);
  }

  private Entry percent(
      Item icon, String title, String path, double fallback, Supplier<Page> page) {
    return entry(
        icon,
        title + ": §e" + mod.config.decimal(path, fallback, 0, 100) + "%",
        player ->
            input(
                player,
                title,
                String.valueOf(mod.config.decimal(path, fallback, 0, 100)),
                page,
                text -> {
                  Double value = RuleMath.percentage(text);
                  if (value == null) return "Enter a percentage from 0 to 100";
                  mod.config.set(path, value);
                  return null;
                }),
        "§7Range: 0–100%");
  }

  private void writeSetting(String path, Object value) {
    String[] parts = path.split("\\.");
    if (parts.length == 3 && parts[0].equals("mobs")) {
      boolean animal = LootRules.ANIMALS.contains(parts[1]);
      Object inherited =
          parts[2].equals("cooked-food")
              ? animal && mod.config.bool("animal-drops.cook", true)
              : parts[2].equals("xp-bonus")
                  ? animal ? mod.config.integer("animal-drops.xp-bonus", 0, 0, 50) : 0
                  : null;
      if (inherited != null && inherited.equals(value)) value = null;
    }
    mod.config.set(path, value);
  }

  private Entry reset(String title, String path, Supplier<Page> page) {
    return entry(
        Items.TNT,
        "§c" + title,
        player ->
            confirm(
                player,
                title,
                page,
                () -> {
                  mod.config.set(path, null);
                }),
        "§7Click to review.");
  }

  private Entry resetDrops(String root, Supplier<Page> page) {
    return entry(
        Items.BUCKET,
        "§cReset Drops",
        player ->
            confirm(
                player,
                "Restore vanilla drops?",
                page,
                () -> {
                  Map<String, Object> changes = new LinkedHashMap<>();
                  changes.put(root + ".drops", null);
                  changes.put(root + ".vanilla-overrides", null);
                  mod.config.batch(changes);
                }),
        "§7Reset custom drops.",
        "§7Cooking and bonus XP are unchanged.");
  }

  private Page mainPage() {
    return new Page(
        "CCR Config",
        null,
        () ->
            List.of(
                folder(
                    Items.BEEF,
                    "§6Mob Drops",
                    this::mobs,
                    "§7Drops, cooked food and XP."),
                folder(
                    Items.DIAMOND_ORE,
                    "§bOre Drops",
                    this::ores,
                    "§7Drops, Fortune, auto smelt and XP."),
                folder(
                    Items.IRON_AXE,
                    "§aTimber",
                    this::timber,
                    "§7Fell natural trees with your chosen tools."),
                folder(
                    Items.ENCHANTED_BOOK,
                    "§dAutomatic Enchantments",
                    this::automatic,
                    "§7Enchantments for new armor, tools and weapons."),
                folder(
                    Items.OAK_LOG,
                    "§eClean Stacks",
                    this::clean,
                    "§7Combine wood and stone into matching stacks."),
                folder(
                    Items.SUGAR_CANE,
                    "§aBetter Sugar Cane",
                    this::cane,
                    "§7Player break and support break drops."),
                folder(
                    Items.DIAMOND_CHESTPLATE,
                    "§cStuff Rules",
                    this::stuff,
                    "§7Armor limits, enchantment caps and unique mace."),
                entry(
                    Items.COMPASS,
                    "§fReload Config",
                    player -> {
                      try {
                        mod.reload();
                        open(player, mainPage());
                      } catch (RuntimeException failure) {
                        player.sendSystemMessage(
                            Component.literal(
                                "§cCCR • Reload failed; previous settings kept: "
                                    + failure.getMessage()));
                      }
                    },
                    "§7Reload config.yml.")));
  }

  private Page mobs() {
    return new Page(
        "CCR • Mob Drops",
        this::mainPage,
        () -> {
          List<Entry> list = new ArrayList<>();
          list.add(folder(Items.COOKED_BEEF, "§6Global Settings", this::mobGlobals));
          list.add(
              entry(
                  Items.COMPASS,
                  "§bSearch Mobs",
                  player -> {
                    Page[] destination = {mobs()};
                    input(
                        player,
                        "Mob ID",
                        "minecraft:cow",
                        () -> destination[0],
                        text -> {
                          String key = RuleConfig.itemId(text.trim());
                          var type =
                              BuiltInRegistries.ENTITY_TYPE.stream()
                                  .filter(
                                      value ->
                                          BuiltInRegistries.ENTITY_TYPE
                                              .getKey(value)
                                              .toString()
                                              .equals(key))
                                  .findFirst()
                                  .orElse(null);
                          if (type == null
                              || key.equals("minecraft:player")
                              || !NativeAccess.living(type, mod.server))
                            return "Unknown mob";
                          destination[0] = mob(RuleConfig.materialKey(key));
                          return null;
                        });
                  }));
          List<net.minecraft.world.entity.EntityType<?>> types = new ArrayList<>();
          BuiltInRegistries.ENTITY_TYPE.forEach(types::add);
          types.sort(
              Comparator.comparingInt(
                      (net.minecraft.world.entity.EntityType<?> type) ->
                          mobScore(
                                      RuleConfig.materialKey(
                                          BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()))
                                  > 0
                              ? 0
                              : 1)
                  .thenComparing(
                      Comparator.comparingInt(
                              (net.minecraft.world.entity.EntityType<?> type) ->
                                  mobScore(
                                      RuleConfig.materialKey(
                                          BuiltInRegistries.ENTITY_TYPE.getKey(type).toString())))
                          .reversed())
                  .thenComparing(
                      Comparator.comparingLong(
                              (net.minecraft.world.entity.EntityType<?> type) ->
                                  mod.config.get(
                                              "mobs."
                                                  + RuleConfig.materialKey(
                                                      BuiltInRegistries.ENTITY_TYPE
                                                          .getKey(type)
                                                          .toString())
                                                  + ".last-edited")
                                          instanceof Number n
                                      ? n.longValue()
                                      : 0)
                          .reversed())
                  .thenComparing(type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()));
          for (var type : types) {
            if (BuiltInRegistries.ENTITY_TYPE.getKey(type).toString().equals("minecraft:player"))
              continue;
            if (!NativeAccess.living(type, mod.server)) continue;
            String id =
                RuleConfig.materialKey(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
            Item icon =
                InventoryRules.item(
                    BuiltInRegistries.ENTITY_TYPE.getKey(type).toString().replace("minecraft:", "")
                        + "_spawn_egg");
            list.add(
                folder(
                    icon == null ? Items.BONE : icon,
                    "§f" + pretty(id),
                    () -> mob(id),
                    "§7Custom drops: " + mobScore(id),
                    "§7Drops, cooked food and XP."));
          }
          return list;
        });
  }

  private int mobScore(String id) {
    String root = "mobs." + id;
    boolean animal =
        List.of("COW", "MOOSHROOM", "CHICKEN", "PIG", "SHEEP", "RABBIT", "COD", "SALMON")
            .contains(id);
    int score =
        (int)
            (mod.config.entries(root + ".drops").stream().filter(LootRules::validDrop).count()
                + mod.config.entries(root + ".vanilla-overrides").stream()
                    .filter(LootRules::validDrop)
                    .count());
    boolean cook = animal && mod.config.bool("animal-drops.cook", true);
    int xp = animal ? mod.config.integer("animal-drops.xp-bonus", 0, 0, 50) : 0;
    if (mod.config.contains(root + ".cooked-food")
        && mod.config.bool(root + ".cooked-food", cook) != cook) score++;
    if (mod.config.contains(root + ".xp-bonus")
        && mod.config.integer(root + ".xp-bonus", xp, 0, 1_000_000) != xp) score++;
    return score;
  }

  private Page mobGlobals() {
    return new Page(
        "CCR • Global Drops",
        this::mobs,
        () ->
            List.of(
                toggle(
                    Items.CAMPFIRE,
                    "Cook Animal Food",
                    "animal-drops.cook",
                    true,
                    this::mobGlobals),
                number(
                    Items.BEEF,
                    "Animal Drop Multiplier",
                    "animal-drops.multiplier",
                    1,
                    1,
                    5,
                    this::mobGlobals),
                number(
                    Items.EXPERIENCE_BOTTLE,
                    "Animal Bonus XP",
                    "animal-drops.xp-bonus",
                    0,
                    0,
                    50,
                    this::mobGlobals),
                number(
                    Items.CHEST,
                    "Collection Range",
                    "animal-drops.collection-range",
                    0,
                    0,
                    32,
                    this::mobGlobals),
                toggle(
                    Items.IRON_SWORD,
                    "Looting on Vanilla Drops",
                    "mob-drops.looting-vanilla",
                    true,
                    this::mobGlobals),
                toggle(
                    Items.DIAMOND_SWORD,
                    "Looting on Extra Drops",
                    "mob-drops.looting-custom",
                    false,
                    this::mobGlobals)));
  }

  private Page mob(String id) {
    String path = "mobs." + id;
    Supplier<Page> here = () -> mob(id);
    return new Page(
        "CCR • " + pretty(id),
        this::mobs,
        () ->
            List.of(
                toggle(
                    Items.CAMPFIRE,
                    "Cook Food",
                    path + ".cooked-food",
                    List.of(
                                "COW",
                                "MOOSHROOM",
                                "CHICKEN",
                                "PIG",
                                "SHEEP",
                                "RABBIT",
                                "COD",
                                "SALMON")
                            .contains(id)
                        && mod.config.bool("animal-drops.cook", true),
                    here),
                number(
                    Items.EXPERIENCE_BOTTLE,
                    "Bonus XP",
                    path + ".xp-bonus",
                    List.of(
                                "COW",
                                "MOOSHROOM",
                                "CHICKEN",
                                "PIG",
                                "SHEEP",
                                "RABBIT",
                                "COD",
                                "SALMON")
                            .contains(id)
                        ? mod.config.integer("animal-drops.xp-bonus", 0, 0, 50)
                        : 0,
                    0,
                    1_000_000,
                    here),
                folder(
                    Items.BOOK,
                    "§eVanilla Drops",
                    () -> vanillaMob(id, false),
                    "§7Preview and customize vanilla drops."),
                folder(
                    Items.CHEST,
                    "§aExtra Drops",
                    () -> drops(path + ".drops", false, here),
                    "§7Adds drops without changing vanilla drops."),
                resetDrops(path, here),
                reset("Reset Mob", path, here)));
  }

  private Page ores() {
    return new Page(
        "CCR • Ore Drops",
        this::mainPage,
        () -> {
          List<Entry> list = new ArrayList<>();
          list.add(
              toggle(
                  Items.FURNACE, "Auto Smelt", "ore-drops.auto-smelt", false, this::ores));
          list.add(
              toggle(
                  Items.DIAMOND_PICKAXE,
                  "Fortune on Vanilla Drops",
                  "ore-drops.fortune-vanilla",
                  true,
                  this::ores));
          list.add(
              toggle(
                  Items.GOLDEN_PICKAXE,
                  "Fortune on Extra Drops",
                  "ore-drops.fortune-custom",
                  false,
                  this::ores));
          LootRules.ORES.keySet().stream()
              .sorted()
              .forEach(
                  id ->
                      list.add(
                          folder(
                              InventoryRules.item(id),
                              "§f" + pretty(id),
                              () -> ore(id),
                              "§7Drops, XP and copy settings.")));
          return list;
        });
  }

  private Page ore(String id) {
    String path = "ore-drops." + id;
    Supplier<Page> here = () -> ore(id);
    return new Page(
        "CCR • " + pretty(id),
        this::ores,
        () ->
            List.of(
                number(
                    Items.EXPERIENCE_BOTTLE, "Bonus XP", path + ".xp-bonus", 0, 0, 1_000_000, here),
                folder(Items.BOOK, "§eVanilla Drop", () -> vanillaOre(id)),
                folder(
                    Items.CHEST,
                    "§aExtra Drops",
                    () -> drops(path + ".drops", false, here)),
                entry(
                    Items.WRITABLE_BOOK,
                    "§bCopy to Ore",
                    player ->
                        input(
                            player,
                            "Target Ore",
                            "deepslate_" + id,
                            here,
                            text -> {
                              String target =
                                  text.trim()
                                      .replace("minecraft:", "")
                                      .toLowerCase(java.util.Locale.ROOT);
                              if (!LootRules.ORES.containsKey(target)) return "Unknown ore";
                              if (target.equals(id)) return "Choose a different ore";
                              mod.config.set("ore-drops." + target, mod.config.section(path));
                              return null;
                            }),
                    "§7Copy drops and bonus XP."),
                resetDrops(path, here),
                reset("Reset Ore", path, here)));
  }

  private Page drops(String path, boolean source, Supplier<Page> parent) {
    Supplier<Page> here = () -> drops(path, source, parent);
    return new Page(
        "CCR • " + (path.endsWith(".drops") ? "Extra Drops" : "Vanilla Drops"),
        parent,
        () -> {
          List<Entry> list = new ArrayList<>();
          list.add(
              entry(
                  InventoryRules.item("minecraft:lime_dye"),
                  "§aAdd Item",
                  player ->
                      input(
                          player,
                          "Item ID",
                          "minecraft:diamond",
                          here,
                          text -> {
                            Item item = InventoryRules.item(text);
                            if (item == null || item == Items.AIR) return "Unknown Minecraft item";
                            List<Map<String, Object>> values = mod.config.entries(path);
                            Map<String, Object> rule = new LinkedHashMap<>();
                            rule.put("item", InventoryRules.id(item));
                            if (source) rule.put("source", InventoryRules.id(item));
                            rule.put("minimum", 1);
                            rule.put("maximum", 1);
                            rule.put("chance", 100.0);
                            values.add(rule);
                            mod.config.set(path, values);
                            return null;
                          })));
          List<Map<String, Object>> rules = mod.config.entries(path);
          for (int index = 0; index < rules.size(); index++) {
            int position = index;
            Map<String, Object> rule = rules.get(index);
            Item icon = InventoryRules.item(String.valueOf(rule.get("item")));
            list.add(
                folder(
                    icon == null ? Items.BARRIER : icon,
                    "§f" + rule.get("item") + " §7[" + (index + 1) + "]",
                    () -> drop(path, position, source, here),
                    "§7Amount: " + rule.get("minimum") + "–" + rule.get("maximum"),
                    "§7Chance: " + rule.get("chance") + "%"));
          }
          return list;
        });
  }

  private Page drop(String path, int index, boolean source, Supplier<Page> parent) {
    Supplier<Page> here = () -> drop(path, index, source, parent);
    return new Page(
        "CCR • Drop Item",
        parent,
        () -> {
          List<Map<String, Object>> values = mod.config.entries(path);
          if (index >= values.size()) return List.of();
          Map<String, Object> rule = values.get(index);
          List<Entry> list = new ArrayList<>();
          list.add(
              entry(
                  Items.ITEM_FRAME,
                  "§fItem: " + rule.get("item"),
                  player ->
                      input(
                          player,
                          "Output Item",
                          String.valueOf(rule.get("item")),
                          here,
                          text -> {
                            Item item = InventoryRules.item(text);
                            if (item == null || item == Items.AIR) return "Unknown item";
                            editRule(path, index, map -> map.put("item", InventoryRules.id(item)));
                            return null;
                          })));
          if (source)
            list.add(
                entry(
                    Items.BOOK,
                    "§eReplace: " + rule.getOrDefault("source", rule.get("item")),
                    player ->
                        input(
                            player,
                            "Vanilla Item to Replace",
                            String.valueOf(rule.getOrDefault("source", rule.get("item"))),
                            here,
                            text -> {
                              Item item = InventoryRules.item(text);
                              if (item == null || item == Items.AIR) return "Unknown item";
                              editRule(
                                  path, index, map -> map.put("source", InventoryRules.id(item)));
                              return null;
                            })));
          list.add(
              entry(
                  Items.HOPPER,
                  "§eAmount: " + rule.get("minimum") + "–" + rule.get("maximum"),
                  player ->
                      input(
                          player,
                          "Amount: 1 or 1-3",
                          rule.get("minimum") + "-" + rule.get("maximum"),
                          here,
                          text -> {
                            var range = RuleMath.range(text, 0, 64);
                            if (range == null)
                              return "Enter an amount from 0 to 64; minimum ≤ maximum";
                            editRule(
                                path,
                                index,
                                map -> {
                                  map.put("minimum", range.minimum());
                                  map.put("maximum", range.maximum());
                                });
                            return null;
                          })));
          list.add(
              entry(
                  Items.GOLD_NUGGET,
                  "§eChance: " + rule.get("chance") + "%",
                  player ->
                      input(
                          player,
                          "Chance (%)",
                          String.valueOf(rule.get("chance")),
                          here,
                          text -> {
                            Double chance = RuleMath.percentage(text);
                            if (chance == null) return "Enter a percentage from 0 to 100";
                            editRule(path, index, map -> map.put("chance", chance));
                            return null;
                          })));
          list.add(
              entry(
                  Items.TNT,
                  source ? "§aRestore Vanilla Drops" : "§cRemove Item",
                  player ->
                      confirm(
                          player,
                          "Reset these drops?",
                          parent,
                          () -> {
                            List<Map<String, Object>> current = mod.config.entries(path);
                            if (index < current.size()) current.remove(index);
                            mod.config.set(path, current);
                          })));
          return list;
        });
  }

  private void editRule(String path, int index, Consumer<Map<String, Object>> editor) {
    List<Map<String, Object>> rules = mod.config.entries(path);
    if (index < rules.size()) {
      editor.accept(rules.get(index));
      mod.config.set(path, rules);
    }
  }

  private void editRule(String path, String ruleId, Consumer<Map<String, Object>> editor) {
    mod.config.editEntryById(path, ruleId, editor);
  }

  private void inputRule(
      ServerPlayer player,
      String path,
      String ruleId,
      String title,
      String initial,
      Supplier<Page> parent,
      Function<String, String> submit) {
    input(
        player,
        title,
        initial,
        parent,
        text -> {
          if (mod.config.entryById(path, ruleId) == null) {
            player.sendSystemMessage(
                Component.literal(
                    "§eCCR • This rule was deleted. Changes"
                        + " cancelled."));
            return null;
          }
          return submit.apply(text);
        });
  }

  private Page vanillaMob(String id, boolean looting) {
    String path = "mobs." + id + ".vanilla-overrides";
    Supplier<Page> here = () -> vanillaMob(id, looting);
    return new Page(
        "CCR • Vanilla " + pretty(id) + (looting ? " / Looting III" : ""),
        () -> mob(id),
        () -> {
          List<Entry> list = new ArrayList<>();
          list.add(
              folder(
                  Items.IRON_SWORD,
                  looting ? "§ePreview Without Looting" : "§bPreview Looting III",
                  () -> vanillaMob(id, !looting),
                  "§7Vanilla drops preview."));
          list.add(
              folder(
                  Items.WRITABLE_BOOK,
                  "§eCustom Vanilla Drops",
                  () -> drops(path, true, here)));
          for (Map<String, Object> vanilla : mod.catalog.mob(id, looting)) {
            Item item = InventoryRules.item(String.valueOf(vanilla.get("item")));
            String source = String.valueOf(vanilla.get("item"));
            Entry preview =
                entry(
                    item,
                    "§f" + pretty(source),
                    player -> {
                      List<Map<String, Object>> values = mod.config.entries(path);
                      int index = -1;
                      for (int i = 0; i < values.size(); i++)
                        if (source.equals(
                            values.get(i).getOrDefault("source", values.get(i).get("item")))) {
                          index = i;
                          break;
                        }
                      if (index < 0) {
                        Map<String, Object> base =
                            mod.catalog.mob(id, false).stream()
                                .filter(value -> source.equals(value.get("item")))
                                .findFirst()
                                .orElse(vanilla);
                        Map<String, Object> rule = new LinkedHashMap<>(base);
                        rule.put("source", source);
                        values.add(rule);
                        index = values.size() - 1;
                        mod.config.set(path, values);
                      }
                      open(player, drop(path, index, true, here));
                    },
                    "§7Vanilla amount: " + vanilla.get("minimum") + "–" + vanilla.get("maximum"),
                    "§7Estimated chance: "
                        + String.format(java.util.Locale.ROOT, "%.1f", vanilla.get("chance"))
                        + "%",
                    "§eClick to edit.",
                    "§eRight-click to disable or restore.");
            list.add(
                new Entry(
                    preview.icon(),
                    preview.title(),
                    preview.lore(),
                    preview.click(),
                    player -> {
                      List<Map<String, Object>> current = mod.config.entries(path);
                      boolean disabled =
                          current.stream()
                              .anyMatch(
                                  rule ->
                                      source.equals(rule.getOrDefault("source", rule.get("item")))
                                          && rule.get("chance") instanceof Number chance
                                          && chance.doubleValue() <= 0);
                      current.removeIf(
                          rule -> source.equals(rule.getOrDefault("source", rule.get("item"))));
                      if (!disabled) {
                        Map<String, Object> base =
                            mod.catalog.mob(id, false).stream()
                                .filter(value -> source.equals(value.get("item")))
                                .findFirst()
                                .orElse(vanilla);
                        Map<String, Object> rule = new LinkedHashMap<>(base);
                        rule.put("source", source);
                        rule.put("chance", 0.0);
                        current.add(rule);
                      }
                      mod.config.set(path, current);
                      open(player, here.get());
                    }));
          }
          if (list.size() == 2)
            list.add(
                entry(
                    Items.BARRIER,
                    "§7No drops found.",
                    player -> {},
                    "§7You can also add a drop manually."));
          return list;
        });
  }

  private Page vanillaOre(String id) {
    String path = "ore-drops." + id + ".vanilla-overrides";
    Supplier<Page> here = () -> vanillaOre(id);
    return new Page(
        "CCR • Drops / " + pretty(id),
        () -> ore(id),
        () -> {
          Map<String, Object> vanilla = VanillaCatalog.ore(id, false),
              fortune = VanillaCatalog.ore(id, true);
          List<Entry> list = new ArrayList<>();
          list.add(
              entry(
                  LootRules.ORES.get(id),
                  "§eEdit Vanilla Drop",
                  player -> {
                    List<Map<String, Object>> values = mod.config.entries(path);
                    if (values.isEmpty()) {
                      mod.config.set(path, List.of(new LinkedHashMap<>(vanilla)));
                    }
                    open(player, drop(path, 0, false, here));
                  },
                  "§7Without Fortune: " + vanilla.get("minimum") + "–" + vanilla.get("maximum"),
                  "§7Fortune III: " + fortune.get("minimum") + "–" + fortune.get("maximum"),
                  "§7Silk Touch keeps the ore block."));
          list.add(reset("Restore Vanilla Drop", path, here));
          return list;
        });
  }

  private Page timber() {
    return new Page(
        "CCR • Timber",
        this::mainPage,
        () ->
            List.of(
                toggle(Items.IRON_AXE, "Enable Timber", "tree-cutter.enabled", true, this::timber),
                folder(
                    Items.DIAMOND_AXE,
                    "§bTools & Activation",
                    this::timberTools,
                    "§7Choose which tools can fell trees."),
                folder(
                    Items.CLOCK,
                    "§eBreaking & Limits",
                    this::timberTime,
                    "§7Mining effort based on tree size."),
                folder(Items.OAK_LEAVES, "§aLeaves & Apples", this::timberHarvest),
                toggle(
                    Items.LEATHER_BOOTS,
                    "Require Sneaking",
                    "tree-cutter.sneak-only",
                    false,
                    this::timber),
                number(
                    Items.OAK_LOG,
                    "Maximum Tree Size",
                    "tree-cutter.max-logs",
                    256,
                    2,
                    2048,
                    this::timber),
                number(
                    Items.HOPPER,
                    "Blocks per Tick",
                    "tree-cutter.blocks-per-tick",
                    12,
                    1,
                    64,
                    this::timber),
                reset("Reset Timber", "tree-cutter", this::timber)));
  }

  private Page timberTools() {
    return new Page(
        "Timber • Tools & Activation",
        this::timber,
        () -> {
          List<Entry> list = new ArrayList<>();
          list.add(
              toggle(
                  Items.STICK,
                  "Hand / Other Tools",
                  "tree-cutter.hand",
                  false,
                  this::timberTools));
          BuiltInRegistries.ITEM.stream()
              .filter(item -> InventoryRules.id(item).endsWith("_axe"))
              .forEach(
                  item ->
                      list.add(
                          toggle(
                              item,
                              pretty(InventoryRules.id(item)),
                              "tree-cutter.axes." + RuleConfig.materialKey(InventoryRules.id(item)),
                              true,
                              this::timberTools)));
          return list;
        });
  }

  private Page timberTime() {
    return new Page(
        "Timber • Breaking & Limits",
        this::timber,
        () ->
            List.of(
                toggle(
                    Items.CLOCK,
                    "Scale Mining Effort",
                    "tree-cutter.break-time.enabled",
                    false,
                    this::timberTime),
                percent(
                    Items.REPEATER,
                    "Effort per Extra Log",
                    "tree-cutter.break-time.percent",
                    100,
                    this::timberTime)));
  }

  private Page timberHarvest() {
    return new Page(
        "Timber • Leaves & Apples",
        this::timber,
        () ->
            List.of(
                toggle(
                    Items.OAK_LEAVES,
                    "Harvest Tree Leaves",
                    "tree-cutter.break-leaves",
                    false,
                    this::timberHarvest),
                percent(
                    Items.APPLE,
                    "Apple Chance",
                    "tree-cutter.apple-chance",
                    0.5,
                    this::timberHarvest),
                reset(
                    "Reset Apple Chance",
                    "tree-cutter.apple-chance",
                    this::timberHarvest)));
  }

  private Page cane() {
    return new Page(
        "CCR • Better Sugar Cane",
        this::mainPage,
        () ->
            List.of(
                toggle(
                    Items.SUGAR_CANE,
                    "Enable Bonus",
                    "better-sugar-cane.enabled",
                    false,
                    this::cane),
                folder(
                    Items.IRON_AXE,
                    "§bPlayer Break",
                    () -> caneCause("player"),
                    "§7Drops when a player breaks the cane."),
                folder(
                    Items.SAND,
                    "§eSupport Break",
                    () -> caneCause("support"),
                    "§7Drops when the block underneath breaks."),
                reset("Reset Sugar Cane", "better-sugar-cane", this::cane)));
  }

  private Page caneCause(String cause) {
    String path = "better-sugar-cane." + cause;
    Supplier<Page> here = () -> caneCause(cause);
    return new Page(
        "Cane • " + (cause.equals("player") ? "Player Break" : "Support Break"),
        this::cane,
        () ->
            List.of(
                toggle(Items.SUGAR_CANE, "Enable", path + ".enabled", true, here),
                percent(Items.GOLD_NUGGET, "Bonus Chance", path + ".chance", 100, here),
                entry(
                    Items.HOPPER,
                    "§eFinal Amount: "
                        + mod.config.integer(path + ".minimum", 1, 1, 64)
                        + "–"
                        + mod.config.integer(path + ".maximum", 3, 1, 64),
                    player ->
                        input(
                            player,
                            "Amount: 1-3",
                            "1-3",
                            here,
                            text -> {
                              var range = RuleMath.range(text, 1, 64);
                              if (range == null) return "Enter an amount from 1 to 64";
                              mod.config.set(path + ".minimum", range.minimum());
                              mod.config.set(path + ".maximum", range.maximum());
                              return null;
                            }),
                    "§7Total drops, not extra drops.",
                    "§7Player-placed cane uses vanilla drops.")));
  }

  private Page clean() {
    return new Page(
        "CCR • Clean Stacks",
        this::mainPage,
        () ->
            List.of(
                folder(Items.OAK_LOG, "§aWood & Planks", () -> cleanGroup("wood")),
                folder(Items.STONE, "§7Stone conversions.", () -> cleanGroup("stone")),
                entry(
                    Items.LEVER,
                    "§eMy Clean Stacks",
                    player -> {
                      mod.config.set(
                          "clean-stacks.players." + player.getUUID(),
                          !mod.inventory.cleanEnabled(player));
                      mod.inventory.correct(player);
                      open(player, clean());
                    },
                    "§7/cleanstacks on|off|status"),
                reset("Reset Wood Conversions", "clean-stacks.wood", this::clean),
                reset("Reset Stone Conversions", "clean-stacks.stone", this::clean)));
  }

  private Page cleanGroup(String group) {
    Supplier<Page> here = () -> cleanGroup(group);
    return new Page(
        "CCR • Conversions / " + group,
        this::clean,
        () -> {
          List<Entry> list = new ArrayList<>();
          list.add(cleanBulk(group, "all", "Convert All", here));
          if (group.equals("wood")) {
            list.add(cleanBulk(group, "logs", "Convert Logs", here));
            list.add(cleanBulk(group, "stripped", "Convert Stripped Logs", here));
            list.add(cleanBulk(group, "planks", "Convert Planks", here));
          }
          BuiltInRegistries.ITEM.stream()
              .filter(item -> group.equals(InventoryRules.category(item)))
              .sorted(Comparator.comparing(InventoryRules::id))
              .forEach(
                  item -> {
                    String path =
                        "clean-stacks."
                            + group
                            + "."
                            + RuleConfig.materialKey(InventoryRules.id(item));
                    String defaultValue =
                        group.equals("stone")
                            ? "minecraft:stone"
                            : InventoryRules.id(item).endsWith("_planks")
                                ? "minecraft:oak_planks"
                                : "minecraft:oak_log";
                    list.add(
                        new Entry(
                            item,
                            "§f" + pretty(InventoryRules.id(item)),
                            List.of(
                                "§7Output: " + mod.config.text(path, defaultValue),
                                "§eClick to choose. Right-click to reset."),
                            player ->
                                input(
                                    player,
                                    "Output Item",
                                    mod.config.text(path, defaultValue),
                                    here,
                                    text -> {
                                      Item target = InventoryRules.item(text);
                                      if (target == null || target == Items.AIR)
                                        return "Unknown item";
                                      mod.inventory.setCleanTarget(item, target);
                                      return null;
                                    }),
                            player -> {
                              mod.config.set(path, null);
                              open(player, here.get());
                            }));
                  });
          return list;
        });
  }

  private Entry cleanBulk(String group, String kind, String title, Supplier<Page> here) {
    return entry(
        Items.HOPPER,
        "§b" + title,
        player ->
            input(
                player,
                "Group Output",
                group.equals("stone")
                    ? "minecraft:stone"
                    : kind.equals("planks") ? "minecraft:oak_planks" : "minecraft:oak_log",
                here,
                text -> {
                  Item target = InventoryRules.item(text);
                  if (target == null || target == Items.AIR) return "Unknown item";
                  Map<String, Object> changes = new LinkedHashMap<>();
                  BuiltInRegistries.ITEM.stream()
                      .filter(
                          item ->
                              group.equals(InventoryRules.category(item))
                                  && (kind.equals("all")
                                      || InventoryRules.id(item).endsWith("_planks")
                                          == kind.equals("planks")))
                      .forEach(
                          item ->
                              changes.put(
                                  "clean-stacks."
                                      + group
                                      + "."
                                      + RuleConfig.materialKey(InventoryRules.id(item)),
                                  InventoryRules.id(target)));
                  String targetGroup = InventoryRules.category(target);
                  if (targetGroup != null)
                    changes.put(
                        "clean-stacks."
                            + targetGroup
                            + "."
                            + RuleConfig.materialKey(InventoryRules.id(target)),
                        InventoryRules.id(target));
                  mod.config.batch(changes);
                  return null;
                }),
        "§7Set an output for this group.");
  }

  private Page automatic() {
    return new Page(
        "CCR • Auto Enchantments",
        this::mainPage,
        () ->
            List.of(
                toggle(
                    Items.ENCHANTED_BOOK,
                    "Enable",
                    "automatic-enchantments.enabled",
                    true,
                    this::automatic),
                folder(Items.DIAMOND_CHESTPLATE, "§bArmor", () -> autoGroup("armor")),
                folder(Items.DIAMOND_PICKAXE, "§eTools & Weapons", () -> autoGroup("tools")),
                entry(
                    Items.CLOCK,
                    "§aRefresh CCR Items",
                    player ->
                        confirm(
                            player,
                            "Refresh unchanged CCR items?",
                            this::automatic,
                            () -> {
                              mod.config.set(
                                  "automatic-enchantments.refresh-version",
                                  mod.config.integer(
                                          "automatic-enchantments.refresh-version",
                                          0,
                                          0,
                                          Integer.MAX_VALUE - 1)
                                      + 1);
                              mod.server
                                  .getPlayerList()
                                  .getPlayers()
                                  .forEach(mod.inventory::correct);
                            }),
                    "§7Player-modified enchantments are kept.",
                    "§7Refresh unchanged CCR items.")));
  }

  private Page autoGroup(String group) {
    return new Page(
        "CCR • Auto / " + group,
        this::automatic,
        () -> {
          List<Entry> list = new ArrayList<>();
          BuiltInRegistries.ITEM.stream()
              .filter(group.equals("armor") ? InventoryRules::armor : InventoryRules::tool)
              .sorted(Comparator.comparing(InventoryRules::id))
              .forEach(
                  item ->
                      list.add(
                          folder(
                              item,
                              "§f" + pretty(InventoryRules.id(item)),
                              () -> autoItem(group, item),
                              "§7"
                                  + mod.config
                                      .section(
                                          "automatic-enchantments."
                                              + group
                                              + "."
                                              + RuleConfig.materialKey(InventoryRules.id(item)))
                                      .size()
                                  + " enchantment(s)")));
          return list;
        });
  }

  private Page autoItem(String group, Item item) {
    String path =
        "automatic-enchantments." + group + "." + RuleConfig.materialKey(InventoryRules.id(item));
    Supplier<Page> here = () -> autoItem(group, item);
    return new Page(
        "CCR • Auto " + pretty(InventoryRules.id(item)),
        () -> autoGroup(group),
        () -> {
          List<Entry> list = new ArrayList<>();
          list.add(
              folder(
                  Items.ENCHANTED_BOOK,
                  "§aChoose Enchantment",
                  () -> enchantmentPicker(group, item),
                  "§7Choose an enchantment."));
          list.add(
              entry(
                  Items.WRITABLE_BOOK,
                  "§fEnter Enchantment and Level",
                  player ->
                      input(
                          player,
                          "Ex. sharpness 3",
                          "minecraft:unbreaking 3",
                          here,
                          text -> {
                            String[] fields = text.trim().split("\\s+");
                            if (fields.length != 2) return "Format: enchantment level";
                            var enchantment = mod.inventory.enchantment(fields[0]);
                            Integer level = RuleMath.integer(fields[1], 1, 255);
                            if (enchantment == null
                                || level == null
                                || !enchantment.value().canEnchant(new ItemStack(item)))
                              return "Invalid enchantment for this item (level 1–255)";
                            mod.config.set(
                                path + "." + mod.inventory.enchantmentId(enchantment), level);
                            return null;
                          })));
          mod.config
              .section(path)
              .forEach(
                  (id, value) ->
                      list.add(
                          entry(
                              Items.BOOK,
                              "§d" + id + " §e" + value,
                              player ->
                                  input(
                                      player,
                                      "Level (0 = remove)",
                                      String.valueOf(value),
                                      here,
                                      text -> {
                                        Integer level = RuleMath.integer(text, 0, 255);
                                        if (level == null) return "Enter a level from 0 to 255";
                                        mod.config.set(path + "." + id, level == 0 ? null : level);
                                        return null;
                                      }))));
          list.add(
              entry(
                  Items.WRITABLE_BOOK,
                  "§bCopy to Same Material",
                  player ->
                      confirm(
                          player,
                          "Copy without overwriting?",
                          here,
                          () -> {
                            String raw = InventoryRules.id(item);
                            int split = raw.lastIndexOf('_');
                            String prefix = split < 0 ? raw : raw.substring(0, split);
                            Map<String, Object> values = mod.config.section(path);
                            BuiltInRegistries.ITEM.stream()
                                .filter(
                                    candidate ->
                                        candidate != item
                                            && (group.equals("armor")
                                                ? InventoryRules.armor(candidate)
                                                : InventoryRules.tool(candidate))
                                            && InventoryRules.id(candidate)
                                                .startsWith(prefix + "_"))
                                .forEach(
                                    candidate -> {
                                      String target =
                                          "automatic-enchantments."
                                              + group
                                              + "."
                                              + RuleConfig.materialKey(
                                                  InventoryRules.id(candidate));
                                      for (var value : values.entrySet()) {
                                        var enchantment = mod.inventory.enchantment(value.getKey());
                                        if (enchantment != null
                                            && enchantment
                                                .value()
                                                .canEnchant(new ItemStack(candidate))
                                            && !mod.config.contains(target + "." + value.getKey()))
                                          mod.config.set(
                                              target + "." + value.getKey(), value.getValue());
                                      }
                                    });
                          })));
          list.add(reset("Delete Auto Enchantment Rule", path, () -> autoGroup(group)));
          return list;
        });
  }

  private Page enchantmentPicker(String group, Item item) {
    String path =
        "automatic-enchantments." + group + "." + RuleConfig.materialKey(InventoryRules.id(item));
    Supplier<Page> parent = () -> autoItem(group, item);
    return new Page(
        "CCR • Enchantments",
        parent,
        () -> {
          List<Entry> result = new ArrayList<>();
          mod.inventory
              .enchantments()
              .listElements()
              .filter(holder -> holder.value().canEnchant(new ItemStack(item)))
              .sorted(Comparator.comparing(mod.inventory::enchantmentId))
              .forEach(
                  holder -> {
                    String id = mod.inventory.enchantmentId(holder);
                    int current = mod.config.integer(path + "." + id, 0, 0, 255);
                    result.add(
                        entry(
                            Items.ENCHANTED_BOOK,
                            (current > 0 ? "§a" : "§f")
                                + pretty(id)
                                + (current > 0 ? " • " + current : ""),
                            player ->
                                input(
                                    player,
                                    pretty(id) + ": level",
                                    String.valueOf(Math.max(1, current)),
                                    parent,
                                    text -> {
                                      Integer level = RuleMath.integer(text, 1, 255);
                                      if (level == null) return "Enter a level from 1 to 255";
                                      mod.config.set(path + "." + id, level);
                                      return null;
                                    }),
                            "§7Compatible with this item.",
                            "§7Vanilla max level: " + holder.value().getMaxLevel(),
                            "§eClick to set the level."));
                  });
          return result;
        });
  }

  private Page targetPicker(boolean cap, String ruleId) {
    String path = InventoryRules.ROOT + (cap ? ".enchantment-caps" : ".armor-rules");
    if (mod.config.entryById(path, ruleId) == null) return ruleList(cap);
    Supplier<Page> parent = () -> rule(cap, ruleId);
    return new Page(
        "CCR • Choose Target",
        parent,
        () -> {
          Map<String, Object> rule = mod.config.entryById(path, ruleId);
          if (rule == null) return List.of();
          List<Entry> result = new ArrayList<>();
          result.add(
              folder(
                  Items.ITEM_FRAME,
                  "§bSelect Specific Items",
                  () -> exactTargets(cap, ruleId),
                  "§7Select one or more items."));
          result.add(
              entry(
                  Items.WRITABLE_BOOK,
                  "§fEnter Category or Item List",
                  player ->
                      inputRule(
                          player,
                          path,
                          ruleId,
                          "Rule Target",
                          String.valueOf(rule.get("target")),
                          parent,
                          text -> {
                            String target = text.trim().toUpperCase(java.util.Locale.ROOT);
                            if (!validTarget(target, cap))
                              return "Invalid category or item list";
                            editRule(path, ruleId, map -> map.put("target", target));
                            return null;
                          }),
                  "§7Enter item IDs separated by commas."));
          List<String> categories =
              cap ? List.of("ALL", "TOOLS", "SWORDS", "ARMOR") : armorFamilies();
          for (String target : categories) {
            result.add(
                entry(
                    cap ? Items.ENCHANTED_BOOK : Items.DIAMOND_CHESTPLATE,
                    (target.equals(rule.get("target")) ? "§a" : "§f") + pretty(target),
                    player -> {
                      editRule(path, ruleId, map -> map.put("target", target));
                      open(player, parent.get());
                    },
                    "§eClick to select this group."));
          }
          return result;
        });
  }

  private Page exactTargets(boolean cap, String ruleId) {
    String path = InventoryRules.ROOT + (cap ? ".enchantment-caps" : ".armor-rules");
    if (mod.config.entryById(path, ruleId) == null) return ruleList(cap);
    Supplier<Page> here = () -> exactTargets(cap, ruleId);
    return new Page(
        "CCR • Specific Items",
        () -> targetPicker(cap, ruleId),
        () -> {
          Map<String, Object> rule = mod.config.entryById(path, ruleId);
          if (rule == null) return List.of();
          java.util.Set<String> selected = new java.util.TreeSet<>();
          String target = String.valueOf(rule.get("target"));
          if (!(cap ? List.of("ALL", "ARMOR", "TOOLS", "SWORDS") : armorFamilies())
              .contains(target.toUpperCase(java.util.Locale.ROOT))) {
            for (String part : target.split(",")) {
              Item exact = InventoryRules.item(part.trim());
              if (exact != null) selected.add(InventoryRules.id(exact));
            }
          }
          var enchantment =
              cap ? mod.inventory.enchantment(String.valueOf(rule.get("enchantment"))) : null;
          List<Entry> result = new ArrayList<>();
          BuiltInRegistries.ITEM.stream()
              .filter(
                  item ->
                      item != Items.AIR
                          && (cap
                              ? (item == Items.BOOK
                                  || item == Items.ENCHANTED_BOOK
                                  || enchantment == null
                                  || enchantment.value().canEnchant(new ItemStack(item)))
                              : InventoryRules.armor(item)))
              .sorted(Comparator.comparing(InventoryRules::id))
              .forEach(
                  item -> {
                    String id = InventoryRules.id(item);
                    boolean checked = selected.contains(id);
                    result.add(
                        entry(
                            item,
                            (checked ? "§a✓ " : "§7○ ") + pretty(id),
                            player -> {
                              java.util.Set<String> changed = new java.util.TreeSet<>(selected);
                              if (!changed.add(id)) {
                                if (changed.size() == 1) {
                                  player.sendSystemMessage(
                                      Component.literal(
                                          "CCR • Select at least one item or choose a"
                                              + " category."));
                                  return;
                                }
                                changed.remove(id);
                              }
                              editRule(
                                  path,
                                  ruleId,
                                  map -> map.put("target", String.join(",", changed)));
                              open(player, here.get());
                            },
                            "§7Click to select or deselect.",
                            "§7Applies to the selected items."));
                  });
          return result;
        });
  }

  private Page stuff() {
    return new Page(
        "CCR • Stuff Rules",
        this::mainPage,
        () ->
            List.of(
                toggle(
                    Items.REDSTONE_TORCH,
                    "Enable Stuff Rules",
                    InventoryRules.ROOT + ".enabled",
                    false,
                    this::stuff),
                folder(
                    Items.DIAMOND_CHESTPLATE,
                    "§bArmor Limits",
                    () -> ruleList(false),
                    "§7Move excess equipped armor into the inventory."),
                folder(
                    Items.ENCHANTED_BOOK,
                    "§dEnchantment Caps",
                    () -> ruleList(true),
                    "§7Enchantment limits and XP refunds."),
                folder(
                    Items.MACE,
                    "§6Unique Mace",
                    this::mace,
                    "§7One mace at a time.")));
  }

  private Page mace() {
    String path = InventoryRules.ROOT + ".unique-mace";
    return new Page(
        "CCR • Unique Mace",
        this::stuff,
        () ->
            List.of(
                toggle(
                    Items.MACE, "Enable Unique Mace", path + ".enabled", false, this::mace),
                entry(
                    Items.COMPASS,
                    "§eStatus: "
                        + (mod.config.bool(path + ".exists", false)
                            ? "ALREADY CRAFTED"
                            : "AVAILABLE"),
                    player -> open(player, mace()),
                    "§7Only destruction unlocks a new craft."),
                entry(
                    Items.TNT,
                    "§cUnlock New Craft",
                    player ->
                        confirm(
                            player,
                            "Unlock a new mace craft?",
                            this::mace,
                            () -> {
                              mod.config.set(path + ".exists", false);
                              mod.config.set(path + ".id", null);
                            }),
                    "§cMay allow a second mace.")));
  }

  private Page ruleList(boolean cap) {
    String path = InventoryRules.ROOT + (cap ? ".enchantment-caps" : ".armor-rules");
    Supplier<Page> here = () -> ruleList(cap);
    return new Page(
        "CCR • " + (cap ? "Enchantment Caps" : "Armor Limits"),
        this::stuff,
        () -> {
          List<Entry> list = new ArrayList<>();
          list.add(
              entry(
                  InventoryRules.item("minecraft:lime_dye"),
                  "§aAdd Rule",
                  player -> {
                    List<Map<String, Object>> rules = mod.config.entries(path);
                    Map<String, Object> rule = new LinkedHashMap<>();
                    rule.put("id", UUID.randomUUID().toString());
                    rule.put("enabled", false);
                    rule.put("target", cap ? "SWORDS" : "DIAMOND");
                    rule.put("maximum", cap ? 3 : 4);
                    if (cap) {
                      rule.put("enchantment", "minecraft:sharpness");
                      rule.put("refund-xp", false);
                      rule.put("xp-per-removed-level", 0);
                    }
                    rules.add(rule);
                    mod.config.set(path, rules);
                    open(player, rule(cap, String.valueOf(rule.get("id"))));
                  },
                  "§7New rules start OFF."));
          List<Map<String, Object>> rules = mod.config.identifiedEntries(path);
          for (int index = 0; index < rules.size(); index++) {
            Map<String, Object> rule = rules.get(index);
            String position = String.valueOf(rule.get("id"));
            list.add(
                folder(
                    cap ? Items.ENCHANTED_BOOK : Items.DIAMOND_CHESTPLATE,
                    (RuleConfig.enabled(rule) ? "§a" : "§7")
                        + String.valueOf(rule.get("target"))
                        + " ≤ "
                        + rule.get("maximum"),
                    () -> rule(cap, position),
                    cap ? "§7" + rule.get("enchantment") : "§7Maximum equipped armor pieces."));
          }
          return list;
        });
  }

  private Page rule(boolean cap, String ruleId) {
    String path = InventoryRules.ROOT + (cap ? ".enchantment-caps" : ".armor-rules");
    if (mod.config.entryById(path, ruleId) == null) return ruleList(cap);
    Supplier<Page> here = () -> rule(cap, ruleId);
    return new Page(
        "CCR • " + (cap ? "Enchantment Cap" : "Equipped Armor"),
        () -> ruleList(cap),
        () -> {
          Map<String, Object> rule = mod.config.entryById(path, ruleId);
          if (rule == null) return List.of();
          List<Entry> list = new ArrayList<>();
          list.add(
              entry(
                  Items.LEVER,
                  (RuleConfig.enabled(rule) ? "§a" : "§c")
                      + "Rule "
                      + (RuleConfig.enabled(rule) ? "active" : "inactive"),
                  player -> {
                    editRule(path, ruleId, map -> map.put("enabled", !RuleConfig.enabled(map)));
                    open(player, here.get());
                  }));
          list.add(
              folder(
                  Items.ITEM_FRAME,
                  "§bTarget: " + rule.get("target"),
                  () -> targetPicker(cap, ruleId),
                  "§7Choose a category, material or specific items.",
                  "§7Select items or enter a list."));
          list.add(
              entry(
                  Items.HOPPER,
                  "§eMaximum: " + rule.get("maximum"),
                  player ->
                      inputRule(
                          player,
                          path,
                          ruleId,
                          "Maximum",
                          String.valueOf(rule.get("maximum")),
                          here,
                          text -> {
                            Integer value = RuleMath.integer(text, cap ? 1 : 0, cap ? 255 : 4);
                            if (value == null) return cap ? "Enter a maximum from 1 to 255" : "Enter a maximum from 0 to 4";
                            editRule(path, ruleId, map -> map.put("maximum", value));
                            return null;
                          })));
          if (cap) {
            list.add(
                entry(
                    Items.ENCHANTED_BOOK,
                    "§dEnchantment: " + rule.get("enchantment"),
                    player ->
                        inputRule(
                            player,
                            path,
                            ruleId,
                            "Enchantment",
                            String.valueOf(rule.get("enchantment")),
                            here,
                            text -> {
                              var enchantment = mod.inventory.enchantment(text.trim());
                              if (enchantment == null) return "Unknown enchantment";
                              editRule(
                                  path,
                                  ruleId,
                                  map ->
                                      map.put(
                                          "enchantment", mod.inventory.enchantmentId(enchantment)));
                              return null;
                            })));
            list.add(
                entry(
                    Items.EXPERIENCE_BOTTLE,
                    "§aRefund XP: " + Boolean.TRUE.equals(rule.get("refund-xp")),
                    player -> {
                      editRule(
                          path,
                          ruleId,
                          map -> map.put("refund-xp", !Boolean.TRUE.equals(map.get("refund-xp"))));
                      open(player, here.get());
                    },
                    "§7Refund levels spent at an anvil or enchanting table.",
                    "§7Refunds cannot exceed the levels spent."));
            list.add(
                entry(
                    Items.GOLD_NUGGET,
                    "§eXP per Removed Level: " + rule.getOrDefault("xp-per-removed-level", 0),
                    player ->
                        inputRule(
                            player,
                            path,
                            ruleId,
                            "XP per Removed Level",
                            String.valueOf(rule.getOrDefault("xp-per-removed-level", 0)),
                            here,
                            text -> {
                              Integer value = RuleMath.integer(text, 0, 1000);
                              if (value == null) return "Enter a value from 0 to 1000";
                              editRule(path, ruleId, map -> map.put("xp-per-removed-level", value));
                              return null;
                            })));
          }
          list.add(
              entry(
                  Items.WRITABLE_BOOK,
                  "§bCopy to Another Target",
                  player ->
                      inputRule(
                          player,
                          path,
                          ruleId,
                          "New Target",
                          String.valueOf(rule.get("target")),
                          () -> ruleList(cap),
                          text -> {
                            String target = text.trim().toUpperCase(java.util.Locale.ROOT);
                            if (!validTarget(target, cap)) return "Invalid target";
                            List<Map<String, Object>> current = mod.config.entries(path);
                            Map<String, Object> sourceRule = mod.config.entryById(path, ruleId);
                            if (sourceRule == null) return null;
                            Map<String, Object> copy = new LinkedHashMap<>(sourceRule);
                            copy.put("id", UUID.randomUUID().toString());
                            copy.put("enabled", false);
                            copy.put("target", target);
                            current.add(copy);
                            mod.config.set(path, current);
                            return null;
                          }),
                  "§7The copy starts OFF."));
          list.add(
              entry(
                  Items.TNT,
                  "§cDelete Rule",
                  player ->
                      confirm(
                          player,
                          "Delete this rule?",
                          () -> ruleList(cap),
                          () -> {
                            mod.config.removeEntryById(path, ruleId);
                          })));
          return list;
        });
  }

  private static List<String> armorFamilies() {
    List<String> result = new ArrayList<>();
    result.add("ARMOR");
    BuiltInRegistries.ITEM.stream()
        .filter(InventoryRules::armor)
        .map(item -> RuleConfig.materialKey(InventoryRules.id(item)))
        .map(id -> id.substring(0, id.lastIndexOf('_')))
        .distinct()
        .sorted()
        .forEach(result::add);
    return result;
  }

  private static boolean validTarget(String value, boolean cap) {
    if ((cap ? List.of("ALL", "TOOLS", "SWORDS", "ARMOR") : armorFamilies()).contains(value))
      return true;
    for (String part : value.split(",", -1)) {
      Item item = InventoryRules.item(part);
      if (item == null || item == Items.AIR || (!cap && !InventoryRules.armor(item))) return false;
    }
    return true;
  }

  private void confirm(ServerPlayer player, String title, Supplier<Page> parent, Runnable action) {
    open(
        player,
        new Page(
            "CCR • Confirmation",
            parent,
            () ->
                List.of(
                    entry(
                        InventoryRules.item("minecraft:lime_concrete"),
                        "§aConfirm: " + title,
                        p -> {
                          action.run();
                          open(p, parent.get());
                        }),
                    entry(
                        InventoryRules.item("minecraft:red_concrete"),
                        "§cCancel",
                        p -> open(p, parent.get())))));
  }

  public void open(ServerPlayer player, Page page) {
    if (!NativeAccess.admin(((ServerPlayer) player).createCommandSourceStack())) {
      player.sendSystemMessage(Component.literal("§cCCR • Admin permission required."));
      return;
    }
    Page destination = withContext(page, player.containerMenu instanceof Panel panel ? panel.page : null);
    opening.put(player.getUUID(), true);
    try {
      player.openMenu(
          new SimpleMenuProvider(
              (id, inventory, p) -> new Panel(id, inventory, destination),
              Component.literal(destination.title())));
    } finally {
      opening.remove(player.getUUID());
    }
  }

  private Page withContext(Page destination, Page current) {
    if (current != null) {
      if (destination.title().equals(current.title()))
        return new Page(destination.title(), current.parent(), destination.entries(),
            destination.index() < 0 ? current.index() : destination.index());
      if (current.parent() != null) {
        Page parent = current.parent().get();
        if (destination.title().equals(parent.title())) return withContext(destination, parent);
      }
      if (destination.parent() != null && destination.parent().get().title().equals(current.title()))
        return new Page(destination.title(), () -> current, destination.entries(), Math.max(0, destination.index()));
    }
    return new Page(destination.title(), destination.parent(), destination.entries(), Math.max(0, destination.index()));
  }

  private void input(
      ServerPlayer player,
      String title,
      String initial,
      Supplier<Page> parent,
      Function<String, String> submit) {
    Page current = player.containerMenu instanceof Panel panel ? panel.page : null;
    Supplier<Page> returnPage = () -> withContext(parent.get(), current);
    opening.put(player.getUUID(), true);
    try {
      player.openMenu(
          new SimpleMenuProvider(
              (id, inventory, p) -> new TextInput(id, inventory, title, initial, returnPage, submit),
              Component.literal(title)));
    } finally {
      opening.remove(player.getUUID());
    }
  }

  private static String pretty(String value) {
    String text =
        value.replace("minecraft:", "").replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
    return text.isBlank() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }

  private static ItemStack icon(Item item, String title, List<String> lore) {
    ItemStack stack = new ItemStack(item == null ? Items.BARRIER : item);
    stack.set(DataComponents.CUSTOM_NAME, Component.literal(title));
    stack.set(
        DataComponents.LORE,
        new ItemLore(lore.stream().map(Component::literal).map(Component.class::cast).toList()));
    return stack;
  }

  private final class Panel extends ChestMenu {
    private final Page page;
    private final Map<Integer, Consumer<ServerPlayer>> actions = new HashMap<>();
    private final Map<Integer, Consumer<ServerPlayer>> rightActions = new HashMap<>();
    private boolean closed;

    Panel(int id, Inventory inventory, Page page) {
      super(MenuType.GENERIC_9x6, id, inventory, new SimpleContainer(54), 6);
      for (int slot = 0; slot < 54; slot++)
        getSlot(slot)
            .set(icon(InventoryRules.item("minecraft:gray_stained_glass_pane"), " ", List.of()));
      getSlot(4)
          .set(
              icon(
                  Items.BOOK,
                  "§6" + page.title(),
                  List.of()));
      List<Entry> entries = page.entries().get();
      int pages = Math.max(1, (entries.size() + 35) / 36),
          index = Math.clamp(page.index(), 0, pages - 1);
      this.page = new Page(page.title(), page.parent(), page.entries(), index);
      for (int offset = 0; offset < 36 && index * 36 + offset < entries.size(); offset++) {
        Entry entry = entries.get(index * 36 + offset);
        int slot = 9 + offset;
        getSlot(slot).set(icon(entry.icon(), entry.title(), entry.lore()));
        actions.put(slot, entry.click());
        if (entry.rightClick() != null) rightActions.put(slot, entry.rightClick());
      }
      if (page.parent() != null) {
        getSlot(45).set(icon(Items.ARROW, "§eBack", List.of("§7Esc")));
        actions.put(45, player -> open(player, page.parent().get()));
      }
      if (index > 0) {
        getSlot(48).set(icon(Items.PAPER, "§ePrevious Page", List.of()));
        actions.put(
            48,
            player ->
                open(player, new Page(page.title(), page.parent(), page.entries(), index - 1)));
      }
      if (pages > 1)
        getSlot(49)
            .set(icon(Items.MAP, "§fPage " + (index + 1) + " / " + pages, List.of()));
      if (index + 1 < pages) {
        getSlot(50).set(icon(Items.PAPER, "§eNext Page", List.of()));
        actions.put(
            50,
            player ->
                open(player, new Page(page.title(), page.parent(), page.entries(), index + 1)));
      }
      getSlot(53).set(icon(Items.BARRIER, "§cClose CCR", List.of()));
      actions.put(
          53,
          player -> {
            closed = true;
            player.closeContainer();
          });
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
      if (!(player instanceof ServerPlayer serverPlayer)) return;
      Consumer<ServerPlayer> action =
          button == 0 ? actions.get(slot) : button == 1 ? rightActions.get(slot) : null;
      if (type == ClickType.PICKUP && action != null)
        mod.later(
            () -> {
              if (serverPlayer.containerMenu == this
                  && NativeAccess.admin(serverPlayer.createCommandSourceStack()))
                action.accept(serverPlayer);
            });
      sendAllDataToRemote();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
      return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
      return true;
    }

    @Override
    public void removed(Player player) {
      super.removed(player);
      long expectedGeneration = generation;
      if (!closed
          && !opening.containsKey(player.getUUID())
          && page.parent() != null
          && player instanceof ServerPlayer serverPlayer)
        mod.later(
            () -> {
              if (generation == expectedGeneration
                  && serverPlayer.connection != null
                  && serverPlayer.containerMenu == serverPlayer.inventoryMenu)
                open(serverPlayer, page.parent().get());
            });
    }
  }

  private final class TextInput extends AnvilMenu {
    private String value;
    private final Supplier<Page> parent;
    private final Function<String, String> submit;
    private boolean closed;

    TextInput(
        int id,
        Inventory inventory,
        String title,
        String initial,
        Supplier<Page> parent,
        Function<String, String> submit) {
      super(id, inventory, ContainerLevelAccess.NULL);
      this.parent = parent;
      this.submit = submit;
      this.value = initial;
      getSlot(0)
          .set(
              icon(
                  Items.PAPER,
                  initial,
                  List.of("§7Click the result to apply. Esc to cancel.")));
      createResult();
    }

    @Override
    public boolean stillValid(Player player) {
      return true;
    }

    @Override
    public boolean setItemName(String text) {
      value = text;
      createResult();
      // Correct the client's predicted rename cost without resetting its typed text.
      if (player instanceof ServerPlayer viewer
          && viewer.connection != null
          && viewer.containerMenu == this)
        viewer.connection.send(new ClientboundContainerSetDataPacket(containerId, 0, 0));
      return true;
    }

    @Override
    public void createResult() {
      if (resultSlots == null) return;
      resultSlots.setItem(
          0,
          icon(
              InventoryRules.item("minecraft:lime_dye"),
              "§aApply: " + (value == null ? "" : value),
              List.of("§7Click to apply. Esc to cancel.")));
      setData(0, 0);
      broadcastChanges();
    }

    @Override
    protected boolean mayPickup(Player player, boolean hasStack) {
      return true;
    }

    @Override
    protected void onTake(Player player, ItemStack stack) {}

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
      return ItemStack.EMPTY;
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
      if (slot != 2
          || button != 0
          || type != ClickType.PICKUP
          || !(player instanceof ServerPlayer serverPlayer)) return;
      mod.later(
          () -> {
            if (serverPlayer.containerMenu != this
                || submit == null
                || !NativeAccess.admin(serverPlayer.createCommandSourceStack())) return;
            String error = submit.apply(value == null ? "" : value);
            if (error == null) {
              closed = true;
              open(serverPlayer, parent.get());
            } else {
              serverPlayer.sendSystemMessage(Component.literal("§cCCR • " + error));
              resultSlots.setItem(
                  0,
                  icon(Items.BARRIER, "§c" + error, List.of("§7Correct the value and try again.")));
              broadcastChanges();
            }
          });
      sendAllDataToRemote();
    }

    @Override
    public void removed(Player player) {
      long expectedGeneration = generation;
      if (!closed
          && !opening.containsKey(player.getUUID())
          && parent != null
          && player instanceof ServerPlayer serverPlayer)
        mod.later(
            () -> {
              if (generation == expectedGeneration
                  && serverPlayer.connection != null
                  && serverPlayer.containerMenu == serverPlayer.inventoryMenu)
                open(serverPlayer, parent.get());
            });
    }
  }
}
