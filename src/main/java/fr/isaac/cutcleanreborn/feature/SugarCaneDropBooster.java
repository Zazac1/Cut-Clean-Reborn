package fr.isaac.cutcleanreborn.feature;

import fr.isaac.cutcleanreborn.config.CutCleanConfigManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class SugarCaneDropBooster {
	private static boolean enabled = true;
	private static boolean spreadEnabled = true;
	private static int minHeight = 3;
	private static int maxHeight = 4;
	private static float spreadChance = 0.2F;
	private static long tickCounter = 0L;

	private SugarCaneDropBooster() {
	}

	public static void reloadFromConfig() {
		enabled = CutCleanConfigManager.getConfig().features.boostSugarCaneDrops;
		spreadEnabled = CutCleanConfigManager.getConfig().features.spreadSugarCaneNearExisting;
		minHeight = Math.max(1, CutCleanConfigManager.getConfig().sugarCane.minNaturalHeight);
		maxHeight = Math.max(minHeight, CutCleanConfigManager.getConfig().sugarCane.maxNaturalHeight);
		spreadChance = (float) Math.max(0.0, Math.min(1.0, CutCleanConfigManager.getConfig().sugarCane.nearbySpreadChance));
		tickCounter = 0L;
	}

	public static void onServerTick(MinecraftServer server) {
		if (!enabled) {
			return;
		}

		tickCounter++;
		if (tickCounter % 20L != 0L) {
			return;
		}

		for (ServerWorld world : server.getWorlds()) {
			for (var player : world.getPlayers()) {
				BlockPos playerPos = player.getBlockPos();
				int misses = 0;
				for (int i = 0; i < 8; i++) {
					BlockPos sample = playerPos.add(world.random.nextBetween(-26, 26), world.random.nextBetween(-8, 8), world.random.nextBetween(-26, 26));
					BlockPos base = findSugarCaneBase(world, sample);
					if (base == null) {
						misses++;
						continue;
					}

					int targetHeight = world.random.nextBetween(minHeight, maxHeight);
					enforceColumnHeight(world, base, targetHeight);

					if (spreadEnabled && world.random.nextFloat() < spreadChance) {
						trySpreadNearby(world, base);
					}
				}

				// Only enhance existing columns; never seed cane in unrelated player areas.
			}
		}
}

	private static BlockPos findSugarCaneBase(ServerWorld world, BlockPos pos) {
		BlockPos.Mutable cursor = pos.mutableCopy();
		BlockState state = world.getBlockState(cursor);
		if (!state.isOf(Blocks.SUGAR_CANE)) {
			return null;
		}

		while (world.getBlockState(cursor.down()).isOf(Blocks.SUGAR_CANE)) {
			cursor.move(0, -1, 0);
		}
		return cursor.toImmutable();
	}

	private static void enforceColumnHeight(ServerWorld world, BlockPos base, int targetHeight) {
		if (!world.getBlockState(base).isOf(Blocks.SUGAR_CANE)) {
			return;
		}

		int currentHeight = 1;
		while (world.getBlockState(base.up(currentHeight)).isOf(Blocks.SUGAR_CANE)) {
			currentHeight++;
		}

		for (int y = currentHeight; y < targetHeight; y++) {
			BlockPos growPos = base.up(y);
			if (!world.getBlockState(growPos).isAir()) {
				break;
			}

			BlockState sugarState = Blocks.SUGAR_CANE.getDefaultState();
			if (!sugarState.canPlaceAt(world, growPos)) {
				break;
			}

			world.setBlockState(growPos, sugarState, Block.NOTIFY_LISTENERS);
		}

		if (currentHeight > maxHeight) {
			for (int y = maxHeight; y < currentHeight; y++) {
				BlockPos removePos = base.up(y);
				if (world.getBlockState(removePos).isOf(Blocks.SUGAR_CANE)) {
					world.breakBlock(removePos, false);
				}
			}
		}
	}

	private static void trySpreadNearby(ServerWorld world, BlockPos base) {
		for (int attempt = 0; attempt < 5; attempt++) {
			int dx = world.random.nextBetween(-2, 2);
			int dz = world.random.nextBetween(-2, 2);
			if (dx == 0 && dz == 0) {
				continue;
			}

			BlockPos targetBase = base.add(dx, 0, dz);
			if (!world.getBlockState(targetBase).isAir()) {
				continue;
			}

			BlockState sugarState = Blocks.SUGAR_CANE.getDefaultState();
			if (!sugarState.canPlaceAt(world, targetBase)) {
				continue;
			}

			world.setBlockState(targetBase, sugarState, Block.NOTIFY_LISTENERS);
			enforceColumnHeight(world, targetBase, world.random.nextBetween(Math.max(2, minHeight), maxHeight));
			return;
		}

	}

}
