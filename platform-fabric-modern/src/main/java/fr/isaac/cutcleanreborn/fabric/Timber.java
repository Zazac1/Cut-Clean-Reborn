package fr.isaac.cutcleanreborn.fabric;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Every extra block uses the real game mode break pipeline, including other Fabric protection
 * callbacks.
 */
public final class Timber {
  private final RuleRuntime mod;
  private final Map<UUID, Tree> ready = new HashMap<>();
  private final Map<UUID, Harvest> active = new HashMap<>();
  private final Map<UUID, Progress> progress = new HashMap<>();
  private final Set<UUID> internal = new HashSet<>();

  Timber(RuleRuntime mod) {
    this.mod = mod;
  }

  public boolean prepare(ServerPlayer player, BlockPos pos, BlockState state) {
    if (player.gameMode.getGameModeForPlayer() != net.minecraft.world.level.GameType.SURVIVAL)
      return true;
    return before(player.level(), player, pos, state);
  }

  public void after(ServerPlayer player, BlockPos pos, BlockState state) {
    mod.remove(player.level(), pos);
    if (internal.contains(player.getUUID())) return;
    Tree tree = ready.remove(player.getUUID());
    if (tree == null || !tree.first().equals(pos)) return;
    ArrayDeque<BlockPos> logs =
        new ArrayDeque<>(
            tree.logs().stream()
                .filter(p -> !p.equals(pos))
                .sorted(Comparator.comparingInt(BlockPos::getY))
                .toList());
    active.put(
        player.getUUID(),
        new Harvest(
            player,
            player.level(),
            tree.log(),
            tree.tool(),
            logs,
            new ArrayDeque<>(tree.leaves())));
  }

  private boolean before(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
    if (internal.contains(player.getUUID())
        || active.containsKey(player.getUUID())
        || !mod.config.bool("tree-cutter.enabled", true)
        || !state.is(BlockTags.LOGS)
        || mod.placed(level, pos)
        || (mod.config.bool("tree-cutter.sneak-only", false) && !player.isShiftKeyDown())
        || !toolAllowed(player.getMainHandItem())) return true;
    BlockPos base = pos;
    for (int depth = 0; depth < 64; depth++) {
      BlockPos below = base.below();
      if (!level.hasChunkAt(below)
          || !level.getBlockState(below).is(state.getBlock())
          || mod.placed(level, below)) break;
      base = below;
    }
    int maximum = mod.config.integer("tree-cutter.max-logs", 256, 2, 2048);
    List<BlockPos> logs = logs(level, base, state.getBlock(), maximum);
    if (logs.size() < 2 || logs.size() > maximum) return true;
    List<BlockPos> leaves = leaves(level, logs);
    if (leaves.isEmpty()) return true;
    if (mod.config.bool("tree-cutter.break-time.enabled", false)) {
      Progress previous = progress.get(player.getUUID());
      long now = System.currentTimeMillis();
      int count =
          previous != null
                  && previous.pos().equals(pos)
                  && previous.level() == level
                  && now - previous.at() < 15_000
              ? previous.count() + 1
              : 1;
      int required =
          Math.max(
              1,
              (int)
                  Math.ceil(
                      1
                          + (logs.size() - 1)
                              * RuleMath.percent(
                                  mod.config.decimal("tree-cutter.break-time.percent", 100))
                              / 100));
      NativeAccess.overlay(
          player, Component.literal("§6Timber • " + Math.min(count, required) + "/" + required));
      if (count < required) {
        progress.put(player.getUUID(), new Progress(level, pos.immutable(), count, now));
        return false;
      }
      progress.remove(player.getUUID());
    }
    ready.put(
        player.getUUID(),
        new Tree(
            pos.immutable(),
            state.getBlock(),
            player.getMainHandItem().getItem(),
            logs,
            mod.config.bool("tree-cutter.break-leaves", false) ? leaves : List.of()));
    return true;
  }

  private boolean toolAllowed(ItemStack tool) {
    String key = InventoryRules.id(tool.getItem());
    return key.endsWith("_axe")
        ? mod.config.bool("tree-cutter.axes." + RuleConfig.material(key), true)
        : mod.config.bool("tree-cutter.hand", false);
  }

