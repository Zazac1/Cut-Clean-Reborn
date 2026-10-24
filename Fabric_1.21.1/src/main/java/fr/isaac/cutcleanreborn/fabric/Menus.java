package fr.isaac.cutcleanreborn.fabric;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.item.enchantment.Enchantment;

/** Semantic pages, not click history: pagination and edits do not add Escape levels. */
public final class Menus {
  private final CutCleanReborn mod;
  private final Map<UUID, Boolean> opening = new HashMap<>();
  private int epoch;

  Menus(CutCleanReborn mod) {
    this.mod = mod;
  }

  public record Entry(
      Item icon,
      String title,
      List<String> lore,
      Consumer<ServerPlayer> click,
      Consumer<ServerPlayer> rightClick,
      Consumer<ServerPlayer> shiftClick) {
    Entry(Item icon, String title, List<String> lore, Consumer<ServerPlayer> click) {
      this(icon, title, lore, click, null, null);
    }

    Entry(
        Item icon,
        String title,
        List<String> lore,
        Consumer<ServerPlayer> click,
        Consumer<ServerPlayer> rightClick) {
      this(icon, title, lore, click, rightClick, null);
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

  public void closeAll() {
    epoch++;
    for (ServerPlayer player : mod.server().getPlayerList().getPlayers()) {
      if (player.containerMenu instanceof Panel panel) panel.closed = true;
      else if (player.containerMenu instanceof TextInput text) text.closed = true;
      else continue;
      player.closeContainer();
    }
  }

  void clearTransientState() {
    epoch++;
    opening.clear();
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
    boolean enabled = mod.settings().bool(path, fallback);
    String master =
        path.startsWith("tree-cutter.") && !path.equals("tree-cutter.enabled")
            ? "tree-cutter.enabled"
            : path.startsWith("better-sugar-cane.") && !path.equals("better-sugar-cane.enabled")
                ? "better-sugar-cane.enabled"
                : path.startsWith(InventoryRules.ROOT + ".")
                        && !path.equals(InventoryRules.ROOT + ".enabled")
                    ? InventoryRules.ROOT + ".enabled"
                    : null;
    boolean available =
        master == null || mod.settings().bool(master, master.startsWith("tree-cutter"));
    return entry(
        icon,
        (!enabled ? "§c" : available ? "§a" : "§e")
            + title
            + ": "
            + (!enabled ? "OFF" : available ? "ON" : "PAUSED"),
        player -> {
          writeSetting(path, !mod.settings().bool(path, fallback));
          mod.save();
          open(player, page.get());
        },
        enabled && !available
            ? "§eEnable the main switch first."
            : "§7Click to toggle.");
  }

  private Entry number(
      Item icon, String title, String path, int fallback, int min, int max, Supplier<Page> page) {
    return entry(
        icon,
        title + ": §e" + mod.settings().integer(path, fallback, min, max),
        player ->
            input(
                player,
                title,
                String.valueOf(mod.settings().integer(path, fallback, min, max)),
                page,
                text -> {
                  Integer value = GameplayMath.integer(text, min, max);
                  if (value == null) return "Enter a whole number from " + min + " to " + max;
                  writeSetting(path, value);
                  mod.save();
                  return null;
                }),
        "§7Range: " + min + " to " + max);
  }

  private Entry percent(
      Item icon, String title, String path, double fallback, Supplier<Page> page) {
    return entry(
        icon,
        title + ": §e" + mod.settings().decimal(path, fallback, 0, 100) + "%",
        player ->
            input(
                player,
                title,
                String.valueOf(mod.settings().decimal(path, fallback, 0, 100)),
                page,
                text -> {
                  Double value = GameplayMath.percentage(text);
                  if (value == null) return "Enter a percentage from 0 to 100";
                  mod.settings().set(path, value);
                  mod.save();
                  return null;
                }),
        "§7Range: 0–100%");
  }

  /** Returning a mob setting to its inherited value must restore future global inheritance. */
  private void writeSetting(String path, Object value) {
    if (path.startsWith("mobs.")) {
      String[] parts = path.split("\\.");
      boolean animal =
          parts.length == 3
              && Set.of("COW", "MOOSHROOM", "CHICKEN", "PIG", "SHEEP", "RABBIT", "COD", "SALMON")
                  .contains(parts[1]);
      if (parts.length == 3
          && parts[2].equals("cooked-food")
          && value instanceof Boolean enabled
          && enabled == (animal && mod.settings().bool("animal-drops.cook", true))) value = null;
      if (parts.length == 3
          && parts[2].equals("xp-bonus")
          && value instanceof Number number
          && number.intValue()
              == (animal ? mod.settings().integer("animal-drops.xp-bonus", 0, 0, 50) : 0))
        value = null;
    }
    mod.settings().set(path, value);
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
                  mod.settings().set(path, null);
                  mod.save();
                }),
        "§7Click to review.");
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
                      if (mod.reload()) open(player, mainPage());
                      else
                        player.sendSystemMessage(
                            Component.literal("§cCCR • Invalid config.yml. Previous settings kept."));
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
                          var key =
                              net.minecraft.resources.ResourceLocation.tryParse(
                                  text.trim().toLowerCase(java.util.Locale.ROOT));
                          if (key == null
                              || !BuiltInRegistries.ENTITY_TYPE.containsKey(key)
                              || BuiltInRegistries.ENTITY_TYPE.get(key)
                                  == net.minecraft.world.entity.EntityType.PLAYER
                              || !(BuiltInRegistries.ENTITY_TYPE
                                      .get(key)
                                      .create(mod.server().overworld())
                                  instanceof LivingEntity)) return "Unknown mob";
                          destination[0] = mob(Settings.materialKey(key.toString()));
                          return null;
                        });
                  }));
          List<net.minecraft.world.entity.EntityType<?>> types = new ArrayList<>();
          BuiltInRegistries.ENTITY_TYPE.forEach(types::add);
          types.sort(
              Comparator.comparingInt(
                      (net.minecraft.world.entity.EntityType<?> type) ->
                          mobScore(
                                      Settings.materialKey(
                                          BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()))
                                  > 0
                              ? 0
                              : 1)
                  .thenComparing(
                      Comparator.comparingInt(
                              (net.minecraft.world.entity.EntityType<?> type) ->
                                  mobScore(
                                      Settings.materialKey(
                                          BuiltInRegistries.ENTITY_TYPE.getKey(type).toString())))
                          .reversed())
                  .thenComparing(
                      Comparator.comparingLong(
                              (net.minecraft.world.entity.EntityType<?> type) ->
                                  mod.settings()
                                              .get(
                                                  "mobs."
                                                      + Settings.materialKey(
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
            if (type == net.minecraft.world.entity.EntityType.PLAYER) continue;
            try {
              if (!(type.create(mod.server().overworld()) instanceof LivingEntity)) continue;
            } catch (RuntimeException ignored) {
              continue;
            }
            String id = Settings.materialKey(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
            Item icon =
                InventoryRules.item(
                    BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath() + "_spawn_egg");
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
            (mod.settings().entries(root + ".drops").stream().filter(LootRules::validRule).count()
                + mod.settings().entries(root + ".vanilla-overrides").stream()
                    .filter(LootRules::validRule)
                    .count());
    boolean cook = animal && mod.settings().bool("animal-drops.cook", true);
    int xp = animal ? mod.settings().integer("animal-drops.xp-bonus", 0, 0, 50) : 0;
    if (mod.settings().contains(root + ".cooked-food")
        && mod.settings().bool(root + ".cooked-food", cook) != cook) score++;
    if (mod.settings().contains(root + ".xp-bonus")
        && mod.settings().integer(root + ".xp-bonus", xp, 0, 1_000_000) != xp) score++;
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
                        && mod.settings().bool("animal-drops.cook", true),
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
                        ? mod.settings().integer("animal-drops.xp-bonus", 0, 0, 50)
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
                reset("Reset Mob", path, here),
                entry(
                    Items.CLOCK,
                    "§eReset Drops",
                    player ->
                        confirm(
                            player,
                            "Reset vanilla and extra drops?",
                            here,
                            () -> {
                              mod.settings().set(path + ".vanilla-overrides", null);
                              mod.settings().set(path + ".drops", null);
                              mod.save();
                            }),
                    "§7Cooking and bonus XP are unchanged.")));
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
                    player -> {
                      Page[] destination = {here.get()};
                      input(
                          player,
                          "Target Ore",
                          "deepslate_" + id,
                          () -> destination[0],
                          text -> {
                            String target =
                                text.trim()
                                    .replace("minecraft:", "")
                                    .toLowerCase(java.util.Locale.ROOT);
                            if (!LootRules.ORES.containsKey(target)) return "Unknown ore";
                            if (target.equals(id)) return "Choose a different ore";
                            destination[0] =
                                confirmation(
                                    "Overwrite settings for " + pretty(target) + " ?",
                                    here,
                                    () -> {
                                      mod.settings()
                                          .set("ore-drops." + target, mod.settings().section(path));
                                      mod.save();
                                    });
                            return null;
                          });
                    },
                    "§7Copy drops and bonus XP."),
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
                  Items.LIME_DYE,
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
                            List<Map<String, Object>> values = mod.settings().entries(path);
                            Map<String, Object> rule = new LinkedHashMap<>();
                            rule.put("item", InventoryRules.id(item));
                            if (source) rule.put("source", InventoryRules.id(item));
                            rule.put("minimum", 1);
                            rule.put("maximum", 1);
                            rule.put("chance", 100.0);
                            values.add(rule);
                            mod.settings().set(path, values);
                            mod.save();
                            return null;
                          })));
          List<Map<String, Object>> rules = mod.settings().entries(path);
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
          List<Map<String, Object>> values = mod.settings().entries(path);
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
                            var range = GameplayMath.range(text, 0, 64);
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
                            Double chance = GameplayMath.percentage(text);
                            if (chance == null) return "Enter a percentage from 0 to 100";
                            editRule(path, index, map -> map.put("chance", chance));
                            return null;
                          })));
          list.add(
              entry(
                  Items.TNT,
                  path.endsWith("vanilla-overrides")
                      ? "§eRestore Vanilla Drop"
                      : "§cDelete Extra Drop",
                  player ->
                      confirm(
                          player,
                          path.endsWith("vanilla-overrides")
                              ? "Restore this vanilla drop?"
                              : "Delete this extra drop?",
                          parent,
                          () -> {
                            List<Map<String, Object>> current = mod.settings().entries(path);
                            if (index < current.size()) current.remove(index);
                            mod.settings().set(path, current);
                            mod.save();
                          })));
          if (path.endsWith("vanilla-overrides"))
            list.add(
                entry(
                    Items.BARRIER,
                    "§cDisable Vanilla Drop",
                    player -> {
                      editRule(path, index, map -> map.put("chance", 0.0));
                      open(player, here.get());
                    },
                    "§7Prevents this vanilla drop.",
                    "§7Vanilla drops remain unchanged."));
          return list;
        });
  }

  private void editRule(String path, int index, Consumer<Map<String, Object>> editor) {
    List<Map<String, Object>> rules = mod.settings().entries(path);
    if (index < rules.size()) {
      editor.accept(rules.get(index));
      mod.settings().set(path, rules);
      mod.save();
    }
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
          for (Map<String, Object> vanilla : mod.catalog().mob(id, looting)) {
            Item item = InventoryRules.item(String.valueOf(vanilla.get("item")));
            String source = String.valueOf(vanilla.get("item"));
            Map<String, Object> configured =
                mod.settings().entries(path).stream()
                    .filter(
                        rule ->
                            item
                                == InventoryRules.item(
                                    String.valueOf(rule.getOrDefault("source", rule.get("item")))))
                    .findFirst()
                    .orElse(null);
            list.add(
                new Entry(
                    item,
                    (configured != null && Settings.number(configured, "chance", 100.0) <= 0
                            ? "§c[OFF] "
                            : "§f")
                        + pretty(source),
                    List.of(
                        configured == null
                            ? "§7Vanilla"
                            : "§eOverride: "
                                + configured.get("minimum")
                                + "–"
                                + configured.get("maximum")
                                + " to "
                                + configured.get("chance")
                                + "%",
                        "§7Vanilla amount: "
                            + vanilla.get("minimum")
                            + "–"
                            + vanilla.get("maximum"),
                        "§7Estimated chance: "
                            + String.format(java.util.Locale.ROOT, "%.1f", vanilla.get("chance"))
                            + "%",
                        "§eClick to edit. Right-click to disable or restore."),
                    player -> {
                      List<Map<String, Object>> values = mod.settings().entries(path);
                      int index = -1;
                      for (int i = 0; i < values.size(); i++)
                        if (item
                            == InventoryRules.item(
                                String.valueOf(
                                    values
                                        .get(i)
                                        .getOrDefault("source", values.get(i).get("item"))))) {
                          index = i;
                          break;
                        }
                      if (index < 0) {
                        Map<String, Object> baseline =
                            mod.catalog().mob(id, false).stream()
                                .filter(entry -> source.equals(entry.get("item")))
                                .findFirst()
                                .orElse(vanilla);
                        Map<String, Object> rule = new LinkedHashMap<>(baseline);
                        rule.put("source", source);
                        values.add(rule);
                        index = values.size() - 1;
                        mod.settings().set(path, values);
                        mod.save();
                      }
                      open(player, drop(path, index, true, here));
                    },
                    player -> {
                      toggleVanilla(path, vanilla, true);
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
                    List<Map<String, Object>> values = mod.settings().entries(path);
                    if (values.isEmpty()) {
                      mod.settings().set(path, List.of(new LinkedHashMap<>(vanilla)));
                      mod.save();
                    }
                    open(player, drop(path, 0, false, here));
                  },
                  "§7Without Fortune: " + vanilla.get("minimum") + "–" + vanilla.get("maximum"),
                  "§7Fortune III: " + fortune.get("minimum") + "–" + fortune.get("maximum"),
                  "§7Silk Touch keeps the ore block."));
          list.add(reset("Restore Vanilla Drop", path, here));
          list.add(
              entry(
                  Items.BARRIER,
                  "§cDisable Vanilla Drop",
                  player -> {
                    mod.settings().set(path, List.of(new LinkedHashMap<>(vanilla)));
                    editRule(path, 0, map -> map.put("chance", 0.0));
                    open(player, here.get());
                  },
                  "§7Extra drops and bonus XP are unchanged."));
          return list;
        });
  }

  private void toggleVanilla(String path, Map<String, Object> vanilla, boolean source) {
    List<Map<String, Object>> values = mod.settings().entries(path);
    String item = String.valueOf(vanilla.get("item"));
    Item original = InventoryRules.item(item);
    boolean disabled =
        values.stream()
            .anyMatch(
                rule ->
                    original
                            == InventoryRules.item(
                                String.valueOf(rule.getOrDefault("source", rule.get("item"))))
                        && Settings.number(rule, "chance", 100.0) <= 0);
    values.removeIf(
        rule ->
            original
                == InventoryRules.item(
                    String.valueOf(rule.getOrDefault("source", rule.get("item")))));
    if (!disabled) {
      Map<String, Object> rule = new LinkedHashMap<>(vanilla);
      if (source) rule.put("source", item);
      rule.put("chance", 0.0);
      values.add(rule);
    }
    mod.settings().set(path, values);
    mod.save();
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
                              "tree-cutter.axes." + Settings.materialKey(InventoryRules.id(item)),
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
                        + mod.settings().integer(path + ".minimum", 1, 1, 64)
                        + "–"
                        + mod.settings().integer(path + ".maximum", 3, 1, 64),
                    player ->
                        input(
                            player,
                            "Amount: 1-3",
                            "1-3",
                            here,
                            text -> {
                              var range = GameplayMath.range(text, 1, 64);
                              if (range == null) return "Enter an amount from 1 to 64";
                              mod.settings().set(path + ".minimum", range.minimum());
                              mod.settings().set(path + ".maximum", range.maximum());
                              mod.save();
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
                      mod.settings()
                          .set(
                              "clean-stacks.players." + player.getUUID(),
                              !mod.inventory().cleanEnabled(player));
                      mod.save();
                      mod.inventory().tick(player);
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
                            + Settings.materialKey(InventoryRules.id(item));
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
                                "§7Output: " + mod.settings().text(path, defaultValue),
                                "§eClick to choose. Right-click to reset."),
                            player ->
                                input(
                                    player,
                                    "Output Item",
                                    mod.settings().text(path, defaultValue),
                                    here,
                                    text -> {
                                      Item target = InventoryRules.item(text);
                                      if (target == null || target == Items.AIR)
                                        return "Unknown item";
                                      mod.inventory().setCleanTarget(item, target);
                                      return null;
                                    }),
                            player -> {
                              mod.settings().set(path, null);
                              mod.save();
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
                  BuiltInRegistries.ITEM.stream()
                      .filter(
                          item ->
                              group.equals(InventoryRules.category(item))
                                  && (kind.equals("all")
                                      || (kind.equals("planks")
                                          ? InventoryRules.id(item).endsWith("_planks")
                                          : kind.equals("stripped")
                                              ? InventoryRules.id(item).contains(":stripped_")
                                              : !InventoryRules.id(item).contains(":stripped_")
                                                  && !InventoryRules.id(item).endsWith("_planks"))))
                      .forEach(
                          item ->
                              mod.settings()
                                  .set(
                                      "clean-stacks."
                                          + group
                                          + "."
                                          + Settings.materialKey(InventoryRules.id(item)),
                                      InventoryRules.id(target)));
                  String targetGroup = InventoryRules.category(target);
                  if (targetGroup != null)
                    mod.settings()
                        .set(
                            "clean-stacks."
                                + targetGroup
                                + "."
                                + Settings.materialKey(InventoryRules.id(target)),
                            InventoryRules.id(target));
                  mod.save();
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
                              mod.settings()
                                  .set(
                                      "automatic-enchantments.refresh-version",
                                      mod.settings()
                                              .integer(
                                                  "automatic-enchantments.refresh-version",
                                                  0,
                                                  0,
                                                  Integer.MAX_VALUE - 1)
                                          + 1);
                              mod.save();
                              mod.server()
                                  .getPlayerList()
                                  .getPlayers()
                                  .forEach(mod.inventory()::tick);
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
          list.add(
              entry(
                  Items.COMPASS,
                  "§bSearch Items",
                  player -> {
                    Page[] destination = {autoGroup(group)};
                    input(
                        player,
                        "Item ID",
                        group.equals("armor")
                            ? "minecraft:diamond_chestplate"
                            : "minecraft:diamond_sword",
                        () -> destination[0],
                        text -> {
                          Item item = InventoryRules.item(text);
                          if (item == null
                              || !(group.equals("armor")
                                  ? InventoryRules.armor(item)
                                  : InventoryRules.tool(item)))
                            return "Item does not match this category";
                          String path =
                              "automatic-enchantments."
                                  + group
                                  + "."
                                  + Settings.materialKey(InventoryRules.id(item));
                          if (!mod.settings().contains(path))
                            mod.settings().set(path, new LinkedHashMap<>());
                          mod.save();
                          destination[0] = autoItem(group, item);
                          return null;
                        });
                  }));
          BuiltInRegistries.ITEM.stream()
              .filter(group.equals("armor") ? InventoryRules::armor : InventoryRules::tool)
              .sorted(Comparator.comparing(InventoryRules::id))
              .forEach(
                  item -> {
                    String path =
                        "automatic-enchantments."
                            + group
                            + "."
                            + Settings.materialKey(InventoryRules.id(item));
                    list.add(
                        new Entry(
                            item,
                            "§f" + pretty(InventoryRules.id(item)),
                            List.of(
                                "§7" + mod.settings().section(path).size() + " enchantment(s)",
                                "§eClick to edit. Right-click to delete."),
                            player -> open(player, autoItem(group, item)),
                            player ->
                                confirm(
                                    player,
                                    "Reset these settings?",
                                    () -> autoGroup(group),
                                    () -> {
                                      mod.settings().set(path, null);
                                      mod.save();
                                    })));
                  });
          return list;
        });
  }

  private Page autoItem(String group, Item item) {
    String path =
        "automatic-enchantments." + group + "." + Settings.materialKey(InventoryRules.id(item));
    Supplier<Page> here = () -> autoItem(group, item);
    return new Page(
        "CCR • Auto " + pretty(InventoryRules.id(item)),
        () -> autoGroup(group),
        () -> {
          List<Entry> list = new ArrayList<>();
          list.add(
              folder(
                  Items.ENCHANTED_BOOK,
                  "§aAdd Enchantment",
                  () ->
                      enchantmentPicker(
                          item,
                          "",
                          here,
                          (player, enchantment) ->
                              input(
                                  player,
                                  "Level (1–255)",
                                  String.valueOf(Math.max(1, automaticLevel(path, enchantment))),
                                  here,
                                  text -> {
                                    Integer level = GameplayMath.integer(text, 1, 255);
                                    if (level == null) return "Enter a level from 1 to 255";
                                    setAutomaticLevel(path, enchantment, level);
                                    return null;
                                  })),
                  "§7Browse or search enchantments."));
          mod.settings()
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
                                        Integer level = GameplayMath.integer(text, 0, 255);
                                        if (level == null) return "Enter a level from 0 to 255";
                                        var enchantment = mod.inventory().enchantment(id);
                                        if (enchantment != null)
                                          setAutomaticLevel(path, enchantment, level);
                                        else {
                                          mod.settings()
                                              .set(path + "." + id, level == 0 ? null : level);
                                          mod.save();
                                        }
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
                            Map<String, Object> values = mod.settings().section(path);
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
                                              + Settings.materialKey(InventoryRules.id(candidate));
                                      for (var value : values.entrySet()) {
                                        var enchantment =
                                            mod.inventory().enchantment(value.getKey());
                                        if (enchantment != null
                                            && value.getValue() instanceof Number level
                                            && level.intValue() > 0
                                            && level.intValue() <= 255
                                            && enchantment
                                                .value()
                                                .canEnchant(new ItemStack(candidate))
                                            && automaticLevel(target, enchantment) == 0)
                                          mod.settings()
                                              .set(
                                                  target + "." + enchantment.key().location(),
                                                  level.intValue());
                                      }
                                    });
                            mod.save();
                          })));
          list.add(reset("Delete Auto Enchantment Rule", path, () -> autoGroup(group)));
          return list;
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
                        + (mod.settings().bool(path + ".exists", false)
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
                              mod.settings().set(path + ".exists", false);
                              mod.settings().set(path + ".id", null);
                              mod.save();
                            }),
                    "§cMay allow a second mace.")));
  }

  private List<Map<String, Object>> identifiedRules(String path) {
    List<Map<String, Object>> rules = mod.settings().entries(path);
    Set<String> seen = new LinkedHashSet<>();
    boolean changed = false;
    for (var rule : rules) {
      String id = rule.get("id") instanceof String value ? value : "";
      if (id.isBlank() || !seen.add(id)) {
        id = UUID.randomUUID().toString();
        seen.add(id);
        rule.put("id", id);
        changed = true;
      }
    }
    if (changed) {
      mod.settings().set(path, rules);
      mod.save();
    }
    return rules;
  }

  private static int indexOfRule(List<Map<String, Object>> rules, String id) {
    for (int index = 0; index < rules.size(); index++)
      if (id.equals(rules.get(index).get("id"))) return index;
    return -1;
  }

  private Map<String, Object> findRule(String path, String id) {
    var rules = identifiedRules(path);
    int index = indexOfRule(rules, id);
    return index < 0 ? null : rules.get(index);
  }

  private Map<String, Object> requireRule(String path, String id) {
    var rule = findRule(path, id);
    if (rule == null) throw new RemovedRule(path.endsWith("enchantment-caps"));
    return rule;
  }

  private void editRule(String path, String id, Consumer<Map<String, Object>> edit) {
    var rules = identifiedRules(path);
    int index = indexOfRule(rules, id);
    if (index < 0) throw new RemovedRule(path.endsWith("enchantment-caps"));
    edit.accept(rules.get(index));
    mod.settings().set(path, rules);
    mod.save();
  }

  private void deleteRule(String path, String id) {
    var rules = identifiedRules(path);
    int index = indexOfRule(rules, id);
    if (index < 0) throw new RemovedRule(path.endsWith("enchantment-caps"));
    rules.remove(index);
    mod.settings().set(path, rules);
    mod.save();
  }

  private static final class RemovedRule extends RuntimeException {
    final boolean cap;

    RemovedRule(boolean cap) {
      super("This rule was deleted by another admin. Changes cancelled.");
      this.cap = cap;
    }
  }

  private void removedRule(ServerPlayer player, RemovedRule error) {
    player.sendSystemMessage(Component.literal("§cCCR • " + error.getMessage()));
    open(player, ruleList(error.cap));
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
                  Items.LIME_DYE,
                  "§aAdd Rule",
                  player -> {
                    List<Map<String, Object>> rules = identifiedRules(path);
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
                    mod.settings().set(path, rules);
                    mod.save();
                    open(player, rule(cap, rules.size() - 1));
                  },
                  "§7New rules start OFF."));
          List<Map<String, Object>> rules = identifiedRules(path);
          for (int index = 0; index < rules.size(); index++) {
            Map<String, Object> rule = rules.get(index);
            String id = String.valueOf(rule.get("id"));
            list.add(
                new Entry(
                    cap ? Items.ENCHANTED_BOOK : Items.DIAMOND_CHESTPLATE,
                    (!Settings.enabled(rule)
                            ? "§7"
                            : mod.settings().bool(InventoryRules.ROOT + ".enabled", false)
                                ? "§a"
                                : "§e")
                        + InventoryRules.ruleTarget(rule, cap ? "item" : "material")
                        + " ≤ "
                        + rule.get("maximum")
                        + (Settings.enabled(rule)
                                && !mod.settings().bool(InventoryRules.ROOT + ".enabled", false)
                            ? " [PAUSED]"
                            : ""),
                    List.of(
                        cap ? "§7" + rule.get("enchantment") : "§7Maximum equipped armor pieces.",
                        "§eClick to edit. Right-click to toggle.",
                        "§7Shift-click to review deletion."),
                    player -> open(player, ruleById(cap, id)),
                    player -> {
                      editRule(path, id, map -> map.put("enabled", !Settings.enabled(map)));
                      open(player, here.get());
                    },
                    player ->
                        confirm(player, "Delete this rule?", here, () -> deleteRule(path, id))));
          }
          return list;
        });
  }

  private Page rule(boolean cap, int index) {
    String path = InventoryRules.ROOT + (cap ? ".enchantment-caps" : ".armor-rules");
    List<Map<String, Object>> rules = identifiedRules(path);
    if (index < 0 || index >= rules.size()) return ruleList(cap);
    return ruleById(cap, String.valueOf(rules.get(index).get("id")));
  }

  private Page ruleById(boolean cap, String id) {
    String path = InventoryRules.ROOT + (cap ? ".enchantment-caps" : ".armor-rules");
    Supplier<Page> here = () -> ruleById(cap, id);
    int index = indexOfRule(identifiedRules(path), id);
    return new Page(
        index < 0
            ? "CCR • Rule Deleted"
            : "CCR • " + (cap ? "Cap" : "Armor") + " #" + (index + 1),
        () -> ruleList(cap),
        () -> {
          Map<String, Object> rule = findRule(path, id);
          if (rule == null)
            return List.of(
                entry(
                    Items.BARRIER,
                    "§cRule Deleted",
                    player -> open(player, ruleList(cap)),
                    "§7This rule no longer exists."));
          List<Entry> list = new ArrayList<>();
          list.add(
              entry(
                  Items.LEVER,
                  (!Settings.enabled(rule)
                          ? "§c"
                          : mod.settings().bool(InventoryRules.ROOT + ".enabled", false)
                              ? "§a"
                              : "§e")
                      + "Rule "
                      + (!Settings.enabled(rule)
                          ? "inactive"
                          : mod.settings().bool(InventoryRules.ROOT + ".enabled", false)
                              ? "active"
                              : "paused (Stuff Rules is OFF)"),
                  player -> {
                    editRule(path, id, map -> map.put("enabled", !Settings.enabled(map)));
                    open(player, here.get());
                  }));
          list.add(
              folder(
                  Items.ITEM_FRAME,
                  "§bTarget: " + InventoryRules.ruleTarget(rule, cap ? "item" : "material"),
                  () ->
                      targetPicker(
                          cap,
                          InventoryRules.ruleTarget(rule, cap ? "item" : "material"),
                          here,
                          target -> editRule(path, id, map -> map.put("target", target))),
                  cap
                      ? "§7ALL / TOOLS / SWORDS / ARMOR / specific items"
                      : "§7ARMOR / DIAMOND / IRON / specific pieces"));
          list.add(
              entry(
                  Items.HOPPER,
                  "§eMaximum: " + rule.get("maximum"),
                  player ->
                      input(
                          player,
                          "Maximum",
                          String.valueOf(rule.get("maximum")),
                          here,
                          text -> {
                            Integer value =
                                GameplayMath.integer(
                                    text, cap ? 1 : 0, cap ? Integer.MAX_VALUE : 4);
                            if (value == null)
                              return cap ? "Enter a positive whole number" : "Enter a maximum from 0 to 4";
                            editRule(path, id, map -> map.put("maximum", value));
                            return null;
                          })));
          if (cap) {
            list.add(
                folder(
                    Items.ENCHANTED_BOOK,
                    "§dEnchantment: " + rule.get("enchantment"),
                    () ->
                        enchantmentPicker(
                            null,
                            "",
                            here,
                            (player, enchantment) -> {
                              editRule(
                                  path,
                                  id,
                                  map ->
                                      map.put(
                                          "enchantment", enchantment.key().location().toString()));
                              open(player, here.get());
                            }),
                    "§7Browse or search by name."));
            list.add(
                entry(
                    Items.EXPERIENCE_BOTTLE,
                    "§aRefund XP: " + Boolean.TRUE.equals(rule.get("refund-xp")),
                    player -> {
                      editRule(
                          path,
                          id,
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
                        input(
                            player,
                            "XP per Removed Level",
                            String.valueOf(rule.getOrDefault("xp-per-removed-level", 0)),
                            here,
                            text -> {
                              Integer value = GameplayMath.integer(text, 0, Integer.MAX_VALUE);
                              if (value == null) return "Enter a non-negative whole number";
                              editRule(path, id, map -> map.put("xp-per-removed-level", value));
                              return null;
                            })));
          }
          list.add(
              folder(
                  Items.WRITABLE_BOOK,
                  "§bCopy to Another Target",
                  () ->
                      targetPicker(
                          cap,
                          InventoryRules.ruleTarget(rule, cap ? "item" : "material"),
                          () -> ruleList(cap),
                          target -> {
                            List<Map<String, Object>> current = identifiedRules(path);
                            Map<String, Object> copy = new LinkedHashMap<>(requireRule(path, id));
                            copy.put("id", UUID.randomUUID().toString());
                            copy.put("enabled", false);
                            copy.put("target", target);
                            current.add(copy);
                            mod.settings().set(path, current);
                            mod.save();
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
                          () -> deleteRule(path, id))));
          return list;
        });
  }

  private int automaticLevel(String path, Holder.Reference<Enchantment> enchantment) {
    int result = 0;
    for (var entry : mod.settings().section(path).entrySet())
      if (enchantment.equals(mod.inventory().enchantment(entry.getKey()))
          && entry.getValue() instanceof Number value
          && value.intValue() > 0
          && value.intValue() <= 255) result = value.intValue();
    return result;
  }

  private void setAutomaticLevel(
      String path, Holder.Reference<Enchantment> enchantment, int level) {
    Map<String, Object> values = mod.settings().section(path);
    values.keySet().removeIf(key -> enchantment.equals(mod.inventory().enchantment(key)));
    if (level > 0) values.put(enchantment.key().location().toString(), level);
    mod.settings().set(path, values);
    mod.save();
  }

  private Page enchantmentPicker(
      Item compatible,
      String query,
      Supplier<Page> parent,
      BiConsumer<ServerPlayer, Holder.Reference<Enchantment>> choose) {
    Supplier<Page> here = () -> enchantmentPicker(compatible, query, parent, choose);
    return new Page(
        "CCR • Enchantments",
        parent,
        () -> {
          List<Entry> list = new ArrayList<>();
          list.add(
              entry(
                  Items.COMPASS,
                  "§bSearch Enchantments",
                  player -> {
                    Page[] destination = {here.get()};
                    input(
                        player,
                        "Search (empty = all)",
                        query,
                        () -> destination[0],
                        text -> {
                          destination[0] =
                              enchantmentPicker(
                                  compatible,
                                  text.trim().toLowerCase(java.util.Locale.ROOT),
                                  parent,
                                  choose);
                          return null;
                        });
                  },
                  "§7Search by name or item ID."));
          mod.inventory()
              .enchantments()
              .holders()
              .filter(
                  holder ->
                      compatible == null || holder.value().canEnchant(new ItemStack(compatible)))
              .filter(holder -> holder.key().location().toString().contains(query))
              .sorted(Comparator.comparing(holder -> holder.key().location().toString()))
              .forEach(
                  holder ->
                      list.add(
                          new Entry(
                              Items.ENCHANTED_BOOK,
                              "§d" + pretty(holder.key().location().toString()),
                              compatible == null
                                  ? List.of(
                                      "§7" + holder.key().location(), "§eClick to choose.")
                                  : List.of(
                                      "§7" + holder.key().location(),
                                      "§eClick to set level. Right-click to remove.",
                                      "§7Level: "
                                          + automaticLevel(
                                              "automatic-enchantments."
                                                  + (InventoryRules.armor(compatible)
                                                      ? "armor"
                                                      : "tools")
                                                  + "."
                                                  + Settings.materialKey(
                                                      InventoryRules.id(compatible)),
                                              holder)),
                              player -> choose.accept(player, holder),
                              compatible == null
                                  ? null
                                  : player -> {
                                    setAutomaticLevel(
                                        "automatic-enchantments."
                                            + (InventoryRules.armor(compatible) ? "armor" : "tools")
                                            + "."
                                            + Settings.materialKey(InventoryRules.id(compatible)),
                                        holder,
                                        0);
                                    open(player, here.get());
                                  })));
          return list;
        });
  }

  private Page targetPicker(
      boolean cap, String current, Supplier<Page> parent, Consumer<String> choose) {
    Supplier<Page> here = () -> targetPicker(cap, current, parent, choose);
    return new Page(
        "CCR • Rule Target",
        parent,
        () -> {
          List<Entry> list = new ArrayList<>();
          List<String> presets =
              cap
                  ? List.of("ALL", "TOOLS", "SWORDS", "ARMOR")
                  : List.of(
                      "ARMOR",
                      "LEATHER",
                      "CHAINMAIL",
                      "IRON",
                      "GOLDEN",
                      "DIAMOND",
                      "NETHERITE",
                      "TURTLE");
          for (String preset : presets)
            list.add(
                entry(
                    cap ? Items.COMPASS : Items.DIAMOND_CHESTPLATE,
                    "§bGroup: " + preset,
                    player -> {
                      choose.accept(preset);
                      open(player, parent.get());
                    },
                    "§7Selected: " + current));
          list.add(
              folder(
                  Items.ITEM_FRAME,
                  "§eSelect Specific Items",
                  () -> {
                    Set<Item> selected = new LinkedHashSet<>();
                    if (!presets.contains(current.trim().toUpperCase(java.util.Locale.ROOT)))
                      for (String part : current.split(",")) {
                        Item item = InventoryRules.item(part);
                        if (item != null
                            && item != Items.AIR
                            && (cap || InventoryRules.armor(item))) selected.add(item);
                      }
                    return exactTargets(cap, selected, "", here, parent, choose);
                  },
                  "§7Select items, then click Apply."));
          list.add(
              entry(
                  Items.NAME_TAG,
                  "§fEnter Item IDs",
                  player ->
                      input(
                          player,
                          "Target or Item List",
                          current,
                          parent,
                          text -> {
                            String target =
                                text.trim()
                                    .toUpperCase(java.util.Locale.ROOT)
                                    .replace("MINECRAFT:", "");
                            if (!validTarget(target, cap))
                              return "Unknown target or invalid item list";
                            choose.accept(target);
                            return null;
                          }),
                  "§7Ex. diamond_helmet,diamond_boots"));
          return list;
        });
  }

  private Page exactTargets(
      boolean cap,
      Set<Item> selected,
      String query,
      Supplier<Page> picker,
      Supplier<Page> destination,
      Consumer<String> choose) {
    Supplier<Page> here = () -> exactTargets(cap, selected, query, picker, destination, choose);
    return new Page(
        "CCR • Specific Items (" + selected.size() + ")",
        picker,
        () -> {
          List<Entry> list = new ArrayList<>();
          list.add(
              entry(
                  Items.LIME_CONCRETE,
                  "§aApply " + selected.size() + " item(s)",
                  player -> {
                    if (selected.isEmpty()) {
                      player.sendSystemMessage(
                          Component.literal("§cSelect at least one item."));
                      return;
                    }
                    choose.accept(
                        selected.stream()
                            .map(InventoryRules::id)
                            .sorted()
                            .collect(java.util.stream.Collectors.joining(",")));
                    open(player, destination.get());
                  },
                  "§7Click Apply to save your selection."));
          list.add(
              entry(
                  Items.COMPASS,
                  "§bSearch Items",
                  player -> {
                    Page[] page = {here.get()};
                    input(
                        player,
                        "Search (empty = all)",
                        query,
                        () -> page[0],
                        text -> {
                          page[0] =
                              exactTargets(
                                  cap,
                                  selected,
                                  text.trim().toLowerCase(java.util.Locale.ROOT),
                                  picker,
                                  destination,
                                  choose);
                          return null;
                        });
                  }));
          BuiltInRegistries.ITEM.stream()
              .filter(item -> item != Items.AIR && (cap || InventoryRules.armor(item)))
              .filter(item -> InventoryRules.id(item).contains(query))
              .sorted(Comparator.comparing(InventoryRules::id))
              .forEach(
                  item ->
                      list.add(
                          entry(
                              item,
                              (selected.contains(item) ? "§a✓ " : "§f")
                                  + pretty(InventoryRules.id(item)),
                              player -> {
                                if (!selected.remove(item)) selected.add(item);
                                open(player, here.get());
                              },
                              "§7" + InventoryRules.id(item),
                              "§eClick to select or remove.")));
          return list;
        });
  }

  private static boolean validTarget(String value, boolean cap) {
    if ((cap
            ? List.of("ALL", "TOOLS", "SWORDS", "ARMOR")
            : List.of(
                "ARMOR",
                "LEATHER",
                "CHAINMAIL",
                "IRON",
                "GOLDEN",
                "DIAMOND",
                "NETHERITE",
                "TURTLE"))
        .contains(value)) return true;
    for (String part : value.split(",", -1)) {
      Item item = InventoryRules.item(part);
      if (item == null || item == Items.AIR || (!cap && !InventoryRules.armor(item))) return false;
    }
    return true;
  }

  private void confirm(ServerPlayer player, String title, Supplier<Page> parent, Runnable action) {
    open(player, confirmation(title, parent, action));
  }

  private Page confirmation(String title, Supplier<Page> parent, Runnable action) {
    return new Page(
        "CCR • Confirmation",
        parent,
        () ->
            List.of(
                entry(
                    Items.LIME_CONCRETE,
                    "§aConfirm: " + title,
                    p -> {
                      action.run();
                      open(p, parent.get());
                    }),
                entry(Items.RED_CONCRETE, "§cCancel", p -> open(p, parent.get()))));
  }

  public void open(ServerPlayer player, Page page) {
    if (!mod.admin(player)) {
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
    private final Map<Integer, Consumer<ServerPlayer>> shiftActions = new HashMap<>();
    private boolean closed;
    private final int openedEpoch = epoch;

    Panel(int id, Inventory inventory, Page page) {
      super(MenuType.GENERIC_9x6, id, inventory, new SimpleContainer(54), 6);
      for (int slot = 0; slot < 54; slot++)
        getSlot(slot).set(icon(Items.GRAY_STAINED_GLASS_PANE, " ", List.of()));
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
        if (entry.shiftClick() != null) shiftActions.put(slot, entry.shiftClick());
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
          type == ClickType.QUICK_MOVE
              ? shiftActions.get(slot)
              : button == 0 ? actions.get(slot) : button == 1 ? rightActions.get(slot) : null;
      if ((type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) && action != null)
        mod.next(
            () -> {
              if (serverPlayer.containerMenu == this && mod.admin(serverPlayer))
                try {
                  action.accept(serverPlayer);
                } catch (RemovedRule error) {
                  removedRule(serverPlayer, error);
                }
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
      if (!closed
          && !opening.containsKey(player.getUUID())
          && page.parent() != null
          && player instanceof ServerPlayer serverPlayer)
        mod.next(
            () -> {
              if (openedEpoch == epoch
                  && serverPlayer.isAlive()
                  && !serverPlayer.isRemoved()
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
    private final int openedEpoch = epoch;

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
      // The vanilla client predicts a rename cost even when the server cost stays zero.
      // Send only that data field: a full inventory sync would reset the edit box.
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
              Items.LIME_DYE,
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
      mod.next(
          () -> {
            if (serverPlayer.containerMenu != this || submit == null || !mod.admin(serverPlayer))
              return;
            String error;
            try {
              error = submit.apply(value == null ? "" : value);
            } catch (RemovedRule removed) {
              closed = true;
              removedRule(serverPlayer, removed);
              return;
            }
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
      if (!closed
          && !opening.containsKey(player.getUUID())
          && parent != null
          && player instanceof ServerPlayer serverPlayer)
        mod.next(
            () -> {
              if (openedEpoch == epoch
                  && serverPlayer.isAlive()
                  && !serverPlayer.isRemoved()
                  && serverPlayer.connection != null
                  && serverPlayer.containerMenu == serverPlayer.inventoryMenu)
                open(serverPlayer, parent.get());
            });
    }
  }
}
