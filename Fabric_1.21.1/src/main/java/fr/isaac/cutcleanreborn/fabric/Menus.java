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
      this(title, parent, entries, 0);
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
            + " : "
            + (!enabled ? "NON" : available ? "OUI" : "EN PAUSE"),
        player -> {
          writeSetting(path, !mod.settings().bool(path, fallback));
          mod.save();
          open(player, page.get());
        },
        enabled && !available
            ? "§eActivez d'abord l'interrupteur général"
            : "§7Cliquer pour changer",
        "§7Réglage enregistré : " + (enabled ? "activé" : "désactivé"));
  }

  private Entry number(
      Item icon, String title, String path, int fallback, int min, int max, Supplier<Page> page) {
    return entry(
        icon,
        title + " : §e" + mod.settings().integer(path, fallback, min, max),
        player ->
            input(
                player,
                title,
                String.valueOf(mod.settings().integer(path, fallback, min, max)),
                page,
                text -> {
                  Integer value = GameplayMath.integer(text, min, max);
                  if (value == null) return "Entrez un entier de " + min + " à " + max;
                  writeSetting(path, value);
                  mod.save();
                  return null;
                }),
        "§7Valeur autorisée : " + min + " à " + max);
  }

  private Entry percent(
      Item icon, String title, String path, double fallback, Supplier<Page> page) {
    return entry(
        icon,
        title + " : §e" + mod.settings().decimal(path, fallback, 0, 100) + "%",
        player ->
            input(
                player,
                title,
                String.valueOf(mod.settings().decimal(path, fallback, 0, 100)),
                page,
                text -> {
                  Double value = GameplayMath.percentage(text);
                  if (value == null) return "Entrez un pourcentage de 0 à 100";
                  mod.settings().set(path, value);
                  mod.save();
                  return null;
                }),
        "§7Pourcentage de 0 à 100");
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
        "§7Confirmation nécessaire");
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
                      if (mod.reload()) open(player, mainPage());
                      else
                        player.sendSystemMessage(
                            Component.literal("§cCCR • YAML invalide, ancien état conservé."));
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
                                  instanceof LivingEntity)) return "Créature vivante inconnue";
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
                        && mod.settings().bool("animal-drops.cook", true),
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
                        ? mod.settings().integer("animal-drops.xp-bonus", 0, 0, 50)
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
                reset("Réinitialiser cette créature", path, here),
                entry(
                    Items.CLOCK,
                    "§eRéinitialiser uniquement le butin",
                    player ->
                        confirm(
                            player,
                            "Rétablir les drops vanilla et enlever les extras ?",
                            here,
                            () -> {
                              mod.settings().set(path + ".vanilla-overrides", null);
                              mod.settings().set(path + ".drops", null);
                              mod.save();
                            }),
                    "§7Conserve la cuisson et le bonus d'XP")));
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
                    player -> {
                      Page[] destination = {here.get()};
                      input(
                          player,
                          "Minerai destinataire",
                          "deepslate_" + id,
                          () -> destination[0],
                          text -> {
                            String target =
                                text.trim()
                                    .replace("minecraft:", "")
                                    .toLowerCase(java.util.Locale.ROOT);
                            if (!LootRules.ORES.containsKey(target)) return "Minerai inconnu";
                            if (target.equals(id)) return "Choisissez un autre minerai";
                            destination[0] =
                                confirmation(
                                    "Écraser les réglages de " + pretty(target) + " ?",
                                    here,
                                    () -> {
                                      mod.settings()
                                          .set("ore-drops." + target, mod.settings().section(path));
                                      mod.save();
                                    });
                            return null;
                          });
                    },
                    "§7Copie XP, overrides et extras"),
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
                  Items.LIME_DYE,
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
          List<Map<String, Object>> values = mod.settings().entries(path);
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
                            var range = GameplayMath.range(text, 0, 64);
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
                            Double chance = GameplayMath.percentage(text);
                            if (chance == null) return "Pourcentage attendu : 0 à 100";
                            editRule(path, index, map -> map.put("chance", chance));
                            return null;
                          })));
          list.add(
              entry(
                  Items.TNT,
                  path.endsWith("vanilla-overrides")
                      ? "§eRétablir le comportement vanilla"
                      : "§cSupprimer cet extra",
                  player ->
                      confirm(
                          player,
                          path.endsWith("vanilla-overrides")
                              ? "Retirer ce remplacement et rétablir vanilla ?"
                              : "Supprimer cet extra ?",
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
                    "§cDésactiver ce drop vanilla (0%)",
                    player -> {
                      editRule(path, index, map -> map.put("chance", 0.0));
                      open(player, here.get());
                    },
                    "§7Conserve un remplacement explicite à 0%",
                    "§7Ne restaure pas la table vanilla"));
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
                  looting ? "§eVoir sans Looting" : "§bAperçu avec Looting III",
                  () -> vanillaMob(id, !looting),
                  "§7Aperçu de la table native, sans apparition de créature"));
          list.add(
              folder(
                  Items.WRITABLE_BOOK,
                  "§eOverrides configurés / ajout manuel",
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
                            ? "§c[DÉSACTIVÉ] "
                            : "§f")
                        + pretty(source),
                    List.of(
                        configured == null
                            ? "§7Vanilla non modifié"
                            : "§eOverride : "
                                + configured.get("minimum")
                                + "–"
                                + configured.get("maximum")
                                + " à "
                                + configured.get("chance")
                                + "%",
                        "§7Quantité vanilla : "
                            + vanilla.get("minimum")
                            + "–"
                            + vanilla.get("maximum"),
                        "§7Chance observée : "
                            + String.format(java.util.Locale.ROOT, "%.1f", vanilla.get("chance"))
                            + "% (256 tirages)",
                        "§eGauche : modifier • droite : désactiver / rétablir"),
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
                    List<Map<String, Object>> values = mod.settings().entries(path);
                    if (values.isEmpty()) {
                      mod.settings().set(path, List.of(new LinkedHashMap<>(vanilla)));
                      mod.save();
                    }
                    open(player, drop(path, 0, false, here));
                  },
                  "§7Sans Fortune : " + vanilla.get("minimum") + "–" + vanilla.get("maximum"),
                  "§7Fortune III : " + fortune.get("minimum") + "–" + fortune.get("maximum"),
                  "§7Silk Touch garde le minerai, sans override"));
          list.add(reset("Rétablir le drop vanilla", path, here));
          list.add(
              entry(
                  Items.BARRIER,
                  "§cDésactiver le drop vanilla (0%)",
                  player -> {
                    mod.settings().set(path, List.of(new LinkedHashMap<>(vanilla)));
                    editRule(path, 0, map -> map.put("chance", 0.0));
                    open(player, here.get());
                  },
                  "§7Les extras et XP bonus restent configurés"));
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
                              "tree-cutter.axes." + Settings.materialKey(InventoryRules.id(item)),
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
                        + mod.settings().integer(path + ".minimum", 1, 1, 64)
                        + "–"
                        + mod.settings().integer(path + ".maximum", 3, 1, 64),
                    player ->
                        input(
                            player,
                            "Quantité : 1-3",
                            "1-3",
                            here,
                            text -> {
                              var range = GameplayMath.range(text, 1, 64);
                              if (range == null) return "Quantité attendue : 1 à 64";
                              mod.settings().set(path + ".minimum", range.minimum());
                              mod.settings().set(path + ".maximum", range.maximum());
                              mod.save();
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
                      mod.settings()
                          .set(
                              "clean-stacks.players." + player.getUUID(),
                              !mod.inventory().cleanEnabled(player));
                      mod.save();
                      mod.inventory().tick(player);
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
                                "§7Vers : " + mod.settings().text(path, defaultValue),
                                "§eClic gauche : choisir • clic droit : rétablir"),
                            player ->
                                input(
                                    player,
                                    "Objet de destination",
                                    mod.settings().text(path, defaultValue),
                                    here,
                                    text -> {
                                      Item target = InventoryRules.item(text);
                                      if (target == null || target == Items.AIR)
                                        return "Objet inconnu";
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
                "Destination du groupe",
                group.equals("stone")
                    ? "minecraft:stone"
                    : kind.equals("planks") ? "minecraft:oak_planks" : "minecraft:oak_log",
                here,
                text -> {
                  Item target = InventoryRules.item(text);
                  if (target == null || target == Items.AIR) return "Objet inconnu";
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
                    "§7Ne modifie jamais les enchantements changés par le joueur",
                    "§7Restaure les enchantements d'origine avant recalcul")));
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
                  "§bRechercher un objet",
                  player -> {
                    Page[] destination = {autoGroup(group)};
                    input(
                        player,
                        "Identifiant de l'objet",
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
                            return "Objet non compatible avec cette catégorie";
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
                                "§7" + mod.settings().section(path).size() + " enchantement(s)",
                                "§eGauche : configurer • droite : supprimer"),
                            player -> open(player, autoItem(group, item)),
                            player ->
                                confirm(
                                    player,
                                    "Supprimer la configuration ?",
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
                  "§aAjouter un enchantement",
                  () ->
                      enchantmentPicker(
                          item,
                          "",
                          here,
                          (player, enchantment) ->
                              input(
                                  player,
                                  "Niveau (1–255)",
                                  String.valueOf(Math.max(1, automaticLevel(path, enchantment))),
                                  here,
                                  text -> {
                                    Integer level = GameplayMath.integer(text, 1, 255);
                                    if (level == null) return "Niveau attendu : 1 à 255";
                                    setAutomaticLevel(path, enchantment, level);
                                    return null;
                                  })),
                  "§7Parcourir les enchantements compatibles et rechercher"));
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
                                      "Niveau (0 = supprimer)",
                                      String.valueOf(value),
                                      here,
                                      text -> {
                                        Integer level = GameplayMath.integer(text, 0, 255);
                                        if (level == null) return "Niveau attendu : 0 à 255";
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
          list.add(reset("Supprimer la règle automatique", path, () -> autoGroup(group)));
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
                        + (mod.settings().bool(path + ".exists", false)
                            ? "DÉJÀ FABRIQUÉE"
                            : "DISPONIBLE"),
                    player -> open(player, mace()),
                    "§7Identité : " + mod.settings().text(path + ".id", "aucune"),
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
                              mod.settings().set(path + ".exists", false);
                              mod.settings().set(path + ".id", null);
                              mod.save();
                            }),
                    "§cAction administrateur : peut permettre une seconde masse")));
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
      super("Cette règle a été supprimée par un autre administrateur. Modification annulée.");
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
        "CCR • " + (cap ? "Plafonds d'enchantement" : "Armures équipées"),
        this::stuff,
        () -> {
          List<Entry> list = new ArrayList<>();
          list.add(
              entry(
                  Items.LIME_DYE,
                  "§aAjouter une règle",
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
                  "§7Créée désactivée pour choisir les valeurs d'abord"));
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
                            ? " [EN PAUSE]"
                            : ""),
                    List.of(
                        cap ? "§7" + rule.get("enchantment") : "§7Nombre de pièces équipées",
                        "§eGauche : éditer • droite : activer/désactiver",
                        "§7Maj-clic : confirmer la suppression"),
                    player -> open(player, ruleById(cap, id)),
                    player -> {
                      editRule(path, id, map -> map.put("enabled", !Settings.enabled(map)));
                      open(player, here.get());
                    },
                    player ->
                        confirm(player, "Supprimer la règle ?", here, () -> deleteRule(path, id))));
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
            ? "CCR • Règle supprimée"
            : "CCR • " + (cap ? "Plafond" : "Armure") + " #" + (index + 1),
        () -> ruleList(cap),
        () -> {
          Map<String, Object> rule = findRule(path, id);
          if (rule == null)
            return List.of(
                entry(
                    Items.BARRIER,
                    "§cRègle supprimée : modification annulée",
                    player -> open(player, ruleList(cap)),
                    "§7Un autre administrateur a retiré cette règle."));
          List<Entry> list = new ArrayList<>();
          list.add(
              entry(
                  Items.LEVER,
                  (!Settings.enabled(rule)
                          ? "§c"
                          : mod.settings().bool(InventoryRules.ROOT + ".enabled", false)
                              ? "§a"
                              : "§e")
                      + "Règle "
                      + (!Settings.enabled(rule)
                          ? "inactive"
                          : mod.settings().bool(InventoryRules.ROOT + ".enabled", false)
                              ? "active"
                              : "en pause (Stuff Rules désactivé)"),
                  player -> {
                    editRule(path, id, map -> map.put("enabled", !Settings.enabled(map)));
                    open(player, here.get());
                  }));
          list.add(
              folder(
                  Items.ITEM_FRAME,
                  "§bCible : " + InventoryRules.ruleTarget(rule, cap ? "item" : "material"),
                  () ->
                      targetPicker(
                          cap,
                          InventoryRules.ruleTarget(rule, cap ? "item" : "material"),
                          here,
                          target -> editRule(path, id, map -> map.put("target", target))),
                  cap
                      ? "§7ALL / TOOLS / SWORDS / ARMOR / objets exacts"
                      : "§7ARMOR / DIAMOND / IRON… / pièces exactes"));
          list.add(
              entry(
                  Items.HOPPER,
                  "§eMaximum : " + rule.get("maximum"),
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
                              return cap ? "Entier positif attendu" : "Maximum 0 à 4";
                            editRule(path, id, map -> map.put("maximum", value));
                            return null;
                          })));
          if (cap) {
            list.add(
                folder(
                    Items.ENCHANTED_BOOK,
                    "§dEnchantement : " + rule.get("enchantment"),
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
                    "§7Parcourir le registre ou rechercher par identifiant"));
            list.add(
                entry(
                    Items.EXPERIENCE_BOTTLE,
                    "§aRembourser l'XP : " + Boolean.TRUE.equals(rule.get("refund-xp")),
                    player -> {
                      editRule(
                          path,
                          id,
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
                        input(
                            player,
                            "Niveaux d'XP par enchantement retiré",
                            String.valueOf(rule.getOrDefault("xp-per-removed-level", 0)),
                            here,
                            text -> {
                              Integer value = GameplayMath.integer(text, 0, Integer.MAX_VALUE);
                              if (value == null) return "Entier positif ou nul attendu";
                              editRule(path, id, map -> map.put("xp-per-removed-level", value));
                              return null;
                            })));
          }
          list.add(
              folder(
                  Items.WRITABLE_BOOK,
                  "§bDupliquer vers une autre cible",
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
        "CCR • Enchantements",
        parent,
        () -> {
          List<Entry> list = new ArrayList<>();
          list.add(
              entry(
                  Items.COMPASS,
                  "§bRechercher un enchantement",
                  player -> {
                    Page[] destination = {here.get()};
                    input(
                        player,
                        "Recherche (vide = tous)",
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
                  "§7Identifiant partiel accepté • seuls les compatibles sont proposés"));
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
                                      "§7" + holder.key().location(), "§eCliquer pour choisir")
                                  : List.of(
                                      "§7" + holder.key().location(),
                                      "§eGauche : niveau • droite : retirer",
                                      "§7Niveau enregistré : "
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
        "CCR • Cible de la règle",
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
                    "§bGroupe : " + preset,
                    player -> {
                      choose.accept(preset);
                      open(player, parent.get());
                    },
                    "§7Choix actuel : " + current));
          list.add(
              folder(
                  Items.ITEM_FRAME,
                  "§eChoisir des objets exacts",
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
                  "§7Sélection multiple, recherche et validation explicite"));
          list.add(
              entry(
                  Items.NAME_TAG,
                  "§fSaisir une liste d'identifiants",
                  player ->
                      input(
                          player,
                          "Cible ou liste d'objets",
                          current,
                          parent,
                          text -> {
                            String target =
                                text.trim()
                                    .toUpperCase(java.util.Locale.ROOT)
                                    .replace("MINECRAFT:", "");
                            if (!validTarget(target, cap))
                              return "Cible inconnue ou liste contenant un objet invalide";
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
        "CCR • Objets exacts (" + selected.size() + ")",
        picker,
        () -> {
          List<Entry> list = new ArrayList<>();
          list.add(
              entry(
                  Items.LIME_CONCRETE,
                  "§aAppliquer " + selected.size() + " objet(s)",
                  player -> {
                    if (selected.isEmpty()) {
                      player.sendSystemMessage(
                          Component.literal("§cChoisissez au moins un objet."));
                      return;
                    }
                    choose.accept(
                        selected.stream()
                            .map(InventoryRules::id)
                            .sorted()
                            .collect(java.util.stream.Collectors.joining(",")));
                    open(player, destination.get());
                  },
                  "§7Aucune modification avant cette validation"));
          list.add(
              entry(
                  Items.COMPASS,
                  "§bRechercher un objet",
                  player -> {
                    Page[] page = {here.get()};
                    input(
                        player,
                        "Recherche (vide = tous)",
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
                              "§eCliquer pour sélectionner / retirer")));
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
                    "§aConfirmer : " + title,
                    p -> {
                      action.run();
                      open(p, parent.get());
                    }),
                entry(Items.RED_CONCRETE, "§cAnnuler", p -> open(p, parent.get()))));
  }

  public void open(ServerPlayer player, Page page) {
    if (!mod.admin(player)) {
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
    private final Map<Integer, Consumer<ServerPlayer>> shiftActions = new HashMap<>();
    private boolean closed;
    private final int openedEpoch = epoch;

    Panel(int id, Inventory inventory, Page page) {
      super(MenuType.GENERIC_9x6, id, inventory, new SimpleContainer(54), 6);
      this.page = page;
      for (int slot = 0; slot < 54; slot++)
        getSlot(slot).set(icon(Items.GRAY_STAINED_GLASS_PANE, " ", List.of()));
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
        if (entry.shiftClick() != null) shiftActions.put(slot, entry.shiftClick());
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
              Items.LIME_DYE,
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
                  icon(Items.BARRIER, "§c" + error, List.of("§7Corrigez le texte puis validez")));
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
