package fr.isaac.cutcleanreborn.feature;

import fr.isaac.cutcleanreborn.config.CutCleanConfigManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public final class TreeFellerManager {
	private static final int MAX_LOGS_PER_TREE = 64;
	private static final int MAX_LEAVES_PER_TREE = 256;
	private static final int MAX_TREE_HEIGHT = 32;
	private static final int MAX_TREE_RADIUS = 8;
	private static final int LEAF_RADIUS_XZ = 2;
	private static final int LEAF_RADIUS_DOWN = 1;
	private static final int LEAF_RADIUS_UP = 4;
	private static boolean bonusAppleDropsEnabled = true;
	private static float extraAppleDropChance = 0.04F;
	private static boolean breakingTree = false;

	private TreeFellerManager() {
	}

	public static void reloadFromConfig() {
		bonusAppleDropsEnabled = CutCleanConfigManager.getConfig().treeFeller.bonusAppleDropsEnabled;
		extraAppleDropChance = (float) Math.max(0.0, Math.min(1.0, CutCleanConfigManager.getConfig().treeFeller.extraAppleDropChance));
	}

	public static void onBlockBroken(ServerWorld world, BlockPos brokenPos, BlockState brokenState, PlayerEntity player) {
		if (breakingTree || !CutCleanConfigManager.getConfig().features.breakWholeTreeFromOneLog) {
			return;
		}

		if (!(player instanceof ServerPlayerEntity serverPlayer) || serverPlayer.interactionManager.getGameMode() != GameMode.SURVIVAL) {
			return;
		}

		if (!player.getMainHandStack().isIn(ItemTags.AXES)) {
			return;
		}

		if (!isLog(brokenState)) {
			return;
		}

		String woodFamily = getWoodFamily(brokenState);
		if (woodFamily == null) {
			return;
		}

		Set<BlockPos> logs = collectConnectedLogs(world, brokenPos, woodFamily);
		if (logs.isEmpty()) {
			return;
		}

		Set<BlockPos> leaves = collectNearbyLeaves(world, logs);
		if (leaves.size() < 4) {
			return;
		}

		breakingTree = true;
		try {
			for (BlockPos logPos : logs) {
				BlockState state = world.getBlockState(logPos);
				if (isLog(state)) {
					world.breakBlock(logPos, true, player);
				}
			}

			for (BlockPos leafPos : leaves) {
				BlockState state = world.getBlockState(leafPos);
				if (!state.isIn(BlockTags.LEAVES)) {
					continue;
				}

				world.breakBlock(leafPos, true, player);
				if (bonusAppleDropsEnabled && world.random.nextFloat() < extraAppleDropChance) {
					Block.dropStack(world, leafPos, new ItemStack(Items.APPLE));
				}
			}
		} finally {
			breakingTree = false;
		}
	}

	private static Set<BlockPos> collectConnectedLogs(ServerWorld world, BlockPos origin, String woodFamily) {
		Set<BlockPos> visited = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();

		enqueueOrthogonalLogNeighbors(world, origin, origin, woodFamily, queue, visited);

		while (!queue.isEmpty() && visited.size() < MAX_LOGS_PER_TREE) {
			BlockPos current = queue.poll();
			if (!visited.add(current)) {
				continue;
			}

			enqueueOrthogonalLogNeighbors(world, origin, current, woodFamily, queue, visited);
		}

		return visited;
	}

	private static void enqueueOrthogonalLogNeighbors(ServerWorld world, BlockPos origin, BlockPos pos, String woodFamily, ArrayDeque<BlockPos> queue, Set<BlockPos> visited) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) {
						continue;
					}

					BlockPos neighbor = pos.add(dx, dy, dz);
					if (Math.abs(neighbor.getY() - origin.getY()) > MAX_TREE_HEIGHT
						|| Math.abs(neighbor.getX() - origin.getX()) > MAX_TREE_RADIUS
						|| Math.abs(neighbor.getZ() - origin.getZ()) > MAX_TREE_RADIUS) {
						continue;
					}
					if (visited.contains(neighbor)) {
						continue;
					}
					if (isMatchingLog(world.getBlockState(neighbor), woodFamily)) {
						queue.add(neighbor.toImmutable());
					}
				}
			}
		}
	}
	private static Set<BlockPos> collectNearbyLeaves(ServerWorld world, Set<BlockPos> logs) {
		Set<BlockPos> leaves = new HashSet<>();
		for (BlockPos logPos : logs) {
			for (int dx = -LEAF_RADIUS_XZ; dx <= LEAF_RADIUS_XZ; dx++) {
				for (int dz = -LEAF_RADIUS_XZ; dz <= LEAF_RADIUS_XZ; dz++) {
					for (int dy = -LEAF_RADIUS_DOWN; dy <= LEAF_RADIUS_UP; dy++) {
						if (leaves.size() >= MAX_LEAVES_PER_TREE) {
							return leaves;
						}

						BlockPos candidate = logPos.add(dx, dy, dz);
						if (world.getBlockState(candidate).isIn(BlockTags.LEAVES)) {
							leaves.add(candidate.toImmutable());
						}
					}
				}
			}
		}
		return leaves;
	}

	private static boolean isMatchingLog(BlockState state, String woodFamily) {
		return isLog(state) && woodFamily.equals(getWoodFamily(state));
	}

	private static boolean isLog(BlockState state) {
		return state.isIn(BlockTags.LOGS);
	}

	private static String getWoodFamily(BlockState state) {
		Identifier id = Registries.BLOCK.getId(state.getBlock());
		if (id == null) {
			return null;
		}

		String path = id.getPath();
		if (path.startsWith("stripped_")) {
			path = path.substring("stripped_".length());
		}

		String[] suffixes = {"_log", "_wood", "_stem", "_hyphae"};
		for (String suffix : suffixes) {
			if (path.endsWith(suffix) && path.length() > suffix.length()) {
				return id.getNamespace() + ":" + path.substring(0, path.length() - suffix.length());
			}
		}

		return id.getNamespace() + ":" + path;
	}
}
