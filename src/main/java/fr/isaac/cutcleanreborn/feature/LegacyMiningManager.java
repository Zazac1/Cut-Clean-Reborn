package fr.isaac.cutcleanreborn.feature;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import fr.isaac.cutcleanreborn.config.CutCleanConfigManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LegacyMiningManager {
	private static final Gson GSON = new Gson();
	private static final String WORLD_FILE_NAME = "cutcleanreborn-world.json";

	private static boolean configDefaultEnabled = false;
	private static Boolean pendingNewWorldOverride = null;
	private static int cycleTicks = 40;
	private static int attemptsPerPlayer = 6;
	private static int minY = 24;
	private static int maxY = 112;
	private static float replacementChance = 0.35F;
	private static int seedSearchRadius = 4;
	private static float existingVeinBoostChance = 0.7F;
	private static int existingVeinExtraMin = 2;
	private static int existingVeinExtraMax = 5;
	private static int newVeinMinSize = 3;
	private static int newVeinMaxSize = 7;
	private static boolean preferNearCaves = true;
	private static long tickCounter = 0L;
	private static Path cachedWorldFile = null;
	private static boolean cachedEnabled = false;
	private static boolean worldStateLoaded = false;
	private static final int EXPOSED_VEIN_EXTRA_MIN = 2;
	private static final int EXPOSED_VEIN_EXTRA_MAX = 4;
	private static final int LEGACY_DIAMOND_MIN_Y = 8;
	private static final int LEGACY_DIAMOND_MAX_Y = 20;
	private static final int[][] DIRECTIONS = new int[][] {
		{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
	};

	private LegacyMiningManager() {
	}

	public static void reloadFromConfig() {
		configDefaultEnabled = CutCleanConfigManager.getConfig().features.legacyPre118Mining;
		cycleTicks = Math.max(1, CutCleanConfigManager.getConfig().legacyMining.cycleTicks);
		attemptsPerPlayer = Math.max(1, CutCleanConfigManager.getConfig().legacyMining.attemptsPerPlayer);
		minY = CutCleanConfigManager.getConfig().legacyMining.minY;
		maxY = Math.max(minY, CutCleanConfigManager.getConfig().legacyMining.maxY);
		replacementChance = (float) Math.max(0.0, Math.min(1.0, CutCleanConfigManager.getConfig().legacyMining.replacementChance));
		seedSearchRadius = Math.max(1, Math.min(12, CutCleanConfigManager.getConfig().legacyMining.seedSearchRadius));
		existingVeinBoostChance = (float) Math.max(0.0, Math.min(1.0, CutCleanConfigManager.getConfig().legacyMining.existingVeinBoostChance));
		existingVeinExtraMin = Math.max(1, CutCleanConfigManager.getConfig().legacyMining.existingVeinExtraMin);
		existingVeinExtraMax = Math.max(existingVeinExtraMin, CutCleanConfigManager.getConfig().legacyMining.existingVeinExtraMax);
		newVeinMinSize = Math.max(1, CutCleanConfigManager.getConfig().legacyMining.newVeinMinSize);
		newVeinMaxSize = Math.max(newVeinMinSize, CutCleanConfigManager.getConfig().legacyMining.newVeinMaxSize);
		preferNearCaves = CutCleanConfigManager.getConfig().legacyMining.preferNearCaves;
		tickCounter = 0L;
		cachedWorldFile = null;
		worldStateLoaded = false;
	}

	public static boolean getNewWorldDefault() {
		return pendingNewWorldOverride != null ? pendingNewWorldOverride : configDefaultEnabled;
	}

	public static void clearPendingNewWorldOverride() {
		pendingNewWorldOverride = null;
	}

	public static void setPendingNewWorldOverride(boolean enabled) {
		pendingNewWorldOverride = enabled;
	}

	public static void applyPendingOverrideIfAny(MinecraftServer server) {
		if (pendingNewWorldOverride == null || !server.isSingleplayer()) {
			return;
		}

		setEnabledForServer(server, pendingNewWorldOverride);
		pendingNewWorldOverride = null;
	}

	public static void onServerTick(MinecraftServer server) {
		if (!isEnabledForServer(server)) {
			return;
		}

		tickCounter++;
		if (tickCounter % cycleTicks != 0L) {
			return;
		}

		for (ServerWorld world : server.getWorlds()) {
			for (var player : world.getPlayers()) {
				BlockPos playerPos = player.getBlockPos();
				for (int i = 0; i < attemptsPerPlayer; i++) {
					tryGenerateOreVein(world, playerPos);
				}
			}
		}
	}

	private static void tryGenerateOreVein(ServerWorld world, BlockPos playerPos) {
		if (world.random.nextFloat() > replacementChance) {
			return;
		}

		BlockPos target = randomTarget(world, playerPos);
		boolean targetExposed = isExposedToAir(world, target);
		BlockPos seedOrePos = findNearbyLegacyOre(world, target, seedSearchRadius);

		if (seedOrePos != null && world.random.nextFloat() < existingVeinBoostChance) {
			BlockState oreState = world.getBlockState(seedOrePos);
			int wanted = world.random.nextBetween(existingVeinExtraMin, existingVeinExtraMax);
			if (isExposedToAir(world, seedOrePos)) {
				wanted += world.random.nextBetween(EXPOSED_VEIN_EXTRA_MIN, EXPOSED_VEIN_EXTRA_MAX);
			}
			growConnectedVein(world, seedOrePos, oreState, wanted);
			return;
		}

		BlockPos spawnPos = relocateForNewVein(world, target, seedSearchRadius);
		if (spawnPos == null) {
			return;
		}

		boolean exposed = targetExposed || isExposedToAir(world, spawnPos);
		BlockState oreState = selectLegacyOre(world, spawnPos.getY(), exposed);
		if (oreState == null) {
			return;
		}

		int wanted = world.random.nextBetween(newVeinMinSize, newVeinMaxSize);
		if (isExposedToAir(world, spawnPos)) {
			wanted += 1;
		}
		createNewConnectedVein(world, spawnPos, oreState, wanted);
	}

	public static boolean isEnabledForServer(MinecraftServer server) {
		Path worldFile = resolveWorldFile(server);
		if (cachedWorldFile == null || !cachedWorldFile.equals(worldFile)) {
			cachedWorldFile = worldFile;
			worldStateLoaded = false;
		}

		if (!worldStateLoaded) {
			cachedEnabled = readOrCreateWorldSetting(worldFile);
			worldStateLoaded = true;
		}

		return cachedEnabled;
	}

	public static void setEnabledForServer(MinecraftServer server, boolean enabled) {
		Path worldFile = resolveWorldFile(server);
		cachedWorldFile = worldFile;
		cachedEnabled = enabled;
		worldStateLoaded = true;
		writeWorldSetting(worldFile, enabled);
	}

	private static Path resolveWorldFile(MinecraftServer server) {
		return server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve(WORLD_FILE_NAME);
	}

	private static boolean readOrCreateWorldSetting(Path worldFile) {
		if (Files.exists(worldFile)) {
			try {
				String raw = Files.readString(worldFile);
				JsonObject json = GSON.fromJson(raw, JsonObject.class);
				if (json != null && json.has("legacyPre118MiningEnabled")) {
					return json.get("legacyPre118MiningEnabled").getAsBoolean();
				}
			} catch (Exception ignored) {
			}
		}

		writeWorldSetting(worldFile, configDefaultEnabled);
		return configDefaultEnabled;
	}

	private static void writeWorldSetting(Path worldFile, boolean enabled) {
		try {
			Files.createDirectories(worldFile.getParent());
			JsonObject json = new JsonObject();
			json.addProperty("legacyPre118MiningEnabled", enabled);
			Files.writeString(worldFile, GSON.toJson(json));
		} catch (IOException ignored) {
		}
	}

	private static BlockPos randomTarget(ServerWorld world, BlockPos origin) {
		int x = origin.getX() + world.random.nextBetween(-32, 32);
		int z = origin.getZ() + world.random.nextBetween(-32, 32);
		int worldMinY = world.getBottomY();
		int worldTopY = world.getTopYInclusive();
		int yMin = Math.max(worldMinY, Math.min(minY, -24));
		int yMax = Math.min(worldTopY, maxY);
		int y;
		if (yMin >= yMax) {
			y = yMin;
		} else {
			int range = yMax - yMin + 1;
			double biased = Math.pow(world.random.nextDouble(), 2.2D);
			y = yMin + Math.min(range - 1, (int) Math.floor(biased * range));
		}
		return new BlockPos(x, y, z);
	}

	private static boolean isReplaceableStone(BlockState state) {
		return state.isIn(BlockTags.BASE_STONE_OVERWORLD);
	}

	private static boolean isLegacyOre(BlockState state) {
		return state.isOf(Blocks.COAL_ORE)
			|| state.isOf(Blocks.IRON_ORE)
			|| state.isOf(Blocks.COPPER_ORE)
			|| state.isOf(Blocks.GOLD_ORE)
			|| state.isOf(Blocks.DIAMOND_ORE)
			|| state.isOf(Blocks.DEEPSLATE_COAL_ORE)
			|| state.isOf(Blocks.DEEPSLATE_IRON_ORE)
			|| state.isOf(Blocks.DEEPSLATE_COPPER_ORE)
			|| state.isOf(Blocks.DEEPSLATE_GOLD_ORE)
			|| state.isOf(Blocks.DEEPSLATE_REDSTONE_ORE)
			|| state.isOf(Blocks.DEEPSLATE_DIAMOND_ORE)
			;
	}

	private static BlockPos findNearbyLegacyOre(ServerWorld world, BlockPos center, int radius) {
		BlockPos bestExposed = null;
		BlockPos fallback = null;
		for (int attempt = 0; attempt < 24; attempt++) {
			BlockPos candidate = center.add(
				world.random.nextBetween(-radius, radius),
				world.random.nextBetween(-radius, radius),
				world.random.nextBetween(-radius, radius)
			);
			if (isLegacyOre(world.getBlockState(candidate))) {
				if (fallback == null) {
					fallback = candidate;
				}
				if (isExposedToAir(world, candidate)) {
					bestExposed = candidate;
					break;
				}
			}
		}
		return bestExposed != null ? bestExposed : fallback;
	}

	private static BlockPos relocateForNewVein(ServerWorld world, BlockPos center, int radius) {
		for (int attempt = 0; attempt < 24; attempt++) {
			BlockPos candidate = center.add(
				world.random.nextBetween(-radius, radius),
				world.random.nextBetween(-radius / 2, radius / 2),
				world.random.nextBetween(-radius, radius)
			);

			if (!isReplaceableStone(world.getBlockState(candidate))) {
				continue;
			}

			if (preferNearCaves && !isNearAir(world, candidate) && world.random.nextFloat() < 0.65F) {
				continue;
			}

			return candidate;
		}

		return (!preferNearCaves && isReplaceableStone(world.getBlockState(center))) ? center : null;
	}

	private static boolean isNearAir(ServerWorld world, BlockPos pos) {
		for (int[] dir : DIRECTIONS) {
			BlockPos check = pos.add(dir[0], dir[1], dir[2]);
			if (world.getBlockState(check).isAir()) {
				return true;
			}
		}
		return false;
	}

	private static boolean isExposedToAir(ServerWorld world, BlockPos pos) {
		return isNearAir(world, pos);
	}

	private static void createNewConnectedVein(ServerWorld world, BlockPos start, BlockState ore, int wanted) {
		if (!isReplaceableStone(world.getBlockState(start))) {
			return;
		}

		world.setBlockState(start, ore, Block.NOTIFY_LISTENERS);
		growConnectedVein(world, start, ore, Math.max(0, wanted - 1));
	}

	private static void growConnectedVein(ServerWorld world, BlockPos seed, BlockState ore, int extraBlocksWanted) {
		if (extraBlocksWanted <= 0) {
			return;
		}

		List<BlockPos> frontier = new ArrayList<>();
		frontier.add(seed.toImmutable());

		int placed = 0;
		int guard = 0;
		while (!frontier.isEmpty() && placed < extraBlocksWanted && guard < 2048) {
			guard++;
			Collections.shuffle(frontier);
			BlockPos pivot = frontier.remove(frontier.size() - 1);

			List<BlockPos> neighbors = new ArrayList<>(6);
			for (int[] dir : DIRECTIONS) {
				neighbors.add(pivot.add(dir[0], dir[1], dir[2]));
			}
			Collections.shuffle(neighbors);

			for (BlockPos candidate : neighbors) {
				if (!isReplaceableStone(world.getBlockState(candidate))) {
					continue;
				}

				world.setBlockState(candidate, ore, Block.NOTIFY_LISTENERS);
				frontier.add(candidate.toImmutable());
				placed++;
				if (placed >= extraBlocksWanted) {
					break;
				}
			}
		}
	}

	private static BlockState selectLegacyOre(ServerWorld world, int y, boolean exposed) {
		int roll = world.random.nextInt(100);
		if (y >= 80) return roll < 50 ? Blocks.COAL_ORE.getDefaultState() : roll < 80 ? Blocks.IRON_ORE.getDefaultState() : Blocks.COPPER_ORE.getDefaultState();
		if (y >= 32) return roll < 35 ? Blocks.IRON_ORE.getDefaultState() : roll < 65 ? Blocks.COAL_ORE.getDefaultState() : roll < 90 ? Blocks.COPPER_ORE.getDefaultState() : Blocks.GOLD_ORE.getDefaultState();
		if (y >= LEGACY_DIAMOND_MIN_Y && y <= LEGACY_DIAMOND_MAX_Y) return roll < 28 ? Blocks.DIAMOND_ORE.getDefaultState() : roll < 52 ? Blocks.REDSTONE_ORE.getDefaultState() : roll < 72 ? Blocks.GOLD_ORE.getDefaultState() : roll < 88 ? Blocks.IRON_ORE.getDefaultState() : Blocks.COAL_ORE.getDefaultState();
		if (y >= 0) return roll < 25 ? Blocks.IRON_ORE.getDefaultState() : roll < 45 ? Blocks.COPPER_ORE.getDefaultState() : roll < 65 ? Blocks.COAL_ORE.getDefaultState() : roll < 82 ? Blocks.GOLD_ORE.getDefaultState() : Blocks.REDSTONE_ORE.getDefaultState();
		return roll < 40 ? Blocks.REDSTONE_ORE.getDefaultState() : roll < 70 ? Blocks.GOLD_ORE.getDefaultState() : roll < 90 ? Blocks.IRON_ORE.getDefaultState() : Blocks.DIAMOND_ORE.getDefaultState();
	}
}
