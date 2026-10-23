package fr.isaac.cutcleanreborn.fabric.mixin;

import fr.isaac.cutcleanreborn.fabric.CutCleanReborn;
import fr.isaac.cutcleanreborn.fabric.InventoryRules;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
abstract class ContainerInteractionMixin {
  @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
  private void ccr$safeUniqueCraft(
      int slot, int button, ClickType type, Player player, CallbackInfo ci) {
    if (!CutCleanReborn.ready() || !(player instanceof ServerPlayer)) return;
    AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
    var mod = CutCleanReborn.instance();
    if (slot >= 0
        && slot < menu.slots.size()
        && menu.getSlot(slot) instanceof ResultSlot
        && menu.getSlot(slot).getItem().is(Items.MACE)
        && mod.settings().bool(InventoryRules.ROOT + ".enabled", false)
        && mod.settings().bool(InventoryRules.ROOT + ".unique-mace.enabled", false)
        && (type != ClickType.PICKUP || button < 0 || button > 1 || !menu.getCarried().isEmpty())) {
      player.displayClientMessage(
          Component.literal("§eCCR • Fabriquez la masse unique par clic normal, curseur vide."),
          true);
      menu.sendAllDataToRemote();
      ci.cancel();
    }
  }

  @Inject(method = "clicked", at = @At("RETURN"))
  private void ccr$correctAfterTransfer(
      int slot, int button, ClickType type, Player player, CallbackInfo ci) {
    if (CutCleanReborn.ready() && player instanceof ServerPlayer serverPlayer)
      CutCleanReborn.instance().inventory().schedule(serverPlayer);
  }
}
