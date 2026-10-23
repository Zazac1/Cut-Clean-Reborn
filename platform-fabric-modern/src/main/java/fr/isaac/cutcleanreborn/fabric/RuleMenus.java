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
      this(title, parent, entries, 0);
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
        (enabled ? "§a" : "§c") + title + " : " + (enabled ? "OUI" : "NON"),
        player -> {
          writeSetting(path, !mod.config.bool(path, fallback));
          open(player, page.get());
        },
        "§7Cliquer pour changer");
  }

  private Entry number(
      Item icon, String title, String path, int fallback, int min, int max, Supplier<Page> page) {
    return entry(
        icon,
        title + " : §e" + mod.config.integer(path, fallback, min, max),
        player ->
            input(
                player,
                title,
                String.valueOf(mod.config.integer(path, fallback, min, max)),
                page,
                text -> {
                  Integer value = RuleMath.integer(text, min, max);
                  if (value == null) return "Entrez un entier de " + min + " à " + max;
                  writeSetting(path, value);
                  return null;
                }),
        "§7Valeur autorisée : " + min + " à " + max);
  }

  private Entry percent(
      Item icon, String title, String path, double fallback, Supplier<Page> page) {
    return entry(
        icon,
        title + " : §e" + mod.config.decimal(path, fallback, 0, 100) + "%",
        player ->
            input(
                player,
                title,
                String.valueOf(mod.config.decimal(path, fallback, 0, 100)),
                page,
                text -> {
                  Double value = RuleMath.percentage(text);
                  if (value == null) return "Entrez un pourcentage de 0 à 100";
                  mod.config.set(path, value);
                  return null;
                }),
        "§7Pourcentage de 0 à 100");
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
        "§7Confirmation nécessaire");
  }

  private Entry resetDrops(String root, Supplier<Page> page) {
    return entry(
        Items.BUCKET,
        "§cRéinitialiser uniquement le butin",
        player ->
            confirm(
                player,
                "Restaurer le butin vanilla ?",
                page,
                () -> {
                  Map<String, Object> changes = new LinkedHashMap<>();
                  changes.put(root + ".drops", null);
                  changes.put(root + ".vanilla-overrides", null);
                  mod.config.batch(changes);
                }),
        "§7Supprime overrides et extras après confirmation",
        "§7Conserve la cuisson et le bonus XP");
  }

  private Page mainPage() {
    return new Page(
        "CCR • Configuration",
        null,
        () ->
            List.of(
                folder(
                    Items.BEEF,
                    "§6Créatures et butin",
                    this::mobs,
                    "§7Cuisson, XP, butin vanilla et extras"),
                folder(
                    Items.DIAMOND_ORE,
                    "§bMinerais",
                    this::ores,
                    "§7Chaque variante, Fortune, fusion et XP"),
                folder(
                    Items.IRON_AXE,
                    "§aTimber",
                    this::timber,
                    "§7Outils, arbres naturels, temps et récolte"),
                folder(
                    Items.ENCHANTED_BOOK,
                    "§dEnchantements automatiques",
                    this::automatic,
                    "§7Armures, outils, copie et rafraîchissement"),
                folder(
                    Items.OAK_LOG,
                    "§eClean Stacks",
                    this::clean,
                    "§7Conversions et préférence personnelle"),
                folder(
                    Items.SUGAR_CANE,
                    "§aCanne à sucre",
                    this::cane,
                    "§7Casse joueur et perte du support séparées"),
                folder(
                    Items.DIAMOND_CHESTPLATE,
                    "§cStuff Rules",
                    this::stuff,
                    "§7Armures équipées, caps, XP et masse unique"),
                entry(
                    Items.COMPASS,
                    "§fRecharger la configuration",
                    player -> {
                      try {
                        mod.reload();
                        open(player, mainPage());
                      } catch (RuntimeException failure) {
                        player.sendSystemMessage(
                            Component.literal(
                                "§cCCR • Rechargement refusé, configuration précédente conservée : "
                                    + failure.getMessage()));
                      }
                    },
                    "§7Relit le fichier YAML sans effacer les préférences")));
  }

  private Page mobs() {
    return new Page(
        "CCR • Créatures",
        this::mainPage,
        () -> {
          List<Entry> list = new ArrayList<>();
          list.add(folder(Items.COOKED_BEEF, "§6Réglages globaux", this::mobGlobals));
          list.add(
              entry(
                  Items.COMPASS,
                  "§bTrouver une créature",
                  player -> {
                    Page[] destination = {mobs()};
                    input(
                        player,
                        "Identifiant de créature",
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
                            return "Créature vivante inconnue";
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
                    "§7Modifications actives : " + mobScore(id),
                    "§7Cuisson, XP, overrides et extras"));
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
        "CCR • Butin global",
        this::mobs,
        () ->
            List.of(
                toggle(
                    Items.CAMPFIRE,
                    "Cuire la viande animale",
                    "animal-drops.cook",
                    true,
                    this::mobGlobals),
                number(
                    Items.BEEF,
                    "Quantité animale finale ×",
                    "animal-drops.multiplier",
                    1,
                    1,
                    5,
                    this::mobGlobals),
                number(
                    Items.EXPERIENCE_BOTTLE,
                    "XP animale bonus",
                    "animal-drops.xp-bonus",
                    0,
                    0,
                    50,
                    this::mobGlobals),
                number(
                    Items.CHEST,
                    "Portée de collecte (blocs)",
                    "animal-drops.collection-range",
                    0,
                    0,
                    32,
                    this::mobGlobals),
                toggle(
                    Items.IRON_SWORD,
                    "Looting sur overrides",
                    "mob-drops.looting-vanilla",
                    true,
                    this::mobGlobals),
                toggle(
                    Items.DIAMOND_SWORD,
                    "Looting sur extras",
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
                    "Cuisson",
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
                    "XP bonus",
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
                    "§eButin vanilla",
                    () -> vanillaMob(id, false),
                    "§7Table réelle, aperçu Looting et overrides"),
                folder(
                    Items.CHEST,
                    "§aButin supplémentaire",
                    () -> drops(path + ".drops", false, here),
                    "§7Ajouts indépendants de la chance vanilla"),
                resetDrops(path, here),
                reset("Réinitialiser cette créature", path, here)));
  }

  private Page ores() {
    return new Page(
        "CCR • Minerais",
        this::mainPage,
        () -> {
          List<Entry> list = new ArrayList<>();
          list.add(
              toggle(
                  Items.FURNACE, "Fusion automatique", "ore-drops.auto-smelt", false, this::ores));
          list.add(
              toggle(
                  Items.DIAMOND_PICKAXE,
                  "Fortune sur vanilla",
                  "ore-drops.fortune-vanilla",
                  true,
                  this::ores));
          list.add(
              toggle(
                  Items.GOLDEN_PICKAXE,
                  "Fortune sur extras",
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
                              "§7XP, butin et copie vers une variante")));
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
                    Items.EXPERIENCE_BOTTLE, "XP bonus", path + ".xp-bonus", 0, 0, 1_000_000, here),
                folder(Items.BOOK, "§eRemplacement vanilla", () -> vanillaOre(id)),
                folder(
                    Items.CHEST,
                    "§aButin supplémentaire",
                    () -> drops(path + ".drops", false, here)),
                entry(
                    Items.WRITABLE_BOOK,
                    "§bCopier vers un minerai",
                    player ->
                        input(
                            player,
                            "Minerai destinataire",
                            "deepslate_" + id,
                            here,
                            text -> {
                              String target =
                                  text.trim()
                                      .replace("minecraft:", "")
                                      .toLowerCase(java.util.Locale.ROOT);
                              if (!LootRules.ORES.containsKey(target)) return "Minerai inconnu";
                              if (target.equals(id)) return "Choisissez un autre minerai";
                              mod.config.set("ore-drops." + target, mod.config.section(path));
                              return null;
                            }),
                    "§7Copie XP, overrides et extras"),
                resetDrops(path, here),
                reset("Réinitialiser ce minerai", path, here)));
  }

  private Page drops(String path, boolean source, Supplier<Page> parent) {
    Supplier<Page> here = () -> drops(path, source, parent);
    return new Page(
        "CCR • " + (path.endsWith(".drops") ? "Butin supplémentaire" : "Overrides vanilla"),
        parent,
        () -> {
          List<Entry> list = new ArrayList<>();
          list.add(
              entry(
                  InventoryRules.item("minecraft:lime_dye"),
                  "§aAjouter un objet",
                  player ->
                      input(
                          player,
                          "Identifiant de l'objet",
                          "minecraft:diamond",
                          here,
                          text -> {
                            Item item = InventoryRules.item(text);
                            if (item == null || item == Items.AIR) return "Objet Minecraft inconnu";
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
                    "§7Quantité : " + rule.get("minimum") + "–" + rule.get("maximum"),
                    "§7Chance : " + rule.get("chance") + "%"));
          }
          return list;
        });
  }

  private Page drop(String path, int index, boolean source, Supplier<Page> parent) {
    Supplier<Page> here = () -> drop(path, index, source, parent);
    return new Page(
        "CCR • Objet de butin",
        parent,
        () -> {
          List<Map<String, Object>> values = mod.config.entries(path);
          if (index >= values.size()) return List.of();
          Map<String, Object> rule = values.get(index);
          List<Entry> list = new ArrayList<>();
          list.add(
              entry(
                  Items.ITEM_FRAME,
                  "§fObjet : " + rule.get("item"),
                  player ->
                      input(
                          player,
                          "Objet de sortie",
                          String.valueOf(rule.get("item")),
                          here,
                          text -> {
                            Item item = InventoryRules.item(text);
                            if (item == null || item == Items.AIR) return "Objet inconnu";
                            editRule(path, index, map -> map.put("item", InventoryRules.id(item)));
                            return null;
                          })));
          if (source)
            list.add(
                entry(
                    Items.BOOK,
                    "§eRemplacer : " + rule.getOrDefault("source", rule.get("item")),
                    player ->
                        input(
                            player,
                            "Objet vanilla à remplacer",
                            String.valueOf(rule.getOrDefault("source", rule.get("item"))),
                            here,
                            text -> {
                              Item item = InventoryRules.item(text);
                              if (item == null || item == Items.AIR) return "Objet inconnu";
                              editRule(
                                  path, index, map -> map.put("source", InventoryRules.id(item)));
                              return null;
                            })));
          list.add(
              entry(
                  Items.HOPPER,
                  "§eQuantité : " + rule.get("minimum") + "–" + rule.get("maximum"),
                  player ->
                      input(
                          player,
                          "Quantité : 1 ou 1-3",
                          rule.get("minimum") + "-" + rule.get("maximum"),
                          here,
                          text -> {
                            var range = RuleMath.range(text, 0, 64);
                            if (range == null)
                              return "Quantité attendue : 0 à 64, minimum ≤ maximum";
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
                  "§eChance : " + rule.get("chance") + "%",
                  player ->
                      input(
                          player,
                          "Chance en %",
                          String.valueOf(rule.get("chance")),
                          here,
                          text -> {
                            Double chance = RuleMath.percentage(text);
                            if (chance == null) return "Pourcentage attendu : 0 à 100";
                            editRule(path, index, map -> map.put("chance", chance));
                            return null;
                          })));
          list.add(
              entry(
                  Items.TNT,
                  source ? "§aRestaurer le butin vanilla" : "§cSupprimer cet objet",
                  player ->
                      confirm(
                          player,
                          "Supprimer le butin ?",
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
                    "§eCCR • Cette règle a été supprimée par un autre administrateur. Modification"
                        + " annulée."));
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
                  looting ? "§eVoir sans Looting" : "§bAperçu avec Looting III",
                  () -> vanillaMob(id, !looting),
                  "§7Aperçu de la table native, sans apparition de créature"));
          list.add(
              folder(
                  Items.WRITABLE_BOOK,
                  "§eOverrides configurés / ajout manuel",
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
                    "§7Quantité vanilla : " + vanilla.get("minimum") + "–" + vanilla.get("maximum"),
                    "§7Chance observée : "
                        + String.format(java.util.Locale.ROOT, "%.1f", vanilla.get("chance"))
                        + "% (256 tirages)",
                    "§eClic gauche : modifier / remplacer",
                    "§eClic droit : désactiver / restaurer le vanilla");
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
                    "§7Aucun drop découvert",
                    player -> {},
                    "§7Les ajouts manuels restent disponibles"));
          return list;
        });
  }

  private Page vanillaOre(String id) {
    String path = "ore-drops." + id + ".vanilla-overrides";
    Supplier<Page> here = () -> vanillaOre(id);
    return new Page(
        "CCR • Butin " + pretty(id),
        () -> ore(id),
        () -> {
          Map<String, Object> vanilla = VanillaCatalog.ore(id, false),
              fortune = VanillaCatalog.ore(id, true);
          List<Entry> list = new ArrayList<>();
          list.add(
              entry(
                  LootRules.ORES.get(id),
                  "§eModifier le drop vanilla",
                  player -> {
                    List<Map<String, Object>> values = mod.config.entries(path);
                    if (values.isEmpty()) {
                      mod.config.set(path, List.of(new LinkedHashMap<>(vanilla)));
                    }
                    open(player, drop(path, 0, false, here));
                  },
                  "§7Sans Fortune : " + vanilla.get("minimum") + "–" + vanilla.get("maximum"),
                  "§7Fortune III : " + fortune.get("minimum") + "–" + fortune.get("maximum"),
                  "§7Silk Touch garde le minerai, sans override"));
          list.add(reset("Rétablir le drop vanilla", path, here));
          return list;
        });
  }

  private Page timber() {
    return new Page(
        "CCR • Timber",
        this::mainPage,
        () ->
            List.of(
                toggle(Items.IRON_AXE, "Timber actif", "tree-cutter.enabled", true, this::timber),
                folder(
                    Items.DIAMOND_AXE,
                    "§bOutils autorisés",
                    this::timberTools,
                    "§7Chaque hache et autres outils/main"),
                folder(
                    Items.CLOCK,
                    "§eTemps et progression",
                    this::timberTime,
                    "§7Casses répétées proportionnelles à l'arbre"),
                folder(Items.OAK_LEAVES, "§aFeuilles et pommes", this::timberHarvest),
                toggle(
                    Items.LEATHER_BOOTS,
                    "Accroupi uniquement",
                    "tree-cutter.sneak-only",
                    false,
                    this::timber),
                number(
                    Items.OAK_LOG,
                    "Limite de bûches",
                    "tree-cutter.max-logs",
                    256,
                    2,
                    2048,
                    this::timber),
                number(
                    Items.HOPPER,
                    "Blocs par tick",
                    "tree-cutter.blocks-per-tick",
                    12,
                    1,
                    64,
                    this::timber),
                reset("Réinitialiser Timber", "tree-cutter", this::timber)));
  }

  private Page timberTools() {
    return new Page(
        "CCR • Outils Timber",
        this::timber,
        () -> {
          List<Entry> list = new ArrayList<>();
          list.add(
              toggle(
                  Items.STICK,
                  "Main / autres outils",
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
        "CCR • Temps Timber",
        this::timber,
        () ->
            List.of(
                toggle(
                    Items.CLOCK,
                    "Temps proportionnel",
                    "tree-cutter.break-time.enabled",
                    false,
                    this::timberTime),
                percent(
                    Items.REPEATER,
                    "Temps par bûche restante",
                    "tree-cutter.break-time.percent",
                    100,
                    this::timberTime)));
  }

  private Page timberHarvest() {
    return new Page(
        "CCR • Récolte Timber",
        this::timber,
        () ->
            List.of(
                toggle(
                    Items.OAK_LEAVES,
                    "Casser les feuilles naturelles",
                    "tree-cutter.break-leaves",
                    false,
                    this::timberHarvest),
                percent(
                    Items.APPLE,
                    "Chance de pomme personnalisée",
                    "tree-cutter.apple-chance",
                    0.5,
                    this::timberHarvest),
                reset(
                    "Rétablir les pommes vanilla",
                    "tree-cutter.apple-chance",
                    this::timberHarvest)));
  }

  private Page cane() {
    return new Page(
        "CCR • Canne à sucre",
        this::mainPage,
        () ->
            List.of(
                toggle(
                    Items.SUGAR_CANE,
                    "Bonus actif",
                    "better-sugar-cane.enabled",
                    false,
                    this::cane),
                folder(
                    Items.IRON_AXE,
                    "§bCasse par un joueur",
                    () -> caneCause("player"),
                    "§7Réglages directs, indépendants du support"),
                folder(
                    Items.SAND,
                    "§ePerte du support",
                    () -> caneCause("support"),
                    "§7Bloc cassé dessous / physique"),
                reset("Réinitialiser la canne", "better-sugar-cane", this::cane)));
  }

  private Page caneCause(String cause) {
    String path = "better-sugar-cane." + cause;
    Supplier<Page> here = () -> caneCause(cause);
    return new Page(
        "CCR • Canne / " + (cause.equals("player") ? "joueur" : "support"),
        this::cane,
        () ->
            List.of(
                toggle(Items.SUGAR_CANE, "Cause active", path + ".enabled", true, here),
                percent(Items.GOLD_NUGGET, "Chance de bonus", path + ".chance", 100, here),
                entry(
                    Items.HOPPER,
                    "§eQuantité finale : "
                        + mod.config.integer(path + ".minimum", 1, 1, 64)
                        + "–"
                        + mod.config.integer(path + ".maximum", 3, 1, 64),
                    player ->
                        input(
                            player,
                            "Quantité : 1-3",
                            "1-3",
                            here,
                            text -> {
                              var range = RuleMath.range(text, 1, 64);
                              if (range == null) return "Quantité attendue : 1 à 64";
                              mod.config.set(path + ".minimum", range.minimum());
                              mod.config.set(path + ".maximum", range.maximum());
                              return null;
                            }),
                    "§7Remplace le total, n'ajoute pas 1",
                    "§7Les blocs placés gardent le butin vanilla")));
  }

  private Page clean() {
    return new Page(
        "CCR • Clean Stacks",
        this::mainPage,
        () ->
            List.of(
                folder(Items.OAK_LOG, "§aBois et planches", () -> cleanGroup("wood")),
                folder(Items.STONE, "§7Pierres naturelles", () -> cleanGroup("stone")),
                entry(
                    Items.LEVER,
                    "§eMa préférence Clean Stacks",
                    player -> {
                      mod.config.set(
                          "clean-stacks.players." + player.getUUID(),
                          !mod.inventory.cleanEnabled(player));
                      mod.inventory.correct(player);
                      open(player, clean());
                    },
                    "§7Commande personnelle : /cleanstacks status"),
                reset("Rétablir les conversions bois", "clean-stacks.wood", this::clean),
                reset("Rétablir les conversions pierre", "clean-stacks.stone", this::clean)));
  }

  private Page cleanGroup(String group) {
    Supplier<Page> here = () -> cleanGroup(group);
    return new Page(
        "CCR • Conversions " + group,
        this::clean,
        () -> {
          List<Entry> list = new ArrayList<>();
          list.add(cleanBulk(group, "all", "Tout convertir", here));
          if (group.equals("wood")) {
            list.add(cleanBulk(group, "logs", "Convertir les bûches non écorcées", here));
            list.add(cleanBulk(group, "stripped", "Convertir les bûches écorcées", here));
            list.add(cleanBulk(group, "planks", "Convertir toutes les planches", here));
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
                                "§7Vers : " + mod.config.text(path, defaultValue),
                                "§eClic gauche : choisir • clic droit : rétablir"),
                            player ->
                                input(
                                    player,
                                    "Objet de destination",
                                    mod.config.text(path, defaultValue),
                                    here,
                                    text -> {
                                      Item target = InventoryRules.item(text);
                                      if (target == null || target == Items.AIR)
                                        return "Objet inconnu";
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
                "Destination du groupe",
                group.equals("stone")
                    ? "minecraft:stone"
                    : kind.equals("planks") ? "minecraft:oak_planks" : "minecraft:oak_log",
                here,
                text -> {
                  Item target = InventoryRules.item(text);
                  if (target == null || target == Items.AIR) return "Objet inconnu";
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
        "§7Applique une destination au groupe en une fois");
  }

  private Page automatic() {
    return new Page(
        "CCR • Enchantements auto",
        this::mainPage,
        () ->
            List.of(
                toggle(
                    Items.ENCHANTED_BOOK,
                    "Activation",
                    "automatic-enchantments.enabled",
                    true,
                    this::automatic),
                folder(Items.DIAMOND_CHESTPLATE, "§bArmures", () -> autoGroup("armor")),
                folder(Items.DIAMOND_PICKAXE, "§eOutils et armes", () -> autoGroup("tools")),
                entry(
                    Items.CLOCK,
                    "§aRafraîchir les anciens objets CCR",
                    player ->
                        confirm(
                            player,
                            "Rafraîchir les objets intacts ?",
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
                    "§7Ne modifie jamais les enchantements changés par le joueur",
                    "§7Restaure les enchantements d'origine avant recalcul")));
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
                                  + " enchantement(s)")));
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
                  "§aChoisir un enchantement",
                  () -> enchantmentPicker(group, item),
                  "§7Liste des enchantements compatibles"));
          list.add(
              entry(
                  Items.WRITABLE_BOOK,
                  "§fSaisie rapide : id niveau",
                  player ->
                      input(
                          player,
                          "Ex. sharpness 3",
                          "minecraft:unbreaking 3",
                          here,
                          text -> {
                            String[] fields = text.trim().split("\\s+");
                            if (fields.length != 2) return "Format : enchantement niveau";
                            var enchantment = mod.inventory.enchantment(fields[0]);
                            Integer level = RuleMath.integer(fields[1], 1, 255);
                            if (enchantment == null
                                || level == null
                                || !enchantment.value().canEnchant(new ItemStack(item)))
                              return "Enchantement invalide pour cet objet (niveau 1–255)";
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
                                      "Niveau (0 = supprimer)",
                                      String.valueOf(value),
                                      here,
                                      text -> {
                                        Integer level = RuleMath.integer(text, 0, 255);
                                        if (level == null) return "Niveau attendu : 0 à 255";
                                        mod.config.set(path + "." + id, level == 0 ? null : level);
                                        return null;
                                      }))));
          list.add(
              entry(
                  Items.WRITABLE_BOOK,
                  "§bCopier aux objets du même matériau",
                  player ->
                      confirm(
                          player,
                          "Copier sans écraser ?",
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
          list.add(reset("Supprimer la règle automatique", path, () -> autoGroup(group)));
          return list;
        });
  }

  private Page enchantmentPicker(String group, Item item) {
    String path =
        "automatic-enchantments." + group + "." + RuleConfig.materialKey(InventoryRules.id(item));
    Supplier<Page> parent = () -> autoItem(group, item);
    return new Page(
        "CCR • Enchantements compatibles",
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
                                    pretty(id) + " : niveau",
                                    String.valueOf(Math.max(1, current)),
                                    parent,
                                    text -> {
                                      Integer level = RuleMath.integer(text, 1, 255);
                                      if (level == null) return "Niveau attendu : 1 à 255";
                                      mod.config.set(path + "." + id, level);
                                      return null;
                                    }),
                            "§7Compatible avec cet objet",
                            "§7Niveau vanilla maximum : " + holder.value().getMaxLevel(),
                            "§eCliquer pour choisir le niveau"));
                  });
          return result;
        });
  }

  private Page targetPicker(boolean cap, String ruleId) {
    String path = InventoryRules.ROOT + (cap ? ".enchantment-caps" : ".armor-rules");
    if (mod.config.entryById(path, ruleId) == null) return ruleList(cap);
    Supplier<Page> parent = () -> rule(cap, ruleId);
    return new Page(
        "CCR • Choisir une cible",
        parent,
        () -> {
          Map<String, Object> rule = mod.config.entryById(path, ruleId);
          if (rule == null) return List.of();
          List<Entry> result = new ArrayList<>();
          result.add(
              folder(
                  Items.ITEM_FRAME,
                  "§bObjets exacts / sélection multiple",
                  () -> exactTargets(cap, ruleId),
                  "§7Choisir un ou plusieurs objets précis"));
          result.add(
              entry(
                  Items.WRITABLE_BOOK,
                  "§fSaisie d'une catégorie ou liste",
                  player ->
                      inputRule(
                          player,
                          path,
                          ruleId,
                          "Cible de la règle",
                          String.valueOf(rule.get("target")),
                          parent,
                          text -> {
                            String target = text.trim().toUpperCase(java.util.Locale.ROOT);
                            if (!validTarget(target, cap))
                              return "Catégorie ou liste d'objets invalide";
                            editRule(path, ruleId, map -> map.put("target", target));
                            return null;
                          }),
                  "§7Pour coller une liste séparée par des virgules"));
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
                    "§eCliquer pour cibler ce groupe"));
          }
          return result;
        });
  }

  private Page exactTargets(boolean cap, String ruleId) {
    String path = InventoryRules.ROOT + (cap ? ".enchantment-caps" : ".armor-rules");
    if (mod.config.entryById(path, ruleId) == null) return ruleList(cap);
    Supplier<Page> here = () -> exactTargets(cap, ruleId);
    return new Page(
        "CCR • Objets exacts",
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
                                          "CCR • Gardez au moins un objet, ou choisissez une"
                                              + " catégorie."));
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
                            "§7Cliquer pour sélectionner / désélectionner",
                            "§7La règle s'applique aux objets sélectionnés"));
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
                    "Règles actives",
                    InventoryRules.ROOT + ".enabled",
                    false,
                    this::stuff),
                folder(
                    Items.DIAMOND_CHESTPLATE,
                    "§bLimites d'armure équipée",
                    () -> ruleList(false),
                    "§7Déplace les pièces en surplus vers l'inventaire"),
                folder(
                    Items.ENCHANTED_BOOK,
                    "§dPlafonds d'enchantement",
                    () -> ruleList(true),
                    "§7Objets ciblés, plafonds et remboursement payé"),
                folder(
                    Items.MACE,
                    "§6Masse unique",
                    this::mace,
                    "§7Identité persistante et libération après destruction")));
  }

  private Page mace() {
    String path = InventoryRules.ROOT + ".unique-mace";
    return new Page(
        "CCR • Masse unique",
        this::stuff,
        () ->
            List.of(
                toggle(
                    Items.MACE, "Une seule masse fabriquée", path + ".enabled", false, this::mace),
                entry(
                    Items.COMPASS,
                    "§eÉtat : "
                        + (mod.config.bool(path + ".exists", false)
                            ? "DÉJÀ FABRIQUÉE"
                            : "DISPONIBLE"),
                    player -> open(player, mace()),
                    "§7Identité : " + mod.config.text(path + ".id", "aucune"),
                    "§7Déchargement, coffre et ramassage ne libèrent pas la masse"),
                entry(
                    Items.TNT,
                    "§cAutoriser manuellement une nouvelle masse",
                    player ->
                        confirm(
                            player,
                            "Autoriser une autre masse ?",
                            this::mace,
                            () -> {
                              mod.config.set(path + ".exists", false);
                              mod.config.set(path + ".id", null);
                            }),
                    "§cAction administrateur : peut permettre une seconde masse")));
  }

  private Page ruleList(boolean cap) {
    String path = InventoryRules.ROOT + (cap ? ".enchantment-caps" : ".armor-rules");
    Supplier<Page> here = () -> ruleList(cap);
    return new Page(
        "CCR • " + (cap ? "Plafonds d'enchantement" : "Armures équipées"),
        this::stuff,
        () -> {
          List<Entry> list = new ArrayList<>();
          list.add(
              entry(
                  InventoryRules.item("minecraft:lime_dye"),
                  "§aAjouter une règle",
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
                  "§7Créée désactivée pour choisir les valeurs d'abord"));
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
                    cap ? "§7" + rule.get("enchantment") : "§7Nombre de pièces équipées"));
          }
          return list;
        });
  }

  private Page rule(boolean cap, String ruleId) {
    String path = InventoryRules.ROOT + (cap ? ".enchantment-caps" : ".armor-rules");
    if (mod.config.entryById(path, ruleId) == null) return ruleList(cap);
    Supplier<Page> here = () -> rule(cap, ruleId);
    return new Page(
        "CCR • " + (cap ? "Plafond d'enchantement" : "Armure équipée"),
        () -> ruleList(cap),
        () -> {
          Map<String, Object> rule = mod.config.entryById(path, ruleId);
          if (rule == null) return List.of();
          List<Entry> list = new ArrayList<>();
          list.add(
              entry(
                  Items.LEVER,
                  (RuleConfig.enabled(rule) ? "§a" : "§c")
                      + "Règle "
                      + (RuleConfig.enabled(rule) ? "active" : "inactive"),
                  player -> {
                    editRule(path, ruleId, map -> map.put("enabled", !RuleConfig.enabled(map)));
                    open(player, here.get());
                  }));
          list.add(
              folder(
                  Items.ITEM_FRAME,
                  "§bCible : " + rule.get("target"),
                  () -> targetPicker(cap, ruleId),
                  "§7Catégories, matériaux et objets exacts",
                  "§7Sélection visuelle ou liste personnalisée"));
          list.add(
              entry(
                  Items.HOPPER,
                  "§eMaximum : " + rule.get("maximum"),
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
                            if (value == null) return cap ? "Maximum 1 à 255" : "Maximum 0 à 4";
                            editRule(path, ruleId, map -> map.put("maximum", value));
                            return null;
                          })));
          if (cap) {
            list.add(
                entry(
                    Items.ENCHANTED_BOOK,
                    "§dEnchantement : " + rule.get("enchantment"),
                    player ->
                        inputRule(
                            player,
                            path,
                            ruleId,
                            "Enchantement",
                            String.valueOf(rule.get("enchantment")),
                            here,
                            text -> {
                              var enchantment = mod.inventory.enchantment(text.trim());
                              if (enchantment == null) return "Enchantement inconnu";
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
                    "§aRembourser l'XP : " + Boolean.TRUE.equals(rule.get("refund-xp")),
                    player -> {
                      editRule(
                          path,
                          ruleId,
                          map -> map.put("refund-xp", !Boolean.TRUE.equals(map.get("refund-xp"))));
                      open(player, here.get());
                    },
                    "§7Uniquement une transaction réellement payée",
                    "§7Jamais plus que les niveaux payés"));
            list.add(
                entry(
                    Items.GOLD_NUGGET,
                    "§eXP par niveau retiré : " + rule.getOrDefault("xp-per-removed-level", 0),
                    player ->
                        inputRule(
                            player,
                            path,
                            ruleId,
                            "Niveaux d'XP par enchantement retiré",
                            String.valueOf(rule.getOrDefault("xp-per-removed-level", 0)),
                            here,
                            text -> {
                              Integer value = RuleMath.integer(text, 0, 1000);
                              if (value == null) return "Valeur de 0 à 1000";
                              editRule(path, ruleId, map -> map.put("xp-per-removed-level", value));
                              return null;
                            })));
          }
          list.add(
              entry(
                  Items.WRITABLE_BOOK,
                  "§bDupliquer vers une autre cible",
                  player ->
                      inputRule(
                          player,
                          path,
                          ruleId,
                          "Nouvelle cible",
                          String.valueOf(rule.get("target")),
                          () -> ruleList(cap),
                          text -> {
                            String target = text.trim().toUpperCase(java.util.Locale.ROOT);
                            if (!validTarget(target, cap)) return "Cible invalide";
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
                  "§7La copie est désactivée"));
          list.add(
              entry(
                  Items.TNT,
                  "§cSupprimer cette règle",
                  player ->
                      confirm(
                          player,
                          "Supprimer la règle ?",
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
                        "§aConfirmer : " + title,
                        p -> {
                          action.run();
                          open(p, parent.get());
                        }),
                    entry(
                        InventoryRules.item("minecraft:red_concrete"),
                        "§cAnnuler",
                        p -> open(p, parent.get())))));
  }

  public void open(ServerPlayer player, Page page) {
    if (!NativeAccess.admin(((ServerPlayer) player).createCommandSourceStack())) {
      player.sendSystemMessage(Component.literal("§cCCR • Permission administrateur requise."));
      return;
    }
    opening.put(player.getUUID(), true);
    try {
      player.openMenu(
          new SimpleMenuProvider(
              (id, inventory, p) -> new Panel(id, inventory, page),
              Component.literal(page.title())));
    } finally {
      opening.remove(player.getUUID());
    }
  }

  private void input(
      ServerPlayer player,
      String title,
      String initial,
      Supplier<Page> parent,
      Function<String, String> submit) {
    opening.put(player.getUUID(), true);
    try {
      player.openMenu(
          new SimpleMenuProvider(
              (id, inventory, p) -> new TextInput(id, inventory, title, initial, parent, submit),
              Component.literal("CCR • " + title)));
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
      this.page = page;
      for (int slot = 0; slot < 54; slot++)
        getSlot(slot)
            .set(icon(InventoryRules.item("minecraft:gray_stained_glass_pane"), " ", List.of()));
      getSlot(4)
          .set(
              icon(
                  Items.BOOK,
                  "§6" + page.title(),
                  List.of(
                      "§7Échap = niveau précédent",
                      "§7Les réglages sont sauvegardés immédiatement")));
      List<Entry> entries = page.entries().get();
      int pages = Math.max(1, (entries.size() + 35) / 36),
          index = Math.clamp(page.index(), 0, pages - 1);
      for (int offset = 0; offset < 36 && index * 36 + offset < entries.size(); offset++) {
        Entry entry = entries.get(index * 36 + offset);
        int slot = 9 + offset;
        getSlot(slot).set(icon(entry.icon(), entry.title(), entry.lore()));
        actions.put(slot, entry.click());
        if (entry.rightClick() != null) rightActions.put(slot, entry.rightClick());
      }
      if (page.parent() != null) {
        getSlot(45).set(icon(Items.ARROW, "§eRetour", List.of("§7Comme la touche Échap")));
        actions.put(45, player -> open(player, page.parent().get()));
      }
      if (index > 0) {
        getSlot(48).set(icon(Items.PAPER, "§ePage précédente", List.of()));
        actions.put(
            48,
            player ->
                open(player, new Page(page.title(), page.parent(), page.entries(), index - 1)));
      }
      getSlot(49)
          .set(
              icon(
                  Items.MAP,
                  "§fPage " + (index + 1) + " / " + pages,
                  List.of("§7La pagination ne change pas le niveau de retour")));
      if (index + 1 < pages) {
        getSlot(50).set(icon(Items.PAPER, "§ePage suivante", List.of()));
        actions.put(
            50,
            player ->
                open(player, new Page(page.title(), page.parent(), page.entries(), index + 1)));
      }
      getSlot(53).set(icon(Items.BARRIER, "§cFermer les menus", List.of()));
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
                  List.of(
                      "§7Modifiez le texte au-dessus",
                      "§7Cliquez le résultat pour valider",
                      "§7Échap annule et revient au menu")));
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
      return true;
    }

    @Override
    public void createResult() {
      if (resultSlots == null) return;
      resultSlots.setItem(
          0,
          icon(
              InventoryRules.item("minecraft:lime_dye"),
              "§aValider : " + (value == null ? "" : value),
              List.of("§7Clic pour valider • Échap pour annuler")));
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
                  icon(Items.BARRIER, "§c" + error, List.of("§7Corrigez le texte puis validez")));
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
