package fr.isaac.cutcleanreborn.fabric.mixin;

import fr.isaac.cutcleanreborn.fabric.CutCleanReborn;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
abstract class PlacedBlockMixin {
  @Inject(method = "place", at = @At("RETURN"))
  private void ccr$placed(
      BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
    if (!CutCleanReborn.ready()
        || context.getLevel().isClientSide
        || context.getPlayer() == null
        || !cir.getReturnValue().consumesAction()) return;
    var state = context.getLevel().getBlockState(context.getClickedPos());
    if (state.is(BlockTags.LOGS))
      CutCleanReborn.instance()
          .origins()
          .add(context.getLevel(), context.getClickedPos(), "timber");
    if (state.is(Blocks.SUGAR_CANE))
      CutCleanReborn.instance().origins().add(context.getLevel(), context.getClickedPos(), "cane");
  }
}