  private List<BlockPos> logs(ServerLevel level, BlockPos first, Block log, int maximum) {
    ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    Set<BlockPos> seen = new HashSet<>();
    List<BlockPos> result = new ArrayList<>();
    queue.add(first);
    seen.add(first);
    while (!queue.isEmpty() && result.size() <= maximum) {
      BlockPos current = queue.removeFirst();
      result.add(current);
      for (int x = -1; x <= 1; x++)
        for (int y = -1; y <= 1; y++)
          for (int z = -1; z <= 1; z++) {
            BlockPos next = current.offset(x, y, z);
            if (level.hasChunkAt(next)
                && seen.add(next)
                && level.getBlockState(next).is(log)
                && !mod.placed(level, next)) queue.addLast(next);
          }
    }
    return result;
  }

  private static List<BlockPos> leaves(ServerLevel level, List<BlockPos> logs) {
    Set<BlockPos> seen = new HashSet<>();
    ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    for (BlockPos log : logs)
      for (Direction direction : Direction.values()) {
        BlockPos next = log.relative(direction);
        if (level.hasChunkAt(next)) {
          BlockState state = level.getBlockState(next);
          if (naturalLeaf(state) && state.getValue(LeavesBlock.DISTANCE) == 1 && seen.add(next))
            queue.addLast(next);
        }
      }
    while (!queue.isEmpty() && seen.size() < 8192) {
      BlockPos current = queue.removeFirst();
      int distance = level.getBlockState(current).getValue(LeavesBlock.DISTANCE);
      if (distance >= 6) continue;
      for (Direction direction : Direction.values()) {
        BlockPos next = current.relative(direction);
        if (!level.hasChunkAt(next)) continue;
        BlockState state = level.getBlockState(next);
        if (naturalLeaf(state)
            && state.getValue(LeavesBlock.DISTANCE) == distance + 1
            && seen.add(next)) queue.addLast(next);
      }
    }
    return List.copyOf(seen);
  }

  private static boolean naturalLeaf(BlockState state) {
    return state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT);
  }

  public void tick() {
    for (UUID id : new ArrayList<>(active.keySet())) {
      Harvest harvest = active.get(id);
      ServerPlayer player = harvest.player;
      if (player.isRemoved()
          || !player.isAlive()
          || player.level() != harvest.level
          || player.gameMode.getGameModeForPlayer() != net.minecraft.world.level.GameType.SURVIVAL
          || !mod.config.bool("tree-cutter.enabled", true)
          || player.getMainHandItem().getItem() != harvest.tool) {
        finish(id, player);
        continue;
      }
      int budget = mod.config.integer("tree-cutter.blocks-per-tick", 12, 1, 64);
      for (int index = 0;
          index < budget && (!harvest.logs.isEmpty() || !harvest.leaves.isEmpty());
          index++) {
        boolean log = !harvest.logs.isEmpty();
        BlockPos pos = log ? harvest.logs.removeFirst() : harvest.leaves.removeFirst();
        if (!harvest.level.hasChunkAt(pos)
            || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                > 64 * 64) {
          finish(id, player);
          break;
        }
        BlockState state = harvest.level.getBlockState(pos);
        if (log ? (!state.is(harvest.log) || mod.placed(harvest.level, pos)) : !naturalLeaf(state))
          continue;
        internal.add(id);
        boolean broken;
        try {
          broken = player.gameMode.destroyBlock(pos);
        } finally {
          internal.remove(id);
        }
        if (!broken || player.getMainHandItem().getItem() != harvest.tool) {
          finish(id, player);
          break;
        }
        if (log) harvest.cut++;
      }
      if (active.containsKey(id) && harvest.logs.isEmpty() && harvest.leaves.isEmpty())
        finish(id, player);
    }
    ready.clear();
  }

  private void finish(UUID id, ServerPlayer player) {
    Harvest removed = active.remove(id);
    if (removed != null && removed.cut > 1 && player != null)
      NativeAccess.overlay(player, Component.literal("§6Timber • " + removed.cut + " logs"));
  }

  public void clear() {
    active.clear();
    progress.clear();
    ready.clear();
    internal.clear();
  }

  private record Tree(
      BlockPos first, Block log, Item tool, List<BlockPos> logs, List<BlockPos> leaves) {}

  private record Progress(ServerLevel level, BlockPos pos, int count, long at) {}

  private static final class Harvest {
    final ServerPlayer player;
    final ServerLevel level;
    final Block log;
    final Item tool;
    final ArrayDeque<BlockPos> logs, leaves;
    int cut = 1;

    Harvest(
        ServerPlayer player,
        ServerLevel level,
        Block log,
        Item tool,
        ArrayDeque<BlockPos> logs,
        ArrayDeque<BlockPos> leaves) {
      this.player = player;
      this.level = level;
      this.log = log;
      this.tool = tool;
      this.logs = logs;
      this.leaves = leaves;
    }
  }
}
