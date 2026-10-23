package fr.isaac.cutcleanreborn.fabric.mixin;

import fr.isaac.cutcleanreborn.fabric.CutCleanReborn;
import fr.isaac.cutcleanreborn.fabric.InventoryRules;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingMenu.class)
abstract class CraftPreviewMixin {
  @Inject(method = "slotChangedCraftingGrid", at = @At("RETURN"))
  private static void ccr$planks(
      AbstractContainerMenu menu,
      Level level,
      Player player,
      CraftingContainer crafting,
      ResultContainer result,
      RecipeHolder<CraftingRecipe> recipe,
      CallbackInfo ci) {
    if (!CutCleanReborn.ready()
        || !(player instanceof ServerPlayer serverPlayer)
        || !CutCleanReborn.instance().inventory().cleanEnabled(serverPlayer)) return;
    ItemStack original = result.getItem(0);
    if (!InventoryRules.id(original.getItem()).endsWith("_planks")) return;
    ItemStack converted = CutCleanReborn.instance().inventory().clean(original);
    if (converted == original) return;
    result.setItem(0, converted);
    menu.setRemoteSlot(0, converted);
    serverPlayer.connection.send(
        new ClientboundContainerSetSlotPacket(
            menu.containerId, menu.incrementStateId(), 0, converted));
  }
}
