package fr.isaac.cutcleanreborn.fabric;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

public final class RuleRuntime {
  public final MinecraftServer server;
  public final RuleConfig config;
  private final RuleConfig state;
  private final Set<String> origins = new HashSet<>();
  public final InventoryRules inventory;
  public final Timber timber;
  public final RuleMenus menus;
  public final LootRules loot;
  public final VanillaCatalog catalog;
  private final ArrayDeque<Runnable> next = new ArrayDeque<>();
  private final Set<java.util.UUID> corrections = new HashSet<>();
  private int ticks;
  private boolean dirty;

  public RuleRuntime(MinecraftServer server) {
    this.server = server;
    config =
        new RuleConfig(
            FabricLoader.getInstance().getConfigDir().resolve("cutcleanreborn/config.yml"),
            RuleRuntime.class.getResourceAsStream("/config.yml"));
    state =
        new RuleConfig(
            server.getWorldPath(LevelResource.ROOT).resolve("data/cutcleanreborn-state.yml"), null);
    String mace = InventoryRules.ROOT + ".unique-mace";
    Map<String, Object> worldMace = new java.util.LinkedHashMap<>();
    worldMace.put(
        mace + ".exists",
        state.bool(
            "unique-mace.exists",
            server.isDedicatedServer() && config.bool(mace + ".exists", false)));
    worldMace.put(
        mace + ".id",
        state.text(
            "unique-mace.id", server.isDedicatedServer() ? config.text(mace + ".id", "") : ""));
    config.batch(worldMace);
    config.onChange(this::persistMace);
    persistMace();
    Object stored = state.get("placed");
    if (stored instanceof java.util.List<?> list)
      for (Object value : list) origins.add(String.valueOf(value));
    inventory = new InventoryRules(this);
    timber = new Timber(this);
    menus = new RuleMenus(this);
    loot = new LootRules(this);
    catalog = new VanillaCatalog(this);
    CutCleanReborn.LOG.info(
        "CCR world state ready: {} tracked player-placed blocks", origins.size());
  }

  public void later(Runnable task) {
    next.add(task);
  }

  public void requestCorrection(net.minecraft.server.level.ServerPlayer player) {
    if (inventory == null
        || inventory.correcting()
        || player.connection == null
        || player.isRemoved()
        || !corrections.add(player.getUUID())) return;
    later(
        () -> {
          corrections.remove(player.getUUID());
          inventory.correct(player);
        });
  }

  public void tick() {
    int queued = next.size();
    for (int i = 0; i < queued; i++) {
      try {
        next.removeFirst().run();
      } catch (RuntimeException failure) {
        CutCleanReborn.LOG.error("CCR deferred action failed; server continues running", failure);
      }
    }
    timber.tick();
    if (++ticks % 20 == 0) server.getPlayerList().getPlayers().forEach(inventory::correct);
    if (ticks % 200 == 0) saveState();
  }

  public void reload() {
    config.load();
    persistMace();
    timber.clear();
    catalog.clear();
    menus.closeAll();
    server.getPlayerList().getPlayers().forEach(inventory::correct);
  }

  public void stop() {
    menus.closeAll();
    timber.clear();
    saveState();
  }

  private String key(ServerLevel level, BlockPos pos) {
    return level.dimension().toString() + "/" + pos.asLong();
  }

  public boolean placed(ServerLevel level, BlockPos pos) {
    return origins.contains(key(level, pos));
  }

  public void mark(ServerLevel level, BlockPos pos, BlockState block) {
    String key = key(level, pos);
    if (block.is(BlockTags.LOGS) || block.is(Blocks.SUGAR_CANE)) {
      dirty |= origins.add(key);
    } else dirty |= origins.remove(key);
  }

  public void remove(ServerLevel level, BlockPos pos) {
    dirty |= origins.remove(key(level, pos));
  }

  private void saveState() {
    if (dirty) {
      state.set("placed", origins.stream().sorted().toList());
      dirty = false;
    }
  }

  public void flushState() {
    saveState();
  }

  private void persistMace() {
    String mace = InventoryRules.ROOT + ".unique-mace";
    boolean exists = config.bool(mace + ".exists", false);
    String identity = config.text(mace + ".id", "");
    if (!state.has("unique-mace.exists")
        || exists != state.bool("unique-mace.exists", false)
        || !identity.equals(state.text("unique-mace.id", "")))
      state.batch(Map.of("unique-mace.exists", exists, "unique-mace.id", identity));
  }
}
