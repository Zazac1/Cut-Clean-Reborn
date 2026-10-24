package fr.isaac.cutcleanreborn.fabric.mixin;

import fr.isaac.cutcleanreborn.fabric.CutCleanReborn;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
abstract class PlacedBlockMixin {
  @Inject(method = "place", at = @At("RETURN"))
  private void ccr$placed(
      BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
    if (CutCleanReborn.runtime == null
        || !(context.getLevel() instanceof ServerLevel level)
        || context.getPlayer() == null
        || !cir.getReturnValue().consumesAction()) return;
    CutCleanReborn.runtime.mark(
        level, context.getClickedPos(), level.getBlockState(context.getClickedPos()));
  }
}
