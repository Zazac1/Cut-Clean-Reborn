package fr.isaac.cutcleanreborn.mixin;

import fr.isaac.cutcleanreborn.feature.LegacyOreGeneration;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
	@Inject(method = "generateFeatures", at = @At("TAIL"))
	private void cutcleanreborn$applyLegacyOreDistribution(StructureWorldAccess world, Chunk chunk, StructureAccessor structureAccessor, CallbackInfo ci) {
		if (world instanceof ServerWorld serverWorld && serverWorld.getRegistryKey() == ServerWorld.OVERWORLD) {
			LegacyOreGeneration.generate(world, chunk);
		}
	}
}
