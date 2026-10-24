package fr.isaac.cutcleanreborn.fabric;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One server-authoritative runtime for dedicated servers and integrated solo worlds. */
public final class CutCleanReborn implements ModInitializer {
  public static final Logger LOGGER = LoggerFactory.getLogger("CutCleanReborn");
  private static CutCleanReborn instance;
  private MinecraftServer server;
  private Settings settings;
  private Origins origins;
  private WorldState worldState;
  private InventoryRules inventory;
  private LootRules loot;
  private Timber timber;
  private Menus menus;
  private VanillaCatalog catalog;
  private final List<Runnable> pending = new ArrayList<>();
  private final java.util.Map<String, String> mobSnapshots = new java.util.HashMap<>();
  private int ticks;

  public static CutCleanReborn instance() {
    return instance;
  }

  public static boolean ready() {
    return instance != null && instance.server != null && instance.origins != null;
  }

  public MinecraftServer server() {
    return server;
  }

  public Settings settings() {
    return settings;
  }

  public Origins origins() {
    return origins;
  }

  public InventoryRules inventory() {
    return inventory;
  }

  public LootRules loot() {
    return loot;
  }

  public Timber timber() {
    return timber;
  }

  public Menus menus() {
    return menus;
  }

  public VanillaCatalog catalog() {
    return catalog;
  }

