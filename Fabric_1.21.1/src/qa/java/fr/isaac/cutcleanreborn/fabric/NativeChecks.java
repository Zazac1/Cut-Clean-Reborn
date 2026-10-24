package fr.isaac.cutcleanreborn.fabric;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.phys.AABB;

/** Verification mod is never included in the distributable CCR JAR. */
public final class NativeChecks implements ModInitializer {
  private final List<String> passed = new ArrayList<>(), failed = new ArrayList<>();
  private final ArrayDeque<Runnable> steps = new ArrayDeque<>();
  private final List<Packet<?>> inputPackets = new ArrayList<>();
  private boolean captureInputPackets;
  private CutCleanReborn mod;
  private Map<String, Object> original;
  private ServerPlayer player;
  private ServerPlayer secondAdmin;
  private int ticks;
  private BlockPos denied;

  @Override
  public void onInitialize() {
    if (Integer.getInteger("ccr.persistence.phase", 0) > 0) return;
    ServerTickEvents.END_SERVER_TICK.register(
        server -> {
          if (!Boolean.getBoolean("ccr.qa") || !CutCleanReborn.ready()) return;
          if (++ticks == 25) {
            mod = CutCleanReborn.instance();
            original = mod.settings().snapshot();
            try {
              initial();
            } catch (Throwable error) {
              failed.add("Uncaught initial verification: " + error);
              CutCleanReborn.LOGGER.error("CCR verification failed", error);
            }
          }
          if (ticks > 25 && !steps.isEmpty()) {
            try {
              steps.removeFirst().run();
            } catch (Throwable error) {
              failed.add("Native step: " + error);
              CutCleanReborn.LOGGER.error("CCR verification step failed", error);
            }
          }
          if (ticks > 30 && steps.isEmpty()) {
            finish();
            server.halt(false);
          }
        });
  }

  private void check(String label, boolean result) {
    (result ? passed : failed).add(label);
  }

  private void checkInputCostSync() {
    var input = (net.minecraft.world.inventory.AnvilMenu) player.containerMenu;
    inputPackets.clear();
    captureInputPackets = true;
    try {
      input.setItemName("not-a-number");
      input.setItemName("still-not-a-number");
    } finally {
      captureInputPackets = false;
    }
    long corrections = inputPackets.stream()
        .filter(packet -> packet instanceof ClientboundContainerSetDataPacket data
            && data.getContainerId() == input.containerId && data.getId() == 0 && data.getValue() == 0)
        .count();
    check("native text input sends a zero-cost correction for each edit", corrections == 2);
    check("native text input cost sync does not resend input contents",
        inputPackets.stream().noneMatch(packet -> packet instanceof ClientboundContainerSetContentPacket
            || packet instanceof ClientboundContainerSetSlotPacket slot
                && slot.getContainerId() == input.containerId && slot.getSlot() == 0));
  }

  private void initial() throws Exception {
    GameProfile profile =
        new GameProfile(UUID.fromString("e9a57af8-3bb6-4e70-9ccb-04164a8f7700"), "CCRVerification");
    player =
        new ServerPlayer(
            mod.server(), mod.server().overworld(), profile, ClientInformation.createDefault());
    player.connection =
        new ServerGamePacketListenerImpl(
            mod.server(),
            new Connection(PacketFlow.SERVERBOUND),
            player,
            CommonListenerCookie.createInitial(profile, false)) {
          @Override
          public void send(Packet<?> packet) {
            if (captureInputPackets) inputPackets.add(packet);
            super.send(packet);
          }
        };
    player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
    mod.server().getPlayerList().op(profile);
    player.setPos(0.5, 100, 0.5);
    mod.server().overworld().setChunkForced(-2, -2, true);
    check("fabric entrypoint and dedicated server ready", CutCleanReborn.ready());
    check(
        "standalone tool catalogue",
        InventoryRules.tool(Items.MACE)
            && InventoryRules.tool(Items.BOW)
            && InventoryRules.tool(Items.SHEARS)
            && InventoryRules.tool(Items.FLINT_AND_STEEL)
            && InventoryRules.armor(Items.TURTLE_HELMET));
    for (String command :
        List.of("ccr status", "ccr ping", "ccr help", "cutclean status", "crr status")) {
      mod.server()
          .getCommands()
          .performPrefixedCommand(mod.server().createCommandSourceStack(), command);
      check(
          "registered command " + command,
          mod.server()
              .getCommands()
              .getDispatcher()
              .parse(command, mod.server().createCommandSourceStack())
              .getReader()
              .getRemaining()
              .isEmpty());
    }
    clean();
    cleanScope();
    automatic();
    caps();
    configParity();
    loot();
    catalog();
    mace();
    maceDestruction();
    craftTransaction();
    anvilTransaction();
    enchantTransaction();
    craftPreview();
    origins();
    timber();
    menus();
    auditMenus();
    concurrentRules();
    lifecycleHooks();
    canePhysics();
  }

  private void clean() {
    ItemStack input = new ItemStack(Items.BIRCH_LOG, 48), output = mod.inventory().clean(input);
    check("clean default logs/count", output.is(Items.OAK_LOG) && output.getCount() == 48);
    ItemStack custom = input.copy();
    custom.set(
        DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Custom block"));
    check("clean named preserved", mod.inventory().clean(custom) == custom);
    mod.settings().set("clean-stacks.wood.BIRCH_LOG", "minecraft:spruce_log");
    mod.settings().set("clean-stacks.wood.SPRUCE_LOG", "minecraft:birch_log");
    ItemStack a = mod.inventory().clean(new ItemStack(Items.BIRCH_LOG)),
        b = mod.inventory().clean(new ItemStack(Items.SPRUCE_LOG));
    check("clean cycle canonical", a.getItem() == b.getItem());
    check("clean cycle idempotent", mod.inventory().clean(a).getItem() == a.getItem());
    mod.inventory().setCleanTarget(Items.BIRCH_PLANKS, Items.SPRUCE_PLANKS);
    check(
        "clean target fixed point",
        mod.inventory().clean(new ItemStack(Items.SPRUCE_PLANKS)).is(Items.SPRUCE_PLANKS));
  }

  private void automatic() {
    var unbreaking = mod.inventory().enchantments().getHolderOrThrow(Enchantments.UNBREAKING);
    var efficiency = mod.inventory().enchantments().getHolderOrThrow(Enchantments.EFFICIENCY);
    mod.settings().set("automatic-enchantments.tools.DIAMOND_AXE.minecraft:unbreaking", 3);
    ItemStack stack = new ItemStack(Items.DIAMOND_AXE);
    stack.enchant(efficiency, 2);
    check(
        "automatic first application",
        mod.inventory().auto(stack)
            && EnchantmentHelper.getItemEnchantmentLevel(unbreaking, stack) == 3);
    String identity = InventoryRules.data(stack).getString("ccr:auto_enchant_id");
    check("automatic persistent UUID", !identity.isBlank());
    check("automatic exactly once", !mod.inventory().auto(stack));
    mod.settings().set("automatic-enchantments.tools.DIAMOND_AXE.minecraft:unbreaking", 1);
    mod.settings().set("automatic-enchantments.refresh-version", 1);
    check(
        "automatic refresh",
        mod.inventory().auto(stack)
            && EnchantmentHelper.getItemEnchantmentLevel(unbreaking, stack) == 1);
    check(
        "automatic original enchant preserved",
        EnchantmentHelper.getItemEnchantmentLevel(efficiency, stack) == 2);
    check(
        "automatic identity retained",
        identity.equals(InventoryRules.data(stack).getString("ccr:auto_enchant_id")));
    stack.enchant(efficiency, 5);
    mod.settings().set("automatic-enchantments.refresh-version", 2);
    check(
        "automatic player edit preserved",
        !mod.inventory().auto(stack)
            && EnchantmentHelper.getItemEnchantmentLevel(efficiency, stack) == 5);
    var fortune = mod.inventory().enchantments().getHolderOrThrow(Enchantments.FORTUNE);
    var silk = mod.inventory().enchantments().getHolderOrThrow(Enchantments.SILK_TOUCH);
    mod.settings().set("automatic-enchantments.tools.DIAMOND_PICKAXE.minecraft:fortune", 3);
    ItemStack conflict = new ItemStack(Items.DIAMOND_PICKAXE);
    conflict.enchant(silk, 1);
    mod.inventory().auto(conflict);
    check(
        "automatic conflicts respected",
        EnchantmentHelper.getItemEnchantmentLevel(fortune, conflict) == 0);
  }

  private void caps() {
    mod.settings().set(InventoryRules.ROOT + ".enabled", true);
    mod.settings()
        .set(
            InventoryRules.ROOT + ".enchantment-caps",
            List.of(
                Map.of(
                    "id",
                    "qa-cap",
                    "enabled",
                    true,
                    "target",
                    "SWORDS",
                    "enchantment",
                    "minecraft:sharpness",
                    "maximum",
                    2,
                    "refund-xp",
                    true,
                    "xp-per-removed-level",
                    5)));
    var sharpness = mod.inventory().enchantments().getHolderOrThrow(Enchantments.SHARPNESS);
    ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
    sword.enchant(sharpness, 5);
    var removed = mod.inventory().enforceCaps(sword);
    check(
        "enchantment cap correction",
        EnchantmentHelper.getItemEnchantmentLevel(sharpness, sword) == 2
            && removed.get(sharpness) == 3);
    player.experienceLevel = 10;
    check(
        "refund cannot mint XP without payment",
        mod.inventory().refund(player, sword, removed, 0) == 0 && player.experienceLevel == 10);
    check(
        "refund bounded by paid levels",
        mod.inventory().refund(player, sword, removed, 2) == 2 && player.experienceLevel == 12);
    mod.settings()
        .set(
            InventoryRules.ROOT + ".armor-rules",
            List.of(Map.of("id", "qa-armor", "enabled", true, "target", "DIAMOND", "maximum", 1)));
    player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
    player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
    mod.inventory().tick(player);
    check(
        "armor equipped limit",
        player.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET)
            && player.getItemBySlot(EquipmentSlot.CHEST).isEmpty());
    check(
        "armor overflow preserved",
        player.getInventory().contains(new ItemStack(Items.DIAMOND_CHESTPLATE)));
    mod.settings().set(InventoryRules.ROOT + ".armor-rules", List.of());
  }

