package fr.isaac.cutcleanreborn.feature;

import fr.isaac.cutcleanreborn.config.CutCleanConfigManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;

/**
 * Pre-1.18 (1.16/1.17-era) Overworld ore passes.
 *
 * <p>This runs during feature generation, once per newly generated chunk. It is
 * deliberately not a server-tick repair: existing chunks are never changed.
 * The counts, vertical ranges and configured vein sizes are the vanilla
 * pre-1.18 values. Copper is excluded because it did not exist in that
 * distribution.</p>
 */
public final class LegacyOreGeneration {
	private static final OrePass[] PASSES = {
		new OrePass(Blocks.COAL_ORE.getDefaultState(), 20, 17, 0, 127),
		new OrePass(Blocks.IRON_ORE.getDefaultState(), 20, 9, 0, 63),
		new OrePass(Blocks.GOLD_ORE.getDefaultState(), 2, 9, 0, 31),
		new OrePass(Blocks.REDSTONE_ORE.getDefaultState(), 8, 8, 0, 15),
		new OrePass(Blocks.DIAMOND_ORE.getDefaultState(), 1, 8, 0, 15),
		new OrePass(Blocks.LAPIS_ORE.getDefaultState(), 1, 7, 0, 31)
	};

	private LegacyOreGeneration() {
	}

	public static void generate(StructureWorldAccess world, Chunk chunk) {
		if (!CutCleanConfigManager.getConfig().features.legacyPre118Mining) {
			return;
		}

		ChunkPos chunkPos = chunk.getPos();
		Random random = Random.create(mixSeed(world.getSeed(), chunkPos.x, chunkPos.z));
		removeModernVanillaOres(chunk);
		for (OrePass pass : PASSES) {
			for (int attempt = 0; attempt < pass.attempts; attempt++) {
				BlockPos start = new BlockPos(chunkPos.getStartX() + random.nextInt(16), random.nextBetween(pass.minY, pass.maxY), chunkPos.getStartZ() + random.nextInt(16));
				placeVein(chunk, start, pass.state, pass.size, random);
			}
		}
	}

	private static void removeModernVanillaOres(Chunk chunk) {
		ChunkPos pos = chunk.getPos();
		BlockPos.Mutable mutable = new BlockPos.Mutable();
		for (int x = pos.getStartX(); x <= pos.getEndX(); x++) {
			for (int z = pos.getStartZ(); z <= pos.getEndZ(); z++) {
				for (int y = chunk.getBottomY(); y <= chunk.getTopYInclusive(); y++) {
					mutable.set(x, y, z);
					if (isVanillaOverworldOre(chunk.getBlockState(mutable))) {
						chunk.setBlockState(mutable, y < 0 ? Blocks.DEEPSLATE.getDefaultState() : Blocks.STONE.getDefaultState(), 0);
					}
				}
			}
		}
	}

	private static void placeVein(Chunk chunk, BlockPos start, BlockState ore, int size, Random random) {
		// Vanilla's classic OreFeature interpolates overlapping ellipsoids along a
		// short line. This is intentionally not a random walk: vein shape affects
		// both the apparent size and the chance of finding a vein while branch mining.
		double angle = random.nextDouble() * Math.PI;
		double halfLength = size / 8.0D;
		double startX = start.getX() + Math.sin(angle) * halfLength;
		double endX = start.getX() - Math.sin(angle) * halfLength;
		double startZ = start.getZ() + Math.cos(angle) * halfLength;
		double endZ = start.getZ() - Math.cos(angle) * halfLength;
		double startY = start.getY() + random.nextInt(3) - 2;
		double endY = start.getY() + random.nextInt(3) - 2;
		BlockPos.Mutable mutable = new BlockPos.Mutable();

		for (int step = 0; step < size; step++) {
			double progress = (double) step / size;
			double centerX = lerp(progress, startX, endX);
			double centerY = lerp(progress, startY, endY);
			double centerZ = lerp(progress, startZ, endZ);
			double radius = ((Math.sin(Math.PI * progress) + 1.0D) * random.nextDouble() * size / 16.0D + 1.0D) / 2.0D;
			int minX = (int) Math.floor(centerX - radius);
			int minY = (int) Math.floor(centerY - radius);
			int minZ = (int) Math.floor(centerZ - radius);
			int maxX = (int) Math.floor(centerX + radius);
			int maxY = (int) Math.floor(centerY + radius);
			int maxZ = (int) Math.floor(centerZ + radius);
			for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
				if (x < chunk.getPos().getStartX() || x > chunk.getPos().getEndX() || z < chunk.getPos().getStartZ() || z > chunk.getPos().getEndZ()) continue;
				double dx = (x + 0.5D - centerX) / radius;
				double dy = (y + 0.5D - centerY) / radius;
				double dz = (z + 0.5D - centerZ) / radius;
				if (dx * dx + dy * dy + dz * dz >= 1.0D) continue;
				mutable.set(x, y, z);
				if (chunk.getBlockState(mutable).isIn(BlockTags.BASE_STONE_OVERWORLD)) chunk.setBlockState(mutable, ore, 0);
			}
		}
	}

	private static double lerp(double delta, double start, double end) {
		return start + delta * (end - start);
	}

	private static boolean isVanillaOverworldOre(BlockState state) {
		return state.isOf(Blocks.COAL_ORE) || state.isOf(Blocks.DEEPSLATE_COAL_ORE)
			|| state.isOf(Blocks.IRON_ORE) || state.isOf(Blocks.DEEPSLATE_IRON_ORE)
			|| state.isOf(Blocks.COPPER_ORE) || state.isOf(Blocks.DEEPSLATE_COPPER_ORE)
			|| state.isOf(Blocks.GOLD_ORE) || state.isOf(Blocks.DEEPSLATE_GOLD_ORE)
			|| state.isOf(Blocks.REDSTONE_ORE) || state.isOf(Blocks.DEEPSLATE_REDSTONE_ORE)
			|| state.isOf(Blocks.DIAMOND_ORE) || state.isOf(Blocks.DEEPSLATE_DIAMOND_ORE)
			|| state.isOf(Blocks.LAPIS_ORE) || state.isOf(Blocks.DEEPSLATE_LAPIS_ORE)
			|| state.isOf(Blocks.EMERALD_ORE) || state.isOf(Blocks.DEEPSLATE_EMERALD_ORE);
	}

	private static long mixSeed(long seed, int x, int z) {
		long value = seed ^ ((long) x * 341873128712L) ^ ((long) z * 132897987541L);
		return value ^ value >>> 33;
	}

	private record OrePass(BlockState state, int attempts, int size, int minY, int maxY) {
	}
}