  @Override
  public void onInitialize() {
    instance = this;
    settings =
        new Settings(
            FabricLoader.getInstance().getConfigDir().resolve("cutcleanreborn/config.yml"));
    try {
      settings.load();
    } catch (IOException | RuntimeException exception) {
      throw new IllegalStateException(
          "Cannot load CutCleanReborn config; original file retained", exception);
    }
    trackMobChanges(false);
    inventory = new InventoryRules(this);
    loot = new LootRules(this);
    timber = new Timber(this);
    menus = new Menus(this);
    catalog = new VanillaCatalog(this);
    timber.register();
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, access, environment) -> {
          var root =
              Commands.literal("ccr")
                  .executes(
                      context -> {
                        CommandSourceStack source = context.getSource();
                        if (source.getEntity() instanceof ServerPlayer player && admin(player))
                          menus.main(player);
                        else
                          source.sendSuccess(
                              () -> Component.literal("CCR • /ccr help • /cleanstacks status"),
                              false);
                        return 1;
                      })
                  .then(
                      Commands.literal("config")
                          .requires(this::admin)
                          .executes(
                              context -> {
                                if (context.getSource().getEntity() instanceof ServerPlayer player)
                                  menus.main(player);
                                else
                                  context
                                      .getSource()
                                      .sendSuccess(
                                          () ->
                                              Component.literal(
                                                  "CCR • Open the menus in game, or edit"
                                                      + " config.yml and run /ccr reload."),
                                          false);
                                return 1;
                              }))
                  .then(
                      Commands.literal("status")
                          .executes(
                              context -> {
                                context
                                    .getSource()
                                    .sendSuccess(
                                        () ->
                                            Component.literal(
                                                "CCR Fabric 1.21.1 • Timber="
                                                    + settings.bool("tree-cutter.enabled", true)
                                                    + " • AutoSmelt="
                                                    + settings.bool("ore-drops.auto-smelt", false)
                                                    + " • AutoEnchant="
                                                    + settings.bool(
                                                        "automatic-enchantments.enabled", true)
                                                    + " • SugarCane="
                                                    + settings.bool(
                                                        "better-sugar-cane.enabled", false)
                                                    + " • StuffRules="
                                                    + settings.bool(
                                                        InventoryRules.ROOT + ".enabled", false)),
                                        false);
                                return 1;
                              }))
                  .then(
                      Commands.literal("reload")
                          .requires(this::admin)
                          .executes(
                              context -> {
                                boolean reloaded = reload();
                                if (!reloaded) {
                                  context
                                      .getSource()
                                      .sendFailure(
                                          Component.literal(
                                              "CCR • Invalid config. Previous settings kept;"
                                                  + " see the server log."));
                                  return 0;
                                }
                                context
                                    .getSource()
                                    .sendSuccess(
                                        () -> Component.literal("CCR • Config reloaded."),
                                        false);
                                return 1;
                              }))
                  .then(
                      Commands.literal("help")
                          .executes(
                              context -> {
                                context
                                    .getSource()
                                    .sendSuccess(
                                        () ->
                                            Component.literal(
                                                "CCR • /ccr config|status|reload|help|ping •"
                                                    + " /cleanstacks on|off|status|toggle"),
                                        false);
                                return 1;
                              }))
                  .then(
                      Commands.literal("ping")
                          .executes(
                              context -> {
                                context
                                    .getSource()
                                    .sendSuccess(() -> Component.literal("CCR • Pong!"), false);
                                return 1;
                              }));
          var registered = dispatcher.register(root);
          for (String alias : List.of("cutclean", "crr"))
            dispatcher.register(
                Commands.literal(alias)
                    .executes(
                        context -> {
                          CommandSourceStack source = context.getSource();
                          if (source.getEntity() instanceof ServerPlayer player && admin(player))
                            menus.main(player);
                          else
                            source.sendSuccess(
                                () -> Component.literal("CCR • /ccr help • /cleanstacks status"),
                                false);
                          return 1;
                        })
                    .redirect(registered));
          dispatcher.register(
              Commands.literal("cleanstacks")
                  .executes(context -> clean(context.getSource(), "toggle"))
                  .then(
                      Commands.argument("mode", StringArgumentType.word())
                          .suggests(
                              (context, builder) -> {
                                for (String value :
                                    List.of("on", "off", "status", "toggle", "true", "false"))
                                  builder.suggest(value);
                                return builder.buildFuture();
                              })
                          .executes(
                              context ->
                                  clean(
                                      context.getSource(),
                                      StringArgumentType.getString(context, "mode")))));
        });
    ServerLifecycleEvents.SERVER_STARTED.register(
        started -> {
          server = started;
          ticks = 0;
          clearTransientState();
          origins =
              new Origins(
                  started
                      .getWorldPath(LevelResource.ROOT)
                      .resolve("data/cutcleanreborn-origins.json"));
          worldState =
              new WorldState(
                  started
                      .getWorldPath(LevelResource.ROOT)
                      .resolve("data/cutcleanreborn-state.json"));
          try {
            origins.load();
            worldState.load(settings, started.isDedicatedServer());
          } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException(
                "Cannot load CCR world state; files preserved", exception);
          }
          LOGGER.info(
              "CutCleanReborn ready (Fabric 1.21.1). /ccr config • solo and dedicated server.");
        });
    ServerPlayConnectionEvents.JOIN.register(
        (handler, sender, current) -> inventory.schedule(handler.player));
    ServerPlayConnectionEvents.DISCONNECT.register(
        (handler, current) -> timber.disconnect(handler.player));
    ServerTickEvents.END_SERVER_TICK.register(
        current -> {
          if (server != current || origins == null) return;
          List<Runnable> actions = new ArrayList<>(pending);
          pending.clear();
          for (Runnable action : actions)
            try {
              action.run();
            } catch (RuntimeException exception) {
              LOGGER.error("CCR scheduled action failed", exception);
            }
          timber.tick();
          ticks++;
          if (ticks % 20 == 0) current.getPlayerList().getPlayers().forEach(inventory::tick);
          if (ticks % 200 == 0) saveOrigins();
        });
    ServerLifecycleEvents.SERVER_STOPPING.register(
        stopping -> {
          if (stopping == server) {
            saveOrigins();
            save();
            menus.closeAll();
            clearTransientState();
          }
        });
    ServerLifecycleEvents.SERVER_STOPPED.register(
        stopped -> {
          if (stopped == server) {
            clearTransientState();
            server = null;
            origins = null;
            worldState = null;
          }
        });
  }

  public boolean admin(ServerPlayer player) {
    return player.hasPermissions(2)
        || (server != null && server.isSingleplayerOwner(player.getGameProfile()));
  }

  private boolean admin(CommandSourceStack source) {
    return source.hasPermission(2)
        || (source.getEntity() instanceof ServerPlayer player && admin(player));
  }

  private int clean(CommandSourceStack source, String mode)
      throws com.mojang.brigadier.exceptions.CommandSyntaxException {
    ServerPlayer player = source.getPlayerOrException();
    boolean before = inventory.cleanEnabled(player);
    Boolean value =
        switch (mode.toLowerCase(java.util.Locale.ROOT)) {
          case "on", "true" -> true;
          case "off", "false" -> false;
          case "toggle" -> !before;
          case "status" -> null;
          default -> null;
        };
    if (value != null) {
      settings.set("clean-stacks.players." + player.getUUID(), value);
      save();
    } else if (!mode.equalsIgnoreCase("status")) {
      source.sendFailure(Component.literal("CCR • Expected on, off, status or toggle"));
      return 0;
    }
    player.sendSystemMessage(
        Component.literal(
            "§6CCR • Clean Stacks: "
                + (inventory.cleanEnabled(player) ? "§aON" : "§cOFF")));
    return 1;
  }

  public void next(Runnable action) {
    pending.add(action);
  }

  /** Only pending work is per-session. Config, item identities and world files remain untouched. */
  private void clearTransientState() {
    pending.clear();
    inventory.clearTransientState();
    timber.clear();
    loot.clearTransientState();
    menus.clearTransientState();
    catalog.clear();
  }

  public boolean reload() {
    try {
      settings.load();
      trackMobChanges(false);
      timber.clear();
      catalog.clear();
      menus.closeAll();
      server.getPlayerList().getPlayers().forEach(inventory::tick);
      return true;
    } catch (IOException | RuntimeException exception) {
      LOGGER.error("CCR reload rejected; inspect config YAML", exception);
      return false;
    }
  }

  public void save() {
    try {
      trackMobChanges(true);
      settings.save();
      if (worldState != null) worldState.save(settings);
      if (server != null) server.getPlayerList().getPlayers().forEach(inventory::schedule);
    } catch (IOException exception) {
      LOGGER.error("CCR config save failed; previous file remains recoverable", exception);
    }
  }

  private void trackMobChanges(boolean stamp) {
    java.util.Map<String, String> latest = new java.util.HashMap<>();
    settings
        .section("mobs")
        .forEach(
            (mob, value) -> {
              java.util.Map<String, Object> content = Settings.map(value);
              content.remove("last-edited");
              content.remove("edit-count");
              String fingerprint = content.toString();
              latest.put(mob, fingerprint);
              if (stamp && !fingerprint.equals(mobSnapshots.get(mob))) {
                settings.set("mobs." + mob + ".last-edited", System.currentTimeMillis());
                settings.set("mobs." + mob + ".edit-count", null);
              }
            });
    mobSnapshots.clear();
    mobSnapshots.putAll(latest);
  }

  private void saveOrigins() {
    if (origins != null)
      try {
        origins.save();
      } catch (IOException exception) {
        LOGGER.error("CCR origin marker save failed", exception);
      }
  }
}