  private void configParity() {
    Map<String, Object> saved = mod.settings().snapshot();
    try {
      var sharp = mod.inventory().enchantments().getHolderOrThrow(Enchantments.SHARPNESS);
      String path = InventoryRules.ROOT + ".enchantment-caps";
      mod.settings()
          .set(
              path,
              List.of(
                  Map.of(
                      "enabled",
                      true,
                      "target",
                      "",
                      "item",
                      "SWORDS",
                      "enchantment",
                      "SHARPNESS",
                      "maximum",
                      2,
                      "refund-xp",
                      true,
                      "xp-per-removed-level",
                      Integer.MAX_VALUE)));
      ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
      sword.enchant(sharp, 5);
      var removed = mod.inventory().enforceCaps(sword);
      check(
          "legacy empty cap target falls back and enchantment ID normalizes",
          EnchantmentHelper.getItemEnchantmentLevel(sharp, sword) == 2);
      player.experienceLevel = 10;
      check(
          "normalized legacy refund saturates safely to paid XP",
          mod.inventory().refund(player, sword, removed, 2) == 2 && player.experienceLevel == 12);
      mod.settings()
          .set(
              path,
              List.of(
                  Map.of(
                      "enabled",
                      true,
                      "target",
                      "SWORDS",
                      "enchantment",
                      "sharpness",
                      "maximum",
                      0)));
      check(
          "invalid zero cap is ignored instead of stripping enchants",
          mod.inventory().cap(Items.DIAMOND_SWORD, sharp) == -1);
      check(
          "Stuff TOOLS differs from automatic tool eligibility",
          InventoryRules.tool(Items.SHEARS)
              && !InventoryRules.matches(Items.SHEARS, "TOOLS")
              && InventoryRules.matches(Items.MACE, "TOOLS"));
      check(
          "cap exact DIAMOND is not the armor family",
          !InventoryRules.matches(Items.DIAMOND_HELMET, "DIAMOND")
              && InventoryRules.matches(Items.DIAMOND, "DIAMOND"));
      check(
          "armor family remains distinct from cap exact items",
          InventoryRules.matchesArmor(Items.DIAMOND_HELMET, " minecraft:diamond ")
              && !InventoryRules.matchesArmor(Items.IRON_HELMET, "DIAMOND"));
      check(
          "invalid exact-item list is rejected as a whole",
          !InventoryRules.matches(Items.DIAMOND_SWORD, "diamond_sword,invalid_item")
              && !InventoryRules.matchesArmor(Items.DIAMOND_HELMET, "diamond_helmet,diamond"));
      mod.settings().set("automatic-enchantments.tools.SHEARS", Map.of("unbreaking", 300));
      ItemStack shears = new ItemStack(Items.SHEARS);
      check(
          "invalid automatic level ignored without UUID",
          !mod.inventory().auto(shears)
              && !shears.isEnchanted()
              && !InventoryRules.data(shears).contains("ccr:auto_enchant_id"));
      mod.settings().set("automatic-enchantments.tools.SHEARS", Map.of("unbreaking", 0));
      check("zero automatic level ignored", !mod.inventory().auto(new ItemStack(Items.SHEARS)));
      mod.settings()
          .set(
              InventoryRules.ROOT + ".armor-rules",
              List.of(Map.of("enabled", true, "target", "", "material", "DIAMOND", "maximum", 0)));
      player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
      mod.inventory().tick(player);
      check(
          "legacy empty armor target falls back to material",
          player.getItemBySlot(EquipmentSlot.HEAD).isEmpty());
      mod.settings().set("clean-stacks.wood.CHERRY_LOG", "invalid_item");
      check(
          "invalid clean mapping falls back to Paper default",
          mod.inventory().clean(new ItemStack(Items.CHERRY_LOG)).is(Items.OAK_LOG));
      mod.settings()
          .set(
              path,
              List.of(
                  Map.of(
                      "enabled",
                      true,
                      "target",
                      "BOOK",
                      "enchantment",
                      "sharpness",
                      "maximum",
                      1)));
      ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
      book.enchant(sharp, 5);
      check(
          "stored book cap preserves original table BOOK target",
          mod.inventory().enforceCaps(book, Items.BOOK).get(sharp) == 4
              && EnchantmentHelper.getEnchantmentsForCrafting(book).getLevel(sharp) == 1);
    } finally {
      mod.settings().restore(saved);
    }
  }

