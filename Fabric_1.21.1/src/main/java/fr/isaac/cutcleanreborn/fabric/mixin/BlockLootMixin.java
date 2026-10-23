package fr.isaac.cutcleanreborn.fabric.mixin;

import fr.isaac.cutcleanreborn.fabric.CutCleanReborn;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
abstract class BlockLootMixin {
  @Inject(
      method = {
        "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
        "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)V",
        "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V"
      },
      at = @At("HEAD"))
  private static void ccr$begin(CallbackInfo ci) {
    if (CutCleanReborn.ready()) CutCleanReborn.instance().loot().beginBlockDrops();
  }

  @Inject(
      method = {
        "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
        "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)V",
        "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V"
      },
      at = @At("RETURN"))
  private static void ccr$end(CallbackInfo ci) {
    if (CutCleanReborn.ready()) CutCleanReborn.instance().loot().endBlockDrops();
  }

  @Inject(
      method =
          "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;",
      at = @At("RETURN"),
      cancellable = true)
  private static void ccr$player(
      BlockState state,
      ServerLevel level,
      BlockPos pos,
      BlockEntity be,
      Entity entity,
      ItemStack tool,
      CallbackInfoReturnable<List<ItemStack>> cir) {
    if (CutCleanReborn.ready())
      cir.setReturnValue(
          CutCleanReborn.instance()
              .loot()
              .block(state, level, pos, be, entity, tool, cir.getReturnValue()));
  }

  @Inject(
      method =
          "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)Ljava/util/List;",
      at = @At("RETURN"),
      cancellable = true)
  private static void ccr$support(
      BlockState state,
      ServerLevel level,
      BlockPos pos,
      BlockEntity be,
      CallbackInfoReturnable<List<ItemStack>> cir) {
    if (CutCleanReborn.ready())
      cir.setReturnValue(
          CutCleanReborn.instance()
              .loot()
              .block(state, level, pos, be, null, ItemStack.EMPTY, cir.getReturnValue()));
  }
}
