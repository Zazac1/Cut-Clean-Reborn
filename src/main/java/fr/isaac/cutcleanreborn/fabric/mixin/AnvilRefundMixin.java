package fr.isaac.cutcleanreborn.fabric.mixin;

import fr.isaac.cutcleanreborn.fabric.CutCleanReborn;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Correct the result before any transfer; a separate ledger survives input-triggered recomputation.
 */
@Mixin(AnvilMenu.class)
abstract class AnvilRefundMixin {
  @Unique private Map<Holder<Enchantment>, Integer> ccr$previewRemoved = Map.of();
  @Unique private Item ccr$previewType;
  @Unique private Map<Holder<Enchantment>, Integer> ccr$transactionRemoved = Map.of();
  @Unique private Item ccr$transactionType;
  @Unique private int ccr$before, ccr$cost;

  @Inject(method = "createResult", at = @At("RETURN"))
  private void ccr$prepareResult(CallbackInfo ci) {
    ccr$previewRemoved = Map.of();
    ccr$previewType = null;
    if (!CutCleanReborn.ready()) return;
    if (!(((AnvilMenu) (Object) this).getSlot(3).container instanceof Inventory inventory)
        || !(inventory.player instanceof ServerPlayer)) return;
    ItemStack result = ((AnvilMenu) (Object) this).getSlot(2).getItem();
    if (result.isEmpty()) return;
    ccr$previewType = result.getItem();
    ccr$previewRemoved = Map.copyOf(CutCleanReborn.instance().inventory().enforceCaps(result));
  }

  @Inject(method = "onTake", at = @At("HEAD"))
  private void ccr$before(Player player, ItemStack stack, CallbackInfo ci) {
    ccr$before = player.experienceLevel;
    ccr$cost = ((AnvilMenu) (Object) this).getCost();
    ccr$transactionType = ccr$previewType;
    ccr$transactionRemoved = ccr$previewRemoved;
    ccr$previewType = null;
    ccr$previewRemoved = Map.of();
  }

  @Inject(method = "onTake", at = @At("RETURN"))
  private void ccr$after(Player player, ItemStack stack, CallbackInfo ci) {
    if (CutCleanReborn.ready()
        && player instanceof ServerPlayer serverPlayer
        && ccr$transactionType != null) {
      int paid = Math.min(ccr$cost, Math.max(0, ccr$before - player.experienceLevel));
      CutCleanReborn.instance()
          .inventory()
          .refund(serverPlayer, ccr$transactionType, ccr$transactionRemoved, paid);
    }
    ccr$transactionRemoved = Map.of();
    ccr$transactionType = null;
  }
}
