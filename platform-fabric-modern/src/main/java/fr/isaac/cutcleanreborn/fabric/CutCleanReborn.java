package fr.isaac.cutcleanreborn.fabric;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CutCleanReborn implements ModInitializer {
  public static final Logger LOG = LoggerFactory.getLogger("cutcleanreborn");
  public static volatile RuleRuntime runtime;

  @Override
  public void onInitialize() {
    ServerLifecycleEvents.SERVER_STARTING.register(CutCleanReborn::beginSession);
    ServerLifecycleEvents.SERVER_STOPPING.register(CutCleanReborn::endSession);
    ServerTickEvents.END_SERVER_TICK.register(
        server -> {
          if (runtime != null) runtime.tick();
        });
    net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
        (handler, sender, server) -> {
          if (runtime != null) runtime.requestCorrection(handler.player);
        });
    net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register(
        (oldPlayer, newPlayer, alive) -> {
          if (runtime != null) runtime.requestCorrection(newPlayer);
        });
    PlayerBlockBreakEvents.BEFORE.register(
        (level, player, pos, state, blockEntity) ->
            !(player instanceof ServerPlayer p)
                || runtime == null
                || runtime.timber.prepare(p, pos, state));
    PlayerBlockBreakEvents.AFTER.register(
        (level, player, pos, state, blockEntity) -> {
          if (runtime != null && player instanceof ServerPlayer p)
            runtime.timber.after(p, pos, state);
        });
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, context, environment) -> {
          for (String alias : new String[] {"ccr", "cutclean", "crr"}) {
            dispatcher.register(
                Commands.literal(alias)
                    .executes(
                        c -> {
                          if (c.getSource().getEntity() instanceof ServerPlayer player
                              && runtime != null
                              && NativeAccess.admin(c.getSource())) runtime.menus.root(player);
                          else
                            c.getSource()
                                .sendSuccess(
                                    () -> Component.literal("CCR: /ccr help | /ccr status"), false);
                          return 1;
                        })
                    .then(
                        Commands.literal("help")
                            .executes(
                                c -> {
                                  c.getSource()
                                      .sendSuccess(
                                          () ->
                                              Component.literal(
                                                  "CCR: config (admin), status, reload (admin),"
                                                      + " ping; /cleanstacks [on|off|status]."
                                                      + " Menus: Escape = parent, Close = exit."
                                                      + " /ccr edit <value> completes a pending"
                                                      + " setting."),
                                          false);
                                  return 1;
                                }))
                    .then(
                        Commands.literal("ping")
                            .executes(
                                c -> {
                                  c.getSource()
                                      .sendSuccess(() -> Component.literal("CCR • Pong"), false);
                                  return 1;
                                }))
                    .then(
                        Commands.literal("status")
                            .executes(
                                c -> {
                                  c.getSource()
                                      .sendSuccess(
                                          () ->
                                              Component.literal(
                                                  runtime == null
                                                      ? "CCR starting"
                                                      : "CCR • Fabric native • Timber "
                                                          + runtime.config.bool(
                                                              "tree-cutter.enabled", true)
                                                          + " • Auto Smelt "
                                                          + runtime.config.bool(
                                                              "ore-drops.auto-smelt", true)
                                                          + " • Auto Enchant "
                                                          + runtime.config.bool(
                                                              "automatic-enchantments.enabled",
                                                              true)
                                                          + " • Sugar Cane "
                                                          + runtime.config.bool(
                                                              "better-sugar-cane.enabled", true)
                                                          + " • Stuff Rules "
                                                          + runtime.config.bool(
                                                              "game-rules.stuff-rules.enabled",
                                                              false)),
                                          false);
                                  return 1;
                                }))
                    .then(
                        Commands.literal("config")
                            .requires(NativeAccess::admin)
                            .executes(
                                c -> {
                                  if (runtime != null
                                      && c.getSource().getEntity() instanceof ServerPlayer player)
                                    runtime.menus.root(player);
                                  else
                                    c.getSource()
                                        .sendSuccess(
                                            () ->
                                                Component.literal(
                                                    "CCR • Open the menus in-game, or edit"
                                                        + " config/cutcleanreborn/config.yml and"
                                                        + " use /ccr reload."),
                                            false);
                                  return 1;
                                }))
                    .then(
                        Commands.literal("reload")
                            .requires(NativeAccess::admin)
                            .executes(
                                c -> {
                                  if (runtime != null) {
                                    try {
                                      runtime.reload();
                                      c.getSource()
                                          .sendSuccess(
                                              () ->
                                                  Component.literal(
                                                      "CCR • Configuration reloaded. Open menus"
                                                          + " closed to refresh their values."),
                                              true);
                                    } catch (RuntimeException failure) {
                                      c.getSource()
                                          .sendFailure(
                                              Component.literal(
                                                  "CCR • Reload rejected; previous configuration"
                                                      + " retained: "
                                                      + failure.getMessage()));
                                      LOG.warn("Configuration reload rejected", failure);
                                      return 0;
                                    }
                                  }
                                  return 1;
                                }))
                    .then(
                        Commands.literal("edit")
                            .requires(NativeAccess::admin)
                            .then(
                                Commands.argument("value", StringArgumentType.greedyString())
                                    .executes(
                                        c ->
                                            runtime == null
                                                ? 0
                                                : runtime.menus.submit(
                                                    c.getSource().getPlayerOrException(),
                                                    StringArgumentType.getString(c, "value")))))
                    .then(
                        Commands.literal("cancel")
                            .executes(
                                c -> {
                                  if (runtime != null)
                                    runtime.menus.cancel(c.getSource().getPlayerOrException());
                                  return 1;
                                })));
          }
          var clean =
              Commands.literal("cleanstacks")
                  .executes(c -> clean(c.getSource().getPlayerOrException(), "toggle"));
          for (String action : new String[] {"on", "off", "status", "toggle", "true", "false"})
            clean.then(
                Commands.literal(action)
                    .executes(c -> clean(c.getSource().getPlayerOrException(), action)));
          dispatcher.register(clean);
        });
    LOG.info("CCR native Fabric initialized; dedicated and integrated servers supported.");
  }

  // These are the exact registered callbacks. Keeping them named permits mechanistic CCR testing
  // without illegally replaying Fabric's dimension finalization on an already-running server.
  static void beginSession(net.minecraft.server.MinecraftServer server) {
    runtime = new RuleRuntime(server);
  }

  static void endSession(net.minecraft.server.MinecraftServer server) {
    RuleRuntime current = runtime;
    if (current == null || current.server != server) return;
    try {
      current.stop();
    } finally {
      if (runtime == current) runtime = null;
    }
  }

  private static int clean(ServerPlayer player, String action) {
    if (runtime == null) return 0;
    String path = "clean-stacks.players." + player.getUUID();
    boolean enabled = runtime.config.bool(path, false);
    if (!action.equals("status")) {
      enabled =
          switch (action) {
            case "on", "true" -> true;
            case "off", "false" -> false;
            default -> !enabled;
          };
      runtime.config.set(path, enabled);
      runtime.inventory.correct(player);
    }
    player.sendSystemMessage(Component.literal("CCR • Clean Stacks: " + (enabled ? "ON" : "OFF")));
    return 1;
  }
}
