package fr.isaac.cutcleanreborn.fabric;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;

/** Small version boundary, kept outside the shared gameplay source. */
public final class NativeAccess {
  private NativeAccess() {}

  public static void overlay(
      net.minecraft.world.entity.player.Player player,
      net.minecraft.network.chat.Component message) {
    player.sendOverlayMessage(message);
  }

  public static void drop(
      net.minecraft.server.level.ServerPlayer player, net.minecraft.world.item.ItemStack stack) {
    player.spawnAtLocation(player.level(), stack);
  }

  public static boolean admin(CommandSourceStack source) {
    return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
        || (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player
            && !source.getServer().isDedicatedServer()
            && source.getServer().isSingleplayerOwner(player.nameAndId()));
  }

  public static boolean mobDrops(net.minecraft.server.level.ServerLevel level) {
    return level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.MOB_DROPS);
  }

  public static boolean blockDrops(net.minecraft.server.level.ServerLevel level) {
    return level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.BLOCK_DROPS);
  }

  public static boolean living(
      net.minecraft.world.entity.EntityType<?> type, net.minecraft.server.MinecraftServer server) {
    return type.create(server.overworld(), net.minecraft.world.entity.EntitySpawnReason.COMMAND)
        instanceof net.minecraft.world.entity.LivingEntity;
  }
}
