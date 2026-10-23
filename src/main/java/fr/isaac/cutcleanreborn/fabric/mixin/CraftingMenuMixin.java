package fr.isaac.cutcleanreborn.fabric.mixin;

import fr.isaac.cutcleanreborn.fabric.CutCleanReborn;
import fr.isaac.cutcleanreborn.fabric.InventoryRules;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({CraftingMenu.class, InventoryMenu.class})
abstract class CraftingMenuMixin {
  @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
  private void ccr$normalCraft(Player player, int slot, CallbackInfoReturnable<ItemStack> cir) {
    if (CutCleanReborn.ready()
        && slot == 0
        && ((AbstractContainerMenu) (Object) this).getSlot(0).getItem().is(Items.MACE)
        && CutCleanReborn.instance().settings().bool(InventoryRules.ROOT + ".enabled", false)
        && CutCleanReborn.instance()
            .settings()
            .bool(InventoryRules.ROOT + ".unique-mace.enabled", false)) {
      player.displayClientMessage(
          Component.literal("§eCCR • Fabriquez la masse unique par clic normal, curseur vide."),
          true);
      cir.setReturnValue(ItemStack.EMPTY);
    }
  }
}