  private void loot() {
    var level = mod.server().overworld();
    BlockPos pos = new BlockPos(0, 105, 0);
    level.getChunkAt(pos);
    level.setBlockAndUpdate(pos, Blocks.IRON_ORE.defaultBlockState());
    ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
    mod.settings().set("ore-drops.auto-smelt", true);
    mod.settings().set("ore-drops.iron_ore.xp-bonus", 2);
    int xpBefore =
        level
            .getEntitiesOfClass(
                net.minecraft.world.entity.ExperienceOrb.class, new AABB(pos).inflate(3))
            .size();
    var preview =
        Block.getDrops(Blocks.IRON_ORE.defaultBlockState(), level, pos, null, player, pick);
    check("ore preview has no CCR mutation", preview.stream().anyMatch(s -> s.is(Items.RAW_IRON)));
    check(
        "ore preview does not spawn XP",
        level
                .getEntitiesOfClass(
                    net.minecraft.world.entity.ExperienceOrb.class, new AABB(pos).inflate(3))
                .size()
            == xpBefore);
    Block.dropResources(Blocks.IRON_ORE.defaultBlockState(), level, pos, null, player, pick);
    var entities = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3));
    CutCleanReborn.LOGGER.info(
        "CCR ore diagnostic output={} xp={} auto-smelt={} creative={}",
        entities.stream().map(e -> e.getItem().toString()).toList(),
        level
            .getEntitiesOfClass(
                net.minecraft.world.entity.ExperienceOrb.class, new AABB(pos).inflate(3))
            .size(),
        mod.settings().bool("ore-drops.auto-smelt", false),
        player.isCreative());
    check(
        "native ore smelt output",
        entities.stream().anyMatch(e -> e.getItem().is(Items.IRON_INGOT)));
    check(
        "native ore smelt XP",
        level
                .getEntitiesOfClass(
                    net.minecraft.world.entity.ExperienceOrb.class, new AABB(pos).inflate(3))
                .size()
            > xpBefore);
    for (ItemEntity entity : entities) entity.discard();
    ItemStack silk = pick.copy();
    silk.enchant(mod.inventory().enchantments().getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
    Block.dropResources(Blocks.IRON_ORE.defaultBlockState(), level, pos, null, player, silk);
    check(
        "native silk touch untouched",
        level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).stream()
            .anyMatch(e -> e.getItem().is(Items.IRON_ORE)));
    for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)))
      entity.discard();
    mod.settings().set("better-sugar-cane.enabled", true);
    mod.settings().set("better-sugar-cane.player.minimum", 3);
    mod.settings().set("better-sugar-cane.player.maximum", 3);
    Block.dropResources(
        Blocks.SUGAR_CANE.defaultBlockState(), level, pos, null, player, ItemStack.EMPTY);
    check(
        "native sugar player final quantity",
        level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).stream()
            .anyMatch(e -> e.getItem().is(Items.SUGAR_CANE) && e.getItem().getCount() == 3));
    for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)))
      entity.discard();
    mod.origins().add(level, pos, "cane");
    Block.dropResources(
        Blocks.SUGAR_CANE.defaultBlockState(), level, pos, null, player, ItemStack.EMPTY);
    check(
        "native sugar placed exclusion",
        level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).stream()
            .allMatch(e -> !e.getItem().is(Items.SUGAR_CANE) || e.getItem().getCount() == 1));
    mod.settings().set("better-sugar-cane.support.minimum", 4);
    mod.settings().set("better-sugar-cane.support.maximum", 4);
    Block.dropResources(Blocks.SUGAR_CANE.defaultBlockState(), level, pos);
    check(
        "native sugar support final quantity",
        level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).stream()
            .anyMatch(e -> e.getItem().is(Items.SUGAR_CANE) && e.getItem().getCount() == 4));
    mod.settings()
        .set(
            "mobs.COW.vanilla-overrides",
            List.of(
                Map.of(
                    "source",
                    "minecraft:beef",
                    "item",
                    "minecraft:diamond",
                    "minimum",
                    2,
                    "maximum",
                    2,
                    "chance",
                    100.0)));
    mod.settings()
        .set(
            "mobs.COW.drops",
            List.of(
                Map.of("item", "minecraft:emerald", "minimum", 1, "maximum", 1, "chance", 100.0)));
    var cow = EntityType.COW.create(level);
    cow.setPos(pos.getX(), pos.getY(), pos.getZ());
    level.addFreshEntity(cow);
    cow.hurt(level.damageSources().playerAttack(player), 1000);
    var loot = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3));
    check(
        "native mob vanilla replacement",
        loot.stream().anyMatch(e -> e.getItem().is(Items.DIAMOND) && e.getItem().getCount() == 2)
            && loot.stream()
                .noneMatch(e -> e.getItem().is(Items.BEEF) || e.getItem().is(Items.COOKED_BEEF)));
    check("native mob extra loot", loot.stream().anyMatch(e -> e.getItem().is(Items.EMERALD)));
    for (ItemEntity entity : loot) entity.discard();
    mod.settings()
        .set(
            "mobs.COW.vanilla-overrides",
            List.of(Map.of("item", "beef", "minimum", 4, "maximum", 2, "chance", 100)));
    var invalidCow = EntityType.COW.create(level);
    invalidCow.setPos(pos.getX(), pos.getY(), pos.getZ());
    level.addFreshEntity(invalidCow);
    invalidCow.hurt(level.damageSources().playerAttack(player), 1000);
    check(
        "invalid vanilla mob override leaves real vanilla meat intact",
        level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).stream()
            .anyMatch(e -> e.getItem().is(Items.COOKED_BEEF)));
    for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)))
      entity.discard();
    mod.settings()
        .set(
            "ore-drops.iron_ore.vanilla-overrides",
            List.of(Map.of("item", "diamond", "minimum", 4, "maximum", 2, "chance", 100)));
    Block.dropResources(Blocks.IRON_ORE.defaultBlockState(), level, pos, null, player, pick);
    check(
        "invalid vanilla ore override leaves smelted vanilla intact",
        level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).stream()
            .anyMatch(e -> e.getItem().is(Items.IRON_INGOT)));
    for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)))
      entity.discard();
    player.gameMode.changeGameModeForPlayer(GameType.ADVENTURE);
    Block.dropResources(Blocks.IRON_ORE.defaultBlockState(), level, pos, null, player, pick);
    check(
        "Adventure ore is not unexpectedly auto smelted",
        level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).stream()
            .anyMatch(e -> e.getItem().is(Items.RAW_IRON)));
    for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)))
      entity.discard();
    Block.dropResources(
        Blocks.SUGAR_CANE.defaultBlockState(), level, pos, null, player, ItemStack.EMPTY);
    check(
        "Adventure cane does not receive survival bonus",
        level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).stream()
            .allMatch(e -> !e.getItem().is(Items.SUGAR_CANE) || e.getItem().getCount() == 1));
    player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
    boolean mobLoot =
        level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBLOOT);
    level
        .getGameRules()
        .getRule(net.minecraft.world.level.GameRules.RULE_DOMOBLOOT)
        .set(false, mod.server());
    int beforeBonus =
        level
            .getEntitiesOfClass(
                net.minecraft.world.entity.ExperienceOrb.class, new AABB(pos).inflate(3))
            .stream()
            .mapToInt(net.minecraft.world.entity.ExperienceOrb::getValue)
            .sum();
    mod.settings().set("mobs.COW.xp-bonus", 17);
    var noLootCow = EntityType.COW.create(level);
    noLootCow.setPos(pos.getX(), pos.getY(), pos.getZ());
    level.addFreshEntity(noLootCow);
    noLootCow.hurt(level.damageSources().playerAttack(player), 1000);
    int afterBonus =
        level
            .getEntitiesOfClass(
                net.minecraft.world.entity.ExperienceOrb.class, new AABB(pos).inflate(3))
            .stream()
            .mapToInt(net.minecraft.world.entity.ExperienceOrb::getValue)
            .sum();
    check(
        "configured mob XP remains independent of vanilla mob loot gamerule",
        afterBonus > beforeBonus);
    level
        .getGameRules()
        .getRule(net.minecraft.world.level.GameRules.RULE_DOMOBLOOT)
        .set(mobLoot, mod.server());
  }

  private void mace() {
    mod.settings().set(InventoryRules.ROOT + ".unique-mace.enabled", true);
    mod.settings().set(InventoryRules.ROOT + ".unique-mace.exists", false);
    ItemStack mace = new ItemStack(Items.MACE);
    mod.inventory().crafted(player, mace);
    check(
        "mace claim persisted",
        mod.inventory().maceBlocked()
            && !InventoryRules.data(mace).getString("ccr:unique_mace_id").isBlank());
    ItemStack duplicate = new ItemStack(Items.MACE);
    mod.inventory().crafted(player, duplicate);
    check("mace second craft rejected", duplicate.isEmpty());
    ItemEntity entity = new ItemEntity(player.level(), 0, 110, 0, mace.copy());
    player.level().addFreshEntity(entity);
    entity.discard();
    check("mace arbitrary removal/unload not released", mod.inventory().maceBlocked());
    mod.inventory().destroyed(mace);
    check("mace true destruction released", !mod.inventory().maceBlocked());
  }

  private void catalog() {
    int before = 0;
    for (var ignored : player.serverLevel().getAllEntities()) before++;
    var vanilla = mod.catalog().mob("COW", false);
    var looting = mod.catalog().mob("COW", true);
    var beef =
        vanilla.stream().filter(entry -> entry.get("item").equals("minecraft:beef")).findFirst();
    var extra =
        looting.stream().filter(entry -> entry.get("item").equals("minecraft:beef")).findFirst();
    check("native vanilla mob catalogue discovers meat", beef.isPresent());
    check(
        "native vanilla catalogue bypasses CCR overrides",
        vanilla.stream().noneMatch(entry -> entry.get("item").equals("minecraft:diamond")));
    check(
        "native vanilla catalogue base quantities",
        beef.isPresent()
            && Settings.number(beef.get(), "minimum", 0) == 1
            && Settings.number(beef.get(), "maximum", 0) == 3);
    check(
        "native Looting III preview samples actual table",
        extra.isPresent()
            && Settings.number(extra.get(), "maximum", 0)
                > Settings.number(beef.orElse(Map.of()), "maximum", 0));
    mod.catalog().mob("WITHER", false);
    int after = 0;
    for (var ignored : player.serverLevel().getAllEntities()) after++;
    check("native catalogue never spawns mobs or bosses", before == after);
    check(
        "ore copper base range",
        Settings.number(VanillaCatalog.ore("copper_ore", false), "minimum", 0) == 2
            && Settings.number(VanillaCatalog.ore("copper_ore", false), "maximum", 0) == 5);
    check(
        "ore lapis base range",
        Settings.number(VanillaCatalog.ore("deepslate_lapis_ore", false), "minimum", 0) == 4
            && Settings.number(VanillaCatalog.ore("deepslate_lapis_ore", false), "maximum", 0)
                == 9);
    check(
        "ore fortune redstone and debris ranges",
        Settings.number(VanillaCatalog.ore("redstone_ore", true), "maximum", 0) == 8
            && Settings.number(VanillaCatalog.ore("ancient_debris", true), "maximum", 0) == 1);
  }

  private void origins() throws Exception {
    var level = mod.server().overworld();
    BlockPos pos = new BlockPos(-16, -60, -1);
    mod.origins().add(level, pos, "timber");
    mod.origins().save();
    Origins reloaded =
        new Origins(
            mod.server()
                .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("data/cutcleanreborn-origins.json"));
    reloaded.load();
    check("placed marker restart persistence", reloaded.contains(level, pos, "timber"));
    check("placed marker negative coordinates", !reloaded.contains(level, pos.above(), "timber"));
    mod.origins().remove(level, pos, "timber");
  }

  private void craftTransaction() {
    var menu =
        new net.minecraft.world.inventory.CraftingMenu(
            99,
            player.getInventory(),
            net.minecraft.world.inventory.ContainerLevelAccess.create(
                player.level(), player.blockPosition()));
    player.containerMenu = menu;
    menu.getSlot(1).set(new ItemStack(Items.HEAVY_CORE));
    menu.getSlot(4).set(new ItemStack(Items.BREEZE_ROD));
    check("native mace recipe available", menu.getSlot(0).getItem().is(Items.MACE));
    menu.clicked(0, 0, ClickType.PICKUP, player);
    ItemStack mace = menu.getCarried();
    check(
        "native crafting transaction identity",
        mace.is(Items.MACE)
            && !InventoryRules.data(mace).getString("ccr:unique_mace_id").isBlank()
            && mod.inventory().maceBlocked());
    menu.setCarried(ItemStack.EMPTY);
    menu.getSlot(1).set(new ItemStack(Items.HEAVY_CORE));
    menu.getSlot(4).set(new ItemStack(Items.BREEZE_ROD));
    menu.clicked(0, 0, ClickType.PICKUP, player);
    check(
        "native duplicate craft blocked before ingredients",
        menu.getCarried().isEmpty() && menu.getSlot(1).getItem().is(Items.HEAVY_CORE));
    mod.inventory().destroyed(mace);
    menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
    check(
        "native shift unique craft rejected safely",
        !mod.inventory().maceBlocked() && menu.getSlot(1).getItem().is(Items.HEAVY_CORE));
    menu.clicked(0, 0, ClickType.SWAP, player);
    check(
        "native number-key unique craft rejected before ingredients",
        !mod.inventory().maceBlocked() && menu.getSlot(1).getItem().is(Items.HEAVY_CORE));
    menu.clicked(0, 0, ClickType.THROW, player);
    check(
        "native thrown unique craft rejected before ingredients",
        !mod.inventory().maceBlocked() && menu.getSlot(1).getItem().is(Items.HEAVY_CORE));
    menu.setCarried(new ItemStack(Items.DIRT));
    menu.clicked(0, 0, ClickType.PICKUP, player);
    check(
        "native unique craft requires empty cursor",
        !mod.inventory().maceBlocked() && menu.getSlot(1).getItem().is(Items.HEAVY_CORE));
    menu.setCarried(ItemStack.EMPTY);
    player.closeContainer();
  }

  private void maceDestruction() {
    ItemStack broken = new ItemStack(Items.MACE);
    mod.inventory().crafted(player, broken);
    broken.setDamageValue(broken.getMaxDamage() - 1);
    broken.hurtAndBreak(1, player.serverLevel(), player, item -> {});
    check(
        "native unique mace durability destruction releases",
        broken.isEmpty() && !mod.inventory().maceBlocked());
    ItemStack killed = new ItemStack(Items.MACE);
    mod.inventory().crafted(player, killed);
    ItemEntity entity = new ItemEntity(player.level(), 0, 110, 0, killed);
    player.level().addFreshEntity(entity);
    entity.kill();
    check("native unique mace killed entity releases", !mod.inventory().maceBlocked());
    ItemStack aged = new ItemStack(Items.MACE);
    mod.inventory().crafted(player, aged);
    ItemEntity old = new ItemEntity(player.level(), 0, 110, 0, aged);
    var data = old.saveWithoutId(new net.minecraft.nbt.CompoundTag());
    data.putShort("Age", (short) 6000);
    old.load(data);
    player.level().addFreshEntity(old);
    old.tick();
    check(
        "native unique mace timed despawn releases",
        old.isRemoved() && !mod.inventory().maceBlocked());
  }

  private void anvilTransaction() {
    player.experienceLevel = 40;
    var menu =
        new net.minecraft.world.inventory.AnvilMenu(
            100, player.getInventory(), net.minecraft.world.inventory.ContainerLevelAccess.NULL);
    player.containerMenu = menu;
    ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
    sword.setDamageValue(100);
    sword.enchant(mod.inventory().enchantments().getHolderOrThrow(Enchantments.SHARPNESS), 5);
    menu.getSlot(0).set(sword);
    menu.getSlot(1).set(new ItemStack(Items.DIAMOND));
    check(
        "native anvil output and paid repair cost",
        menu.getSlot(2).getItem().is(Items.DIAMOND_SWORD) && menu.getCost() > 0);
    int before = player.experienceLevel;
    menu.clicked(2, 0, ClickType.PICKUP, player);
    check(
        "native anvil cap before transfer",
        EnchantmentHelper.getItemEnchantmentLevel(
                mod.inventory().enchantments().getHolderOrThrow(Enchantments.SHARPNESS),
                menu.getCarried())
            == 2);
    check("native anvil refund paid transaction only", player.experienceLevel == before);
    menu.setCarried(ItemStack.EMPTY);
    player.closeContainer();
    player.experienceLevel = 40;
    var shift =
        new net.minecraft.world.inventory.AnvilMenu(
            103, player.getInventory(), net.minecraft.world.inventory.ContainerLevelAccess.NULL);
    player.containerMenu = shift;
    ItemStack shiftSword = new ItemStack(Items.DIAMOND_SWORD);
    shiftSword.setDamageValue(100);
    shiftSword.enchant(mod.inventory().enchantments().getHolderOrThrow(Enchantments.SHARPNESS), 5);
    shiftSword.set(
        DataComponents.CUSTOM_NAME,
        net.minecraft.network.chat.Component.literal("CCR shift fixture"));
    shift.getSlot(0).set(shiftSword);
    shift.getSlot(1).set(new ItemStack(Items.DIAMOND));
    shift.setItemName("CCR shift fixture");
    int shiftBefore = player.experienceLevel;
    check(
        "native shift anvil fixture retains its identifying name",
        shift.getSlot(2).getItem().getHoverName().getString().equals("CCR shift fixture"));
    shift.clicked(2, 0, ClickType.QUICK_MOVE, player);
    ItemStack received = ItemStack.EMPTY;
    for (ItemStack stack : player.getInventory().items)
      if (stack.getHoverName().getString().equals("CCR shift fixture")) received = stack;
    check(
        "native shift anvil cap applied before inventory transfer",
        !received.isEmpty()
            && EnchantmentHelper.getItemEnchantmentLevel(
                    mod.inventory().enchantments().getHolderOrThrow(Enchantments.SHARPNESS),
                    received)
                == 2);
    check(
        "native shift anvil refund exactly bounded by paid cost",
        player.experienceLevel == shiftBefore);
    player.closeContainer();
    List<ItemStack> savedInventory =
        player.getInventory().items.stream().map(ItemStack::copy).toList();
    for (int slot = 0; slot < 36; slot++)
      player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
    player.experienceLevel = 40;
    var full =
        new net.minecraft.world.inventory.AnvilMenu(
            104, player.getInventory(), net.minecraft.world.inventory.ContainerLevelAccess.NULL);
    player.containerMenu = full;
    ItemStack fullSword = new ItemStack(Items.DIAMOND_SWORD);
    fullSword.setDamageValue(100);
    fullSword.enchant(mod.inventory().enchantments().getHolderOrThrow(Enchantments.SHARPNESS), 5);
    full.getSlot(0).set(fullSword);
    full.getSlot(1).set(new ItemStack(Items.DIAMOND));
    full.clicked(2, 0, ClickType.QUICK_MOVE, player);
    check(
        "native full inventory anvil transfer pays and refunds nothing",
        player.experienceLevel == 40
            && !full.getSlot(2).getItem().isEmpty()
            && full.getSlot(0).getItem().is(Items.DIAMOND_SWORD));
    player.getInventory().setItem(8, ItemStack.EMPTY);
    full.clicked(2, 0, ClickType.QUICK_MOVE, player);
    check(
        "native retried anvil transfer refunds its one real transaction",
        player.experienceLevel == 40 && full.getSlot(2).getItem().isEmpty());
    full.clicked(2, 0, ClickType.QUICK_MOVE, player);
    check("native anvil refund ledger cannot be reused", player.experienceLevel == 40);
    player.closeContainer();
    for (int slot = 0; slot < 36; slot++)
      player.getInventory().setItem(slot, savedInventory.get(slot));
  }

  private void cleanScope() {
    List<ItemStack> saved = new ArrayList<>();
    for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
      saved.add(player.getInventory().getItem(slot).copy());
      player.getInventory().setItem(slot, ItemStack.EMPTY);
    }
    String preference = "clean-stacks.players." + player.getUUID();
    boolean before = mod.inventory().cleanEnabled(player);
    var woodBefore = mod.settings().section("clean-stacks.wood");
    mod.inventory().setCleanTarget(Items.BIRCH_LOG, Items.OAK_LOG);
    mod.settings().set(preference, false);
    player.getInventory().setItem(6, new ItemStack(Items.BIRCH_LOG, 17));
    player.getInventory().setItem(7, new ItemStack(Items.COBBLESTONE, 23));
    var source = mod.server().createCommandSourceStack().withEntity(player);
    mod.server().getCommands().performPrefixedCommand(source, "cleanstacks on");
    check("clean enable changes preference without converting inventory",
        mod.inventory().cleanEnabled(player)
            && player.getInventory().getItem(6).is(Items.BIRCH_LOG)
            && player.getInventory().getItem(6).getCount() == 17);
    mod.server().getCommands().performPrefixedCommand(source, "give @s minecraft:spruce_log 9");
    mod.inventory().tick(player);
    check("clean ignores given items and later inventory corrections",
        cleanCount(Items.SPRUCE_LOG) == 9
            && cleanCount(Items.BIRCH_LOG) == 17
            && cleanCount(Items.COBBLESTONE) == 23);
    var level = player.serverLevel();
    ItemEntity pickup = new ItemEntity(level, player.getX(), player.getY(), player.getZ(),
        new ItemStack(Items.ACACIA_LOG, 5));
    pickup.setNoPickUpDelay();
    level.addFreshEntity(pickup);
    pickup.playerTouch(player);
    mod.inventory().tick(player);
    check("clean ignores unrelated ground-item pickup",
        pickup.isRemoved() && cleanCount(Items.ACACIA_LOG) == 5);
    BlockPos pos = new BlockPos(34, 100, 34);
    level.getChunkAt(pos);
    player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_AXE));
    level.setBlockAndUpdate(pos, Blocks.BIRCH_LOG.defaultBlockState());
    check("clean enabled native block break succeeds", player.gameMode.destroyBlock(pos));
    var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
    check("clean converts only broken-block drops with their quantity",
        drops.stream().filter(e -> e.getItem().is(Items.OAK_LOG))
            .mapToInt(e -> e.getItem().getCount()).sum() == 1
            && drops.stream().noneMatch(e -> e.getItem().is(Items.BIRCH_LOG)));
    drops.forEach(ItemEntity::discard);
    mod.server().getCommands().performPrefixedCommand(source, "cleanstacks off");
    level.setBlockAndUpdate(pos, Blocks.BIRCH_LOG.defaultBlockState());
    check("clean disabled native block break succeeds", player.gameMode.destroyBlock(pos));
    drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
    check("clean disabled block drop retains vanilla material and count",
        drops.stream().filter(e -> e.getItem().is(Items.BIRCH_LOG))
            .mapToInt(e -> e.getItem().getCount()).sum() == 1
            && drops.stream().noneMatch(e -> e.getItem().is(Items.OAK_LOG)));
    drops.forEach(ItemEntity::discard);
    mod.settings().set(preference, before);
    mod.settings().set("clean-stacks.wood", woodBefore);
    for (int slot = 0; slot < saved.size(); slot++)
      player.getInventory().setItem(slot, saved.get(slot));
  }

  private int cleanCount(net.minecraft.world.item.Item item) {
    return java.util.stream.IntStream.range(0, player.getInventory().getContainerSize())
        .mapToObj(slot -> player.getInventory().getItem(slot))
        .filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
  }

  private void craftPreview() {
    mod.settings().set("clean-stacks.players." + player.getUUID(), true);
    mod.inventory().setCleanTarget(Items.BIRCH_PLANKS, Items.OAK_PLANKS);
    var menu =
        new net.minecraft.world.inventory.CraftingMenu(
            101,
            player.getInventory(),
            net.minecraft.world.inventory.ContainerLevelAccess.create(
                player.level(), player.blockPosition()));
    player.containerMenu = menu;
    menu.getSlot(1).set(new ItemStack(Items.BIRCH_LOG));
    check(
        "clean does not convert vanilla plank craft preview",
        menu.getSlot(0).getItem().is(Items.BIRCH_PLANKS)
            && menu.getSlot(0).getItem().getCount() == 4);
    menu.clicked(0, 0, ClickType.PICKUP, player);
    check(
        "clean leaves crafted material and quantity unchanged",
        menu.getCarried().is(Items.BIRCH_PLANKS) && menu.getCarried().getCount() == 4);
    menu.setCarried(ItemStack.EMPTY);
    player.closeContainer();
    mod.settings().set("clean-stacks.players." + player.getUUID(), false);
  }

  private void enchantTransaction() {
    var level = player.serverLevel();
    BlockPos pos = new BlockPos(26, 100, 0);
    level.getChunkAt(pos);
    level.setBlockAndUpdate(pos, Blocks.ENCHANTING_TABLE.defaultBlockState());
    for (BlockPos offset : net.minecraft.world.level.block.EnchantingTableBlock.BOOKSHELF_OFFSETS) {
      level.setBlockAndUpdate(
          pos.offset(offset.getX() / 2, offset.getY(), offset.getZ() / 2),
          Blocks.AIR.defaultBlockState());
      level.setBlockAndUpdate(pos.offset(offset), Blocks.BOOKSHELF.defaultBlockState());
    }
    List<Map<String, Object>> caps = new ArrayList<>();
    mod.inventory()
        .enchantments()
        .holders()
        .forEach(
            holder ->
                caps.add(
                    Map.of(
                        "enabled",
                        true,
                        "target",
                        "ALL",
                        "enchantment",
                        holder.key().location().toString(),
                        "maximum",
                        1,
                        "refund-xp",
                        true,
                        "xp-per-removed-level",
                        100)));
    mod.settings().set(InventoryRules.ROOT + ".enchantment-caps", caps);
    var menu =
        new net.minecraft.world.inventory.EnchantmentMenu(
            102,
            player.getInventory(),
            net.minecraft.world.inventory.ContainerLevelAccess.create(level, pos));
    player.containerMenu = menu;
    player.experienceLevel = 60;
    menu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));
    menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 64));
    int before = player.experienceLevel;
    boolean enchanted = menu.clickMenuButton(player, 2);
    ItemStack result = menu.getSlot(0).getItem();
    check("native enchanting table transaction executes", enchanted && result.isEnchanted());
    check(
        "native enchanting table caps before transfer",
        result
            .getOrDefault(
                DataComponents.ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY)
            .entrySet()
            .stream()
            .allMatch(entry -> entry.getIntValue() <= 1));
    check(
        "native enchanting refund never exceeds actual payment",
        player.experienceLevel <= before && player.experienceLevel >= before - 3);
    for (var cap : caps) {
      var copy = new java.util.LinkedHashMap<>(cap);
      copy.put("target", "BOOK");
      caps.set(caps.indexOf(cap), copy);
    }
    mod.settings().set(InventoryRules.ROOT + ".enchantment-caps", caps);
    // The visible clue guarantees a removed level, avoiding a probabilistic refund assertion.
    for (int attempt = 0; attempt < 256; attempt++) {
      menu.getSlot(0).set(new ItemStack(Items.BOOK));
      if (menu.levelClue[2] > 1) break;
      player.onEnchantmentPerformed(new ItemStack(Items.BOOK), 0);
      menu.getSlot(0).set(ItemStack.EMPTY);
    }
    check("native book fixture has a capped visible clue", menu.levelClue[2] > 1);
    menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 64));
    player.experienceLevel = 60;
    int bookBefore = player.experienceLevel;
    boolean bookEnchanted = menu.clickMenuButton(player, 2);
    ItemStack book = menu.getSlot(0).getItem();
    var stored =
        book.getOrDefault(
            DataComponents.STORED_ENCHANTMENTS,
            net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
    check(
        "native enchanted book stored caps applied",
        bookEnchanted
            && book.is(Items.ENCHANTED_BOOK)
            && !stored.isEmpty()
            && stored.entrySet().stream().allMatch(entry -> entry.getIntValue() <= 1));
    check(
        "native enchanted book refund uses actual paid levels",
        player.experienceLevel == bookBefore);
    player.closeContainer();
    mod.settings().set(InventoryRules.ROOT + ".enchantment-caps", List.of());
  }

  private void timber() {
    net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register(
        (world, p, pos, state, entity) -> denied == null || !denied.equals(pos));
    BlockPos first = new BlockPos(5, 100, 5),
        protectedTree = new BlockPos(9, 100, 5),
        placedTree = new BlockPos(13, 100, 5),
        timedTree = new BlockPos(17, 100, 5),
        brokenToolTree = new BlockPos(21, 100, 5);
    steps.add(
        () -> {
          mod.settings().set("tree-cutter.enabled", true);
          mod.settings().set("tree-cutter.break-time.enabled", false);
          mod.settings().set("tree-cutter.blocks-per-tick", 2);
          tree(first);
          player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
          check("native timber first break succeeds", player.gameMode.destroyBlock(first));
        });
    steps.add(() -> {});
    steps.add(
        () ->
            check(
                "native timber natural logs harvested",
                java.util.stream.IntStream.range(0, 4)
                    .allMatch(i -> player.level().getBlockState(first.above(i)).isAir())));
    steps.add(
        () ->
            check(
                "native timber normal durability", player.getMainHandItem().getDamageValue() >= 4));
    steps.add(
        () -> {
          tree(protectedTree);
          denied = protectedTree.above(2);
          player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
          player.gameMode.destroyBlock(protectedTree);
        });
    steps.add(() -> {});
    steps.add(
        () -> {
          check(
              "native timber honors Fabric protection",
              player.level().getBlockState(protectedTree.above(2)).is(Blocks.OAK_LOG)
                  && player.level().getBlockState(protectedTree.above(3)).is(Blocks.OAK_LOG));
          denied = null;
        });
    steps.add(
        () -> {
          tree(placedTree);
          for (int i = 0; i < 4; i++)
            mod.origins().add(player.level(), placedTree.above(i), "timber");
          player.gameMode.destroyBlock(placedTree);
        });
    steps.add(
        () ->
            check(
                "native timber placed structure excluded",
                player.level().getBlockState(placedTree.above()).is(Blocks.OAK_LOG)));
    steps.add(
        () -> {
          tree(timedTree);
          mod.settings().set("tree-cutter.break-time.enabled", true);
          mod.settings().set("tree-cutter.break-time.percent", 100.0);
          check(
              "native timber scaled attempt one canceled",
              !player.gameMode.destroyBlock(timedTree));
          check(
              "native timber scaled attempt two canceled",
              !player.gameMode.destroyBlock(timedTree));
          check(
              "native timber scaled attempt three canceled",
              !player.gameMode.destroyBlock(timedTree));
          check(
              "native timber scaled final break succeeds", player.gameMode.destroyBlock(timedTree));
        });
    steps.add(() -> {});
    steps.add(
        () -> {
          check(
              "native timber scaled harvest complete",
              player.level().getBlockState(timedTree.above(3)).isAir());
          mod.settings().set("tree-cutter.break-time.enabled", false);
        });
    steps.add(
        () -> {
          tree(brokenToolTree);
          ItemStack tool = new ItemStack(Items.WOODEN_AXE);
          tool.setDamageValue(tool.getMaxDamage() - 1);
          player.setItemSlot(EquipmentSlot.MAINHAND, tool);
          player.gameMode.destroyBlock(brokenToolTree);
        });
    steps.add(
        () ->
            check(
                "native timber stops when first tool breaks",
                player.getMainHandItem().isEmpty()
                    && player.level().getBlockState(brokenToolTree.above()).is(Blocks.OAK_LOG)));
  }

  private void tree(BlockPos base) {
    var level = player.serverLevel();
    level.getChunkAt(base);
    for (int y = 0; y < 4; y++) {
      level.setBlockAndUpdate(base.above(y), Blocks.OAK_LOG.defaultBlockState());
      mod.origins().remove(level, base.above(y), "timber");
    }
    level.setBlockAndUpdate(
        base.above(4),
        Blocks.OAK_LEAVES
            .defaultBlockState()
            .setValue(LeavesBlock.PERSISTENT, false)
            .setValue(LeavesBlock.DISTANCE, 1));
    player.setPos(base.getX() + 0.5, base.getY(), base.getZ() - 1);
  }

  private void menus() {
    mod.menus().main(player);
    check("root uses compact four-row geometry", player.containerMenu.slots.size() - 36 == 36);
    check("root Timber category is centered at slot 14",
        player.containerMenu.getSlot(14).getItem().getHoverName().getString().contains("Timber"));
    check("root reload is separated in the footer",
        player.containerMenu.getSlot(31).getItem().getHoverName().getString().contains("Reload"));
    check("root interior keeps breathing room", player.containerMenu.getSlot(11).getItem().isEmpty());
    check(
        "native root inventory opens",
        player
            .containerMenu
            .getSlot(4)
            .getItem()
            .getHoverName()
            .getString()
            .contains("CCR Config"));
    player.containerMenu.clicked(menuSlot(player, 11), 0, ClickType.PICKUP, player);
    steps.add(
        () ->
            check(
                "native timber submenu opens",
                player
                    .containerMenu
                    .getSlot(4)
                    .getItem()
                    .getHoverName()
                    .getString()
                    .contains("Timber")));
    steps.add(() -> player.closeContainer());
    steps.add(
        () ->
            check(
                "Escape returns semantic parent",
                player.containerMenu != player.inventoryMenu
                    && player
                        .containerMenu
                        .getSlot(4)
                        .getItem()
                        .getHoverName()
                        .getString()
                        .contains("CCR Config")));
    steps.add(() -> player.closeContainer());
    steps.add(() -> check("Escape root closes", player.containerMenu == player.inventoryMenu));
    steps.add(() -> mod.menus().main(player));
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 11), 0, ClickType.PICKUP, player));
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 14), 0, ClickType.PICKUP, player));
    steps.add(
        () ->
            check(
                "native anvil input opens",
                player.containerMenu instanceof net.minecraft.world.inventory.AnvilMenu));
    steps.add(this::checkInputCostSync);
    steps.add(() -> player.closeContainer());
    steps.add(
        () ->
            check(
                "Escape input cancels to parent",
                player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu
                    && player
                        .containerMenu
                        .getSlot(4)
                        .getItem()
                        .getHoverName()
                        .getString()
                        .contains("Timber")));
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 14), 0, ClickType.PICKUP, player));
    steps.add(
        () -> {
          ((net.minecraft.world.inventory.AnvilMenu) player.containerMenu)
              .setItemName("not-a-number");
          player.containerMenu.clicked(2, 0, ClickType.PICKUP, player);
        });
    steps.add(
        () ->
            check(
                "native invalid text remains open without mutation",
                player.containerMenu instanceof net.minecraft.world.inventory.AnvilMenu
                    && mod.settings().integer("tree-cutter.max-logs", 256, 2, 2048) == 256));
    steps.add(
        () -> {
          ((net.minecraft.world.inventory.AnvilMenu) player.containerMenu).setItemName("500");
          player.containerMenu.clicked(2, 0, ClickType.PICKUP, player);
        });
    steps.add(
        () ->
            check(
                "native valid text saves and returns to same parent",
                player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu
                    && mod.settings().integer("tree-cutter.max-logs", 256, 2, 2048) == 500));
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 53), 0, ClickType.PICKUP, player));
    steps.add(
        () ->
            check(
                "native explicit close never opens parent",
                player.containerMenu == player.inventoryMenu));
    steps.add(() -> mod.menus().main(player));
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 9), 0, ClickType.PICKUP, player));
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 50), 0, ClickType.PICKUP, player));
    steps.add(
        () ->
            check(
                "native mob list pagination opens page two",
                player
                    .containerMenu
                    .getSlot(menuSlot(player, 49))
                    .getItem()
                    .getHoverName()
                    .getString()
                    .contains("Page 2")));
    steps.add(() -> player.closeContainer());
    steps.add(
        () ->
            check(
                "native pagination does not add Escape history",
                player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu
                    && player
                        .containerMenu
                        .getSlot(4)
                        .getItem()
                        .getHoverName()
                        .getString()
                        .contains("CCR Config")));
    menuPageReturn();
    menuComfort();
  }

  private void menuPageReturn() {
    String[] childTitle = {""};
    steps.add(() -> mod.menus().main(player));
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 9), 0, ClickType.PICKUP, player));
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 50), 0, ClickType.PICKUP, player));
    steps.add(() -> check("list return setup opens page two", menuPage(2)));
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 9), 0, ClickType.PICKUP, player));
    steps.add(() -> {
      childTitle[0] = player.containerMenu.getSlot(4).getItem().getHoverName().getString();
      player.containerMenu.clicked(menuSlot(player, 10), 0, ClickType.PICKUP, player);
    });
    steps.add(() -> check("list child opens numeric input", player.containerMenu instanceof net.minecraft.world.inventory.AnvilMenu));
    steps.add(() -> player.closeContainer());
    steps.add(() -> check("list child input cancels to its detail page",
        player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu
            && player.containerMenu.getSlot(4).getItem().getHoverName().getString().equals(childTitle[0])));
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 9), 0, ClickType.PICKUP, player));
    steps.add(() -> player.closeContainer());
    steps.add(() -> check("Escape after a detail refresh restores list page two", menuPage(2)));
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 9), 0, ClickType.PICKUP, player));
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 45), 0, ClickType.PICKUP, player));
    steps.add(() -> check("Back from a list child restores page two", menuPage(2)));
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 48), 0, ClickType.PICKUP, player));
    steps.add(() -> check("previous page can explicitly select page one", menuPage(1)));
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 50), 0, ClickType.PICKUP, player));
    steps.add(() -> check("next page still selects page two", menuPage(2)));
    steps.add(() -> player.closeContainer());
    steps.add(() -> check("list Escape returns directly to root after browsing",
        player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu
            && player.containerMenu.getSlot(4).getItem().getHoverName().getString().contains("CCR Config")));
  }

  private boolean menuPage(int page) {
    return player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu
        && player.containerMenu.getSlot(menuSlot(player, 49)).getItem().getHoverName().getString().contains("Page " + page + " /");
  }

  private void menuComfort() {
    steps.add(
        () -> {
          mod.settings().set("clean-stacks.wood.ACACIA_LOG", "minecraft:diamond");
          player.containerMenu.clicked(menuSlot(player, 13), 0, ClickType.PICKUP, player);
        });
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 9), 0, ClickType.PICKUP, player));
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 13), 1, ClickType.PICKUP, player));
    steps.add(
        () ->
            check(
                "native clean source right-click resets mapping",
                !mod.settings().contains("clean-stacks.wood.ACACIA_LOG")));
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 11), 0, ClickType.PICKUP, player));
    steps.add(
        () -> {
          ((net.minecraft.world.inventory.AnvilMenu) player.containerMenu)
              .setItemName("minecraft:cherry_log");
          player.containerMenu.clicked(2, 0, ClickType.PICKUP, player);
        });
    steps.add(
        () ->
            check(
                "native grouped stripped conversion leaves ordinary logs untouched",
                mod.settings()
                        .text("clean-stacks.wood.STRIPPED_OAK_LOG", "")
                        .equals("minecraft:cherry_log")
                    && !mod.settings().contains("clean-stacks.wood.OAK_LOG")));
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 53), 0, ClickType.PICKUP, player));
    steps.add(() -> mod.menus().main(player));
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 12), 0, ClickType.PICKUP, player));
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 11), 0, ClickType.PICKUP, player));
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 9), 0, ClickType.PICKUP, player));
    steps.add(
        () -> {
          ((net.minecraft.world.inventory.AnvilMenu) player.containerMenu)
              .setItemName("minecraft:bow");
          player.containerMenu.clicked(2, 0, ClickType.PICKUP, player);
        });
    steps.add(
        () ->
            check(
                "native item search opens selected editor",
                player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu
                    && player
                        .containerMenu
                        .getSlot(4)
                        .getItem()
                        .getHoverName()
                        .getString()
                        .contains("Bow")));
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 10), 0, ClickType.PICKUP, player));
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 9), 0, ClickType.PICKUP, player));
    steps.add(
        () ->
            check(
                "native standalone tool copy does not crash",
                player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu
                    && player
                        .containerMenu
                        .getSlot(4)
                        .getItem()
                        .getHoverName()
                        .getString()
                        .contains("Bow")));
    steps.add(() -> player.closeContainer());
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 10), 1, ClickType.PICKUP, player));
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 9), 0, ClickType.PICKUP, player));
    steps.add(
        () ->
            check(
                "native configured item right-click removal",
                !mod.settings().contains("automatic-enchantments.tools.BOW")));
  }

  private void auditMenus() {
    steps.add(
        () -> {
          mod.settings().set(InventoryRules.ROOT + ".enabled", true);
          mod.settings()
              .set(
                  InventoryRules.ROOT + ".armor-rules",
                  List.of(Map.of("enabled", true, "target", "DIAMOND", "maximum", 1)));
          mod.settings().set("automatic-enchantments.tools.SHEARS", Map.of("unbreaking", 2));
          player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
          player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
          player.getInventory().setItem(7, new ItemStack(Items.SHEARS));
        });
    steps.add(
        () -> {
          check(
              "native equipment mutation corrected next tick",
              player.getItemBySlot(EquipmentSlot.CHEST).isEmpty());
          check(
              "native inventory mutation auto enchanted next tick",
              EnchantmentHelper.getItemEnchantmentLevel(
                      mod.inventory().enchantments().getHolderOrThrow(Enchantments.UNBREAKING),
                      player.getInventory().getItem(7))
                  == 2);
          mod.settings().set(InventoryRules.ROOT + ".enabled", false);
          page("rule", false, 0);
        });
    steps.add(
        () ->
            check(
                "native armor rule exposes paused master state",
                player
                    .containerMenu
                    .getSlot(menuSlot(player, 9))
                    .getItem()
                    .getHoverName()
                    .getString()
                    .contains("paused")));
    steps.add(() -> clickNamed("Target:", 0));
    steps.add(() -> clickNamed("Select Specific Items", 0));
    steps.add(
        () ->
            check(
                "native armor family not silently selected as diamond gem",
                player
                    .containerMenu
                    .getSlot(menuSlot(player, 9))
                    .getItem()
                    .getHoverName()
                    .getString()
                    .contains("0 item")));
    steps.add(() -> clickNamed("Search Items", 0));
    steps.add(() -> submit("diamond_boots"));
    steps.add(() -> clickNamed("Diamond boots", 0));
    steps.add(() -> clickNamed("Apply", 0));
    steps.add(
        () -> {
          check(
              "native exact armor picker saves one explicit item",
              mod.settings()
                  .entries(InventoryRules.ROOT + ".armor-rules")
                  .getFirst()
                  .get("target")
                  .equals("minecraft:diamond_boots"));
          mod.settings()
              .set(
                  InventoryRules.ROOT + ".enchantment-caps",
                  List.of(
                      Map.of(
                          "enabled",
                          false,
                          "target",
                          "SWORDS",
                          "enchantment",
                          "sharpness",
                          "maximum",
                          3)));
          page("rule", true, 0);
        });
    steps.add(() -> clickNamed("Maximum:", 0));
    steps.add(() -> submit("10000"));
    steps.add(() -> clickNamed("XP per Removed Level", 0));
    steps.add(() -> submit("2000"));
    steps.add(
        () -> {
          var rule = mod.settings().entries(InventoryRules.ROOT + ".enchantment-caps").getFirst();
          check(
              "native cap menus preserve full positive integer bounds",
              Settings.number(rule, "maximum", 0) == 10000
                  && Settings.number(rule, "xp-per-removed-level", 0) == 2000);
          page("autoItem", "tools", Items.BOW);
        });
    steps.add(() -> clickNamed("Add Enchantment", 0));
    steps.add(
        () -> {
          check(
              "native automatic browser excludes incompatible enchantments",
              java.util.stream.IntStream.range(9, 45)
                  .noneMatch(
                      i ->
                          player
                              .containerMenu
                              .getSlot(i)
                              .getItem()
                              .getHoverName()
                              .getString()
                              .contains("Sharpness")));
          clickNamed("Search Enchantments", 0);
        });
    steps.add(() -> submit("unbreaking"));
    steps.add(() -> clickNamed("Unbreaking", 0));
    steps.add(() -> submit("4"));
    steps.add(
        () -> {
          check(
              "native compatible enchantment browser edits level",
              mod.settings()
                      .integer("automatic-enchantments.tools.BOW.minecraft:unbreaking", 0, 0, 255)
                  == 4);
          mod.settings()
              .set(
                  "automatic-enchantments.tools.DIAMOND_AXE",
                  Map.of("minecraft:unbreaking", 3, "minecraft:efficiency", 5));
          mod.settings()
              .set("automatic-enchantments.tools.DIAMOND_SWORD", Map.of("minecraft:unbreaking", 2));
          mod.settings().set("automatic-enchantments.tools.DIAMOND_HOE", Map.of("unbreaking", 2));
          mod.settings().set("automatic-enchantments.tools.DIAMOND_PICKAXE", Map.of());
          page("autoItem", "tools", Items.DIAMOND_AXE);
        });
    steps.add(() -> clickNamed("Copy to Same Material", 0));
    steps.add(() -> clickNamed("Confirm", 0));
    steps.add(
        () -> {
          check(
              "native auto copy respects material and compatibility without overwriting",
              mod.settings()
                          .integer(
                              "automatic-enchantments.tools.DIAMOND_PICKAXE.minecraft:unbreaking",
                              0,
                              0,
                              255)
                      == 3
                  && mod.settings()
                          .integer(
                              "automatic-enchantments.tools.DIAMOND_SWORD.minecraft:unbreaking",
                              0,
                              0,
                              255)
                      == 2
                  && !mod.settings()
                      .contains("automatic-enchantments.tools.DIAMOND_SWORD.minecraft:efficiency")
                  && mod.settings()
                          .integer(
                              "automatic-enchantments.tools.BOW.minecraft:unbreaking", 0, 0, 255)
                      == 4);
          ItemStack hoe = new ItemStack(Items.DIAMOND_HOE);
          mod.inventory().auto(hoe);
          check(
              "native material copy preserves imported enchantment aliases",
              EnchantmentHelper.getItemEnchantmentLevel(
                      mod.inventory().enchantments().getHolderOrThrow(Enchantments.UNBREAKING), hoe)
                  == 2);
          mod.settings()
              .set(
                  "mobs.COW",
                  Map.of(
                      "xp-bonus",
                      17,
                      "cooked-food",
                      false,
                      "drops",
                      List.of(Map.of("item", "diamond", "minimum", 1, "maximum", 1, "chance", 100)),
                      "vanilla-overrides",
                      List.of(Map.of("item", "beef", "minimum", 1, "maximum", 1, "chance", 0))));
          page("mob", "COW");
        });
    steps.add(() -> clickNamed("Reset Drops", 0));
    steps.add(() -> clickNamed("Confirm", 0));
    steps.add(
        () -> {
          check(
              "native reset drops preserves cooking and XP",
              mod.settings().entries("mobs.COW.drops").isEmpty()
                  && mod.settings().entries("mobs.COW.vanilla-overrides").isEmpty()
                  && mod.settings().integer("mobs.COW.xp-bonus", 0, 0, 100) == 17
                  && !mod.settings().bool("mobs.COW.cooked-food", true));
          page("vanillaMob", "COW", false);
        });
    steps.add(() -> clickNamed("Beef", 1));
    steps.add(
        () ->
            check(
                "native right click disables vanilla instead of restoring it",
                mod.settings().entries("mobs.COW.vanilla-overrides").stream()
                    .anyMatch(rule -> Settings.number(rule, "chance", 100.0) == 0)));
    steps.add(() -> clickNamed("Beef", 1));
    steps.add(
        () -> {
          check(
              "native second right click restores vanilla",
              mod.settings().entries("mobs.COW.vanilla-overrides").isEmpty());
          mod.settings().set("ore-drops.iron_ore", Map.of("xp-bonus", 7));
          mod.settings().set("ore-drops.deepslate_iron_ore", Map.of("xp-bonus", 3));
          page("ore", "iron_ore");
        });
    steps.add(() -> clickNamed("Copy to Ore", 0));
    steps.add(() -> submit("deepslate_iron_ore"));
    steps.add(
        () ->
            check(
                "native ore overwrite waits for confirmation",
                mod.settings().integer("ore-drops.deepslate_iron_ore.xp-bonus", 0, 0, 100) == 3));
    steps.add(() -> clickNamed("Confirm", 0));
    steps.add(
        () ->
            check(
                "native confirmed ore copy includes XP",
                mod.settings().integer("ore-drops.deepslate_iron_ore.xp-bonus", 0, 0, 100) == 7));
    steps.add(
        () -> {
          try {
            var config =
                FabricLoader.getInstance().getConfigDir().resolve("cutcleanreborn/config.yml");
            mod.save();
            String content = Files.readString(config);
            boolean timber = mod.settings().bool("tree-cutter.enabled", false);
            try {
              Files.writeString(config, "mobs: [\n");
              check(
                  "native malformed reload reports failure and preserves live rules",
                  !mod.reload() && mod.settings().bool("tree-cutter.enabled", false) == timber);
            } finally {
              Files.writeString(config, content);
            }
            mod.server().getPlayerList().getPlayers().add(player);
            try {
              page("rule", true, 0);
              player.closeContainer();
              check(
                  "native reload cancels stale Escape reopen callbacks",
                  mod.reload() && player.containerMenu == player.inventoryMenu);
            } finally {
              mod.server().getPlayerList().getPlayers().remove(player);
            }
          } catch (Exception exception) {
            throw new IllegalStateException(exception);
          }
        });
    steps.add(
        () ->
            check(
                "native menu remains closed after reload tick",
                player.containerMenu == player.inventoryMenu));
    steps.add(
        () -> {
          mod.settings().set("animal-drops.cook", true);
          mod.settings().set("animal-drops.xp-bonus", 0);
          mod.settings().set("mobs.COW", Map.of("xp-bonus", 17, "cooked-food", false));
          page("mob", "COW");
        });
    steps.add(() -> clickNamed("Cook Food", 0));
    steps.add(() -> clickNamed("Bonus XP", 0));
    steps.add(() -> submit("0"));
    steps.add(
        () -> {
          check(
              "native mob values returning to default restore inheritance",
              !mod.settings().contains("mobs.COW.cooked-food")
                  && !mod.settings().contains("mobs.COW.xp-bonus"));
          mod.settings().set("animal-drops.cook", false);
          mod.settings().set("animal-drops.xp-bonus", 9);
          page("mob", "COW");
        });
    steps.add(
        () -> {
          check(
              "native mob follows later global cooking and XP changes",
              player.containerMenu.getSlot(menuSlot(player, 9)).getItem().getHoverName().getString().contains("OFF")
                  && player
                      .containerMenu
                      .getSlot(menuSlot(player, 10))
                      .getItem()
                      .getHoverName()
                      .getString()
                      .contains("9"));
          mod.settings()
              .set(
                  InventoryRules.ROOT + ".armor-rules",
                  List.of(Map.of("enabled", false, "target", "DIAMOND", "maximum", 1)));
          page("ruleList", false);
        });
    steps.add(() -> clickNamed("DIAMOND", 1));
    steps.add(
        () -> {
          check(
              "native rule right click quick toggle retained",
              Settings.enabled(
                  mod.settings().entries(InventoryRules.ROOT + ".armor-rules").getFirst()));
          for (int slot = 9; slot < player.containerMenu.slots.size() - 36; slot++)
            if (player
                .containerMenu
                .getSlot(slot)
                .getItem()
                .getHoverName()
                .getString()
                .contains("DIAMOND")) {
              player.containerMenu.clicked(slot, 0, ClickType.QUICK_MOVE, player);
              break;
            }
        });
    steps.add(
        () ->
            check(
                "native shift rule deletion waits for confirmation",
                mod.settings().entries(InventoryRules.ROOT + ".armor-rules").size() == 1
                    && player
                        .containerMenu
                        .getSlot(4)
                        .getItem()
                        .getHoverName()
                        .getString()
                        .contains("Confirmation")));
    steps.add(() -> clickNamed("Confirm", 0));
    steps.add(
        () ->
            check(
                "native shift rule deletion confirmed",
                mod.settings().entries(InventoryRules.ROOT + ".armor-rules").isEmpty()));
    steps.add(
        () -> {
          mod.settings().set("automatic-enchantments.tools.BOW", Map.of("unbreaking", 7));
          page("autoItem", "tools", Items.BOW);
        });
    steps.add(() -> clickNamed("Add Enchantment", 0));
    steps.add(() -> clickNamed("Unbreaking", 0));
    steps.add(
        () -> {
          check(
              "native enchantment editor preloads imported current level",
              player.containerMenu.getSlot(0).getItem().getHoverName().getString().equals("7"));
          submit("5");
        });
    steps.add(
        () ->
            check(
                "native automatic edit canonicalizes aliases without duplicates",
                mod.settings().section("automatic-enchantments.tools.BOW").size() == 1
                    && mod.settings()
                            .integer(
                                "automatic-enchantments.tools.BOW.minecraft:unbreaking", 0, 0, 255)
                        == 5));
    steps.add(() -> clickNamed("Add Enchantment", 0));
    steps.add(() -> clickNamed("Unbreaking", 1));
    steps.add(
        () ->
            check(
                "native automatic browser right click removes the enchantment",
                mod.settings().section("automatic-enchantments.tools.BOW").isEmpty()));
  }

  private void concurrentRules() {
    steps.add(
        () -> {
          var profile =
              new GameProfile(
                  UUID.fromString("34906bc8-fabc-4147-8396-000000000002"), "CCRSecondAdmin");
          secondAdmin =
              new ServerPlayer(
                  mod.server(),
                  mod.server().overworld(),
                  profile,
                  ClientInformation.createDefault());
          secondAdmin.connection =
              new ServerGamePacketListenerImpl(
                  mod.server(),
                  new Connection(PacketFlow.SERVERBOUND),
                  secondAdmin,
                  CommonListenerCookie.createInitial(profile, false));
          mod.server().getPlayerList().op(profile);
          mod.settings()
              .set(
                  InventoryRules.ROOT + ".enchantment-caps",
                  List.of(
                      Map.of(
                          "id",
                          "first",
                          "enabled",
                          false,
                          "target",
                          "SWORDS",
                          "enchantment",
                          "sharpness",
                          "maximum",
                          1),
                      Map.of(
                          "id",
                          "editing",
                          "enabled",
                          false,
                          "target",
                          "SWORDS",
                          "enchantment",
                          "sharpness",
                          "maximum",
                          2),
                      Map.of(
                          "id",
                          "other",
                          "enabled",
                          false,
                          "target",
                          "SWORDS",
                          "enchantment",
                          "sharpness",
                          "maximum",
                          3)));
          page("rule", true, 1);
        });
    steps.add(() -> clickNamed("Maximum:", 0));
    steps.add(
        () -> {
          ((net.minecraft.world.inventory.AnvilMenu) player.containerMenu).setItemName("7");
          asSecond(() -> page("ruleList", true));
        });
    steps.add(() -> secondAdmin.containerMenu.clicked(menuSlot(secondAdmin, 10), 0, ClickType.QUICK_MOVE, secondAdmin));
    steps.add(() -> asSecond(() -> clickNamed("Confirm", 0)));
    steps.add(() -> submit("7"));
    steps.add(
        () -> {
          var rules = mod.settings().entries(InventoryRules.ROOT + ".enchantment-caps");
          check(
              "two admin sessions preserve rule UUID when prior rule is deleted during input",
              rules.size() == 2
                  && rules.stream()
                      .anyMatch(
                          rule ->
                              rule.get("id").equals("editing")
                                  && Settings.number(rule, "maximum", 0) == 7)
                  && rules.stream()
                      .anyMatch(
                          rule ->
                              rule.get("id").equals("other")
                                  && Settings.number(rule, "maximum", 0) == 3));
        });
    steps.add(() -> page("ruleById", true, "editing"));
    steps.add(() -> clickNamed("Maximum:", 0));
    steps.add(() -> asSecond(() -> page("ruleList", true)));
    steps.add(() -> secondAdmin.containerMenu.clicked(menuSlot(secondAdmin, 10), 0, ClickType.QUICK_MOVE, secondAdmin));
    steps.add(() -> asSecond(() -> clickNamed("Confirm", 0)));
    steps.add(() -> submit("9"));
    steps.add(
        () -> {
          var rules = mod.settings().entries(InventoryRules.ROOT + ".enchantment-caps");
          check(
              "deleted rule input is explicitly cancelled instead of editing another rule",
              rules.size() == 1
                  && rules.getFirst().get("id").equals("other")
                  && Settings.number(rules.getFirst(), "maximum", 0) == 3
                  && !(player.containerMenu instanceof net.minecraft.world.inventory.AnvilMenu)
                  && player
                      .containerMenu
                      .getSlot(4)
                      .getItem()
                      .getHoverName()
                      .getString()
                      .contains("Enchantment Caps"));
          concurrentFixture();
          page("ruleById", true, "editing");
        });
    steps.add(() -> clickNamed("Delete Rule", 0));
    steps.add(() -> asSecond(() -> page("ruleList", true)));
    steps.add(() -> secondAdmin.containerMenu.clicked(menuSlot(secondAdmin, 10), 0, ClickType.QUICK_MOVE, secondAdmin));
    steps.add(() -> asSecond(() -> clickNamed("Confirm", 0)));
    steps.add(() -> clickNamed("Confirm", 0));
    steps.add(
        () -> {
          var rules = mod.settings().entries(InventoryRules.ROOT + ".enchantment-caps");
          check(
              "pending deletion resolves stable rule UUID after earlier deletion",
              rules.size() == 1 && rules.getFirst().get("id").equals("other"));
          concurrentFixture();
          page("ruleById", true, "editing");
        });
    steps.add(() -> clickNamed("Copy to Another Target", 0));
    steps.add(() -> asSecond(() -> page("ruleList", true)));
    steps.add(() -> secondAdmin.containerMenu.clicked(menuSlot(secondAdmin, 11), 0, ClickType.QUICK_MOVE, secondAdmin));
    steps.add(() -> asSecond(() -> clickNamed("Confirm", 0)));
    steps.add(() -> clickNamed("Group: TOOLS", 0));
    steps.add(
        () -> {
          var rules = mod.settings().entries(InventoryRules.ROOT + ".enchantment-caps");
          check(
              "copy of concurrently deleted rule is explicitly cancelled",
              rules.size() == 2
                  && rules.stream().noneMatch(rule -> rule.get("target").equals("TOOLS"))
                  && player
                      .containerMenu
                      .getSlot(4)
                      .getItem()
                      .getHoverName()
                      .getString()
                      .contains("Enchantment Caps"));
          concurrentFixture();
          page("ruleById", true, "editing");
        });
    steps.add(() -> clickNamed("Target:", 0));
    steps.add(() -> asSecond(() -> page("ruleList", true)));
    steps.add(() -> secondAdmin.containerMenu.clicked(menuSlot(secondAdmin, 10), 0, ClickType.QUICK_MOVE, secondAdmin));
    steps.add(() -> asSecond(() -> clickNamed("Confirm", 0)));
    steps.add(() -> player.closeContainer());
    steps.add(
        () ->
            check(
                "Escape parent resolves the same UUID after list reorder",
                player
                    .containerMenu
                    .getSlot(menuSlot(player, 11))
                    .getItem()
                    .getHoverName()
                    .getString()
                    .contains("2")));
    steps.add(
        () -> {
          mod.settings()
              .set(
                  InventoryRules.ROOT + ".armor-rules",
                  List.of(
                      Map.of("enabled", false, "target", "DIAMOND", "maximum", 1),
                      Map.of("id", "duplicate", "enabled", false, "target", "IRON", "maximum", 2),
                      Map.of(
                          "id",
                          "duplicate",
                          "enabled",
                          false,
                          "target",
                          "NETHERITE",
                          "maximum",
                          3)));
          page("ruleList", false);
          var ids =
              mod.settings().entries(InventoryRules.ROOT + ".armor-rules").stream()
                  .map(rule -> rule.get("id"))
                  .toList();
          check(
              "legacy missing and duplicate rule IDs normalized once",
              ids.stream().allMatch(id -> id instanceof String value && !value.isBlank())
                  && ids.stream().distinct().count() == 3);
        });
  }

  private void concurrentFixture() {
    mod.settings()
        .set(
            InventoryRules.ROOT + ".enchantment-caps",
            List.of(
                Map.of(
                    "id",
                    "first",
                    "enabled",
                    false,
                    "target",
                    "SWORDS",
                    "enchantment",
                    "sharpness",
                    "maximum",
                    1),
                Map.of(
                    "id",
                    "editing",
                    "enabled",
                    false,
                    "target",
                    "SWORDS",
                    "enchantment",
                    "sharpness",
                    "maximum",
                    2),
                Map.of(
                    "id",
                    "other",
                    "enabled",
                    false,
                    "target",
                    "SWORDS",
                    "enchantment",
                    "sharpness",
                    "maximum",
                    3)));
  }

  private void lifecycleHooks() {
    steps.add(
        () -> {
          var server = mod.server();
          var oldOrigins = mod.origins();
          mod.settings().set("automatic-enchantments.enabled", true);
          mod.settings().set("automatic-enchantments.tools.SHEARS", Map.of("unbreaking", 3));
          mod.settings().set("clean-stacks.players." + player.getUUID(), true);
          mod.origins().add(server.overworld(), new BlockPos(-12, 111, -12), "timber");
          mod.catalog().mob("COW", false);
          page("timberTools");
          player.getInventory().setItem(6, new ItemStack(Items.SHEARS));
          // Common Fabric hooks, same mod instance and server fixture: not an actual integrated
          // client.
          net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING
              .invoker()
              .onServerStopping(server);
          net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED
              .invoker()
              .onServerStopped(server);
          net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED
              .invoker()
              .onServerStarted(server);
          check(
              "common lifecycle hooks reload persistent origins without erasing them",
              oldOrigins != mod.origins()
                  && mod.origins()
                      .contains(server.overworld(), new BlockPos(-12, 111, -12), "timber"));
          check(
              "common lifecycle hooks preserve UUID personal preference",
              mod.settings().bool("clean-stacks.players." + player.getUUID(), false));
          check(
              "common lifecycle hooks clear correction deduplication",
              size(mod.inventory(), "scheduled") == 0 && size(mod.inventory(), "correcting") == 0);
          check(
              "common lifecycle hooks clear menu sessions and catalog cache",
              size(mod.menus(), "opening") == 0 && size(mod.catalog(), "cache") == 0);
          check(
              "common lifecycle hooks clear all Timber queues",
              size(mod.timber(), "active") == 0
                  && size(mod.timber(), "ready") == 0
                  && size(mod.timber(), "progress") == 0
                  && size(mod.timber(), "internal") == 0);
          player.closeContainer();
          player.getInventory().setItem(6, new ItemStack(Items.SHEARS));
        });
    steps.add(
        () -> {
          check(
              "common second lifecycle corrects same UUID item next tick",
              EnchantmentHelper.getItemEnchantmentLevel(
                      mod.inventory().enchantments().getHolderOrThrow(Enchantments.UNBREAKING),
                      player.getInventory().getItem(6))
                  == 3);
          check(
              "old menu session cannot reopen after common second lifecycle",
              player.containerMenu == player.inventoryMenu);
        });
  }

  private void canePhysics() {
    var level = player.serverLevel();
    BlockPos pistonCane = new BlockPos(-24, 100, -24);
    BlockPos supportCane = new BlockPos(-18, 100, -24);
    steps.add(
        () -> {
          mod.settings().set("better-sugar-cane.enabled", true);
          mod.settings().set("better-sugar-cane.support.enabled", true);
          mod.settings().set("better-sugar-cane.support.chance", 100.0);
          mod.settings().set("better-sugar-cane.support.minimum", 4);
          mod.settings().set("better-sugar-cane.support.maximum", 4);
          level.setBlockAndUpdate(pistonCane.west(2), Blocks.AIR.defaultBlockState());
          level.setBlockAndUpdate(pistonCane.west(), Blocks.AIR.defaultBlockState());
          naturalCane(pistonCane);
          level.setBlockAndUpdate(
              pistonCane.west(),
              Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
          clearCaneDrops(pistonCane);
          check(
              "native piston cane is supported before destruction",
              level.getBlockState(pistonCane).canSurvive(level, pistonCane));
          level.setBlockAndUpdate(pistonCane.west(2), Blocks.REDSTONE_BLOCK.defaultBlockState());
        });
    for (int i = 0; i < 5; i++) steps.add(() -> {});
    steps.add(
        () -> {
          CutCleanReborn.LOGGER.info(
              "CCR cane piston diagnostic base={} front={} drops={}",
              level.getBlockState(pistonCane.west()),
              level.getBlockState(pistonCane),
              caneDrops(pistonCane));
          check(
              "native powered piston physically destroys cane",
              level.getBlockState(pistonCane.west()).getValue(PistonBaseBlock.EXTENDED)
                  && level.getBlockState(pistonCane).is(Blocks.PISTON_HEAD));
          check("native piston supported cane keeps vanilla quantity", caneDrops(pistonCane) == 1);
          clearCaneDrops(pistonCane);
          naturalCane(supportCane);
          clearCaneDrops(supportCane);
          check(
              "native support cane fixture starts supported",
              level.getBlockState(supportCane).canSurvive(level, supportCane));
          level.setBlockAndUpdate(supportCane.below(), Blocks.AIR.defaultBlockState());
        });
    for (int i = 0; i < 3; i++) steps.add(() -> {});
    steps.add(
        () -> {
          check(
              "native true support loss physically removes cane",
              level.getBlockState(supportCane).isAir());
          check("native true support loss retains bonus quantity", caneDrops(supportCane) == 4);
          clearCaneDrops(supportCane);
          level.setBlockAndUpdate(pistonCane.west(2), Blocks.AIR.defaultBlockState());
          level.setBlockAndUpdate(pistonCane.west(), Blocks.AIR.defaultBlockState());
          level.setChunkForced(-2, -2, false);
        });
  }

  private void naturalCane(BlockPos pos) {
    var level = player.serverLevel();
    level.getChunkAt(pos);
    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    for (int x = -1; x <= 1; x++)
      for (int z = -1; z <= 2; z++)
        level.setBlockAndUpdate(pos.offset(x, -1, z), Blocks.DIRT.defaultBlockState());
    level.setBlockAndUpdate(pos.below().south(), Blocks.WATER.defaultBlockState());
    mod.origins().remove(level, pos, "cane");
    level.setBlockAndUpdate(pos, Blocks.SUGAR_CANE.defaultBlockState());
  }

  private int caneDrops(BlockPos pos) {
    return player
        .serverLevel()
        .getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2.5))
        .stream()
        .filter(entity -> entity.getItem().is(Items.SUGAR_CANE))
        .mapToInt(entity -> entity.getItem().getCount())
        .sum();
  }

  private void clearCaneDrops(BlockPos pos) {
    player.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2.5)).stream()
        .filter(entity -> entity.getItem().is(Items.SUGAR_CANE))
        .forEach(ItemEntity::discard);
  }

  private int size(Object owner, String name) {
    try {
      var field = owner.getClass().getDeclaredField(name);
      field.setAccessible(true);
      Object value = field.get(owner);
      return value instanceof java.util.Map<?, ?> map
          ? map.size()
          : ((java.util.Collection<?>) value).size();
    } catch (ReflectiveOperationException error) {
      throw new IllegalStateException(error);
    }
  }

  private void asSecond(Runnable action) {
    ServerPlayer first = player;
    player = secondAdmin;
    try {
      action.run();
    } finally {
      player = first;
    }
  }

  private void page(String method, Object... args) {
    try {
      for (var candidate : Menus.class.getDeclaredMethods())
        if (candidate.getName().equals(method) && candidate.getParameterCount() == args.length) {
          candidate.setAccessible(true);
          mod.menus().open(player, (Menus.Page) candidate.invoke(mod.menus(), args));
          return;
        }
      throw new IllegalArgumentException("No menu " + method);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(exception);
    }
  }

  /** Resolve an entry's old logical index to its actual rendered slot, including compact footers. */
  private int menuSlot(ServerPlayer viewer, int logical) {
    if (!viewer.containerMenu.getClass().getSimpleName().equals("Panel")) return logical;
    try {
      var field = viewer.containerMenu.getClass().getDeclaredField("contentSlots");
      field.setAccessible(true);
      int[] positions = (int[]) field.get(viewer.containerMenu);
      if (logical >= 9 && logical < 45) {
        int index = logical - 9;
        if (index >= positions.length) throw new IllegalArgumentException("No rendered entry " + index);
        return positions[index];
      }
      return viewer.containerMenu.slots.size() - 36 - 9 + logical - 45;
    } catch (ReflectiveOperationException error) {
      throw new IllegalStateException(error);
    }
  }

  private void clickNamed(String name, int button) {
    for (int slot = 9; slot < player.containerMenu.slots.size() - 36; slot++)
      if (player.containerMenu.getSlot(slot).getItem().getHoverName().getString().contains(name)) {
        player.containerMenu.clicked(slot, button, ClickType.PICKUP, player);
        return;
      }
    throw new IllegalStateException("Menu entry not found: " + name);
  }

  private void submit(String value) {
    ((net.minecraft.world.inventory.AnvilMenu) player.containerMenu).setItemName(value);
    player.containerMenu.clicked(2, 0, ClickType.PICKUP, player);
  }

  private void finish() {
    try {
      if (player != null) player.containerMenu = player.inventoryMenu;
      if (mod != null && original != null) {
        mod.settings().restore(original);
        mod.save();
      }
      Map<String, Object> result =
          Map.of(
              "platform",
              "Fabric",
              "minecraft",
              "1.21.1",
              "passed",
              passed,
              "failed",
              failed,
              "counts",
              Map.of("passed", passed.size(), "failed", failed.size()),
              "realPlayerVisual",
              false);
      Files.writeString(
          FabricLoader.getInstance().getGameDir().resolve("ccr-qa-result.json"),
          new GsonBuilder().setPrettyPrinting().create().toJson(result));
      CutCleanReborn.LOGGER.info(
          "CCR native verification: {} passed, {} failed", passed.size(), failed.size());
      if (!failed.isEmpty()) CutCleanReborn.LOGGER.error("CCR failures: {}", failed);
    } catch (Exception error) {
      CutCleanReborn.LOGGER.error("CCR report error", error);
    }
  }
}
