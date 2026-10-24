package fr.isaac.cutcleanreborn.fabric.mixin;

import fr.isaac.cutcleanreborn.fabric.CutCleanReborn;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cap the actual result before any transfer; charge/refund only once it is really taken. */
@Mixin(AnvilMenu.class)
abstract class AnvilRefundMixin {
  @Unique private Player ccr$owner;
  @Unique private Map<Holder<Enchantment>, Integer> ccr$previewRemoved = Map.of();
  @Unique private Item ccr$previewItem = Items.AIR;
  @Unique private Map<Holder<Enchantment>, Integer> ccr$takenRemoved = Map.of();
  @Unique private Item ccr$takenItem = Items.AIR;
  @Unique private int ccr$before;
  @Unique private int ccr$cost;

  @Inject(
      method =
          "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
      at = @At("RETURN"))
  private void ccr$owner(
      int id, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
    ccr$owner = inventory.player;
  }

  @Inject(method = "createResult", at = @At("RETURN"))
  private void ccr$prepareResult(CallbackInfo ci) {
    ccr$previewRemoved = Map.of();
    ccr$previewItem = Items.AIR;
    if (CutCleanReborn.runtime != null && ccr$owner instanceof ServerPlayer) {
      ItemStack result = ((AnvilMenu) (Object) this).getSlot(2).getItem();
      ccr$previewItem = result.getItem();
      ccr$previewRemoved = CutCleanReborn.runtime.inventory.enforceCaps(result);
    }
  }

  @Inject(method = "onTake", at = @At("HEAD"))
  private void ccr$before(Player player, ItemStack stack, CallbackInfo ci) {
    ccr$before = player.experienceLevel;
    ccr$cost = ((AnvilMenu) (Object) this).getCost();
    // Input consumption recalculates the result. Freeze this purchase separately first.
    ccr$takenRemoved = ccr$previewRemoved;
    ccr$takenItem = ccr$previewItem;
    ccr$previewRemoved = Map.of();
    ccr$previewItem = Items.AIR;
  }

  @Inject(method = "onTake", at = @At("RETURN"))
  private void ccr$after(Player player, ItemStack stack, CallbackInfo ci) {
    if (CutCleanReborn.runtime != null && player instanceof ServerPlayer serverPlayer)
      CutCleanReborn.runtime.inventory.refund(
          serverPlayer,
          ccr$takenItem,
          ccr$takenRemoved,
          Math.min(Math.max(0, ccr$cost), Math.max(0, ccr$before - player.experienceLevel)));
    ccr$takenRemoved = Map.of();
    ccr$takenItem = Items.AIR;
  }
}
