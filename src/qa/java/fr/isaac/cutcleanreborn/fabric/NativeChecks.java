package fr.isaac.cutcleanreborn.fabric;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import fr.isaac.cutcleanreborn.fabric.qamixin.QaItemEntityAge;
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
  private RuleRuntime mod;
  private Map<String, Object> original;
  private ServerPlayer player;
  private int ticks;
  private BlockPos denied;
  private ItemEntity readinessProbe;
  private int startedAt;

  @Override
  public void onInitialize() {
    ServerTickEvents.END_SERVER_TICK.register(
        server -> {
          if (!Boolean.getBoolean("ccr.qa")
              || System.getProperty("ccr.persistence") != null
              || CutCleanReborn.runtime == null) return;
          ++ticks;
          if (ticks == 5) {
            mod = CutCleanReborn.runtime;
            original = mod.config.snapshot();
            var level = mod.server.overworld();
            level.setChunkForced(0, 0, true);
            level.setChunkForced(1, 0, true);
            readinessProbe = new ItemEntity(level, 0.5, 105, 0.5, new ItemStack(Items.STICK));
            readinessProbe.setNoGravity(true);
            readinessProbe.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            level.addFreshEntity(readinessProbe);
          }
          if (startedAt == 0
              && ticks >= 25
              && mod.server
                  .overworld()
                  .getEntitiesOfClass(ItemEntity.class, readinessProbe.getBoundingBox().inflate(1))
                  .contains(readinessProbe)) {
            startedAt = ticks;
            readinessProbe.discard();
            check("QA chunk entity tracking is active", true);
            try {
              initial();
            } catch (Throwable error) {
              failed.add("Uncaught initial verification: " + error);
              CutCleanReborn.LOG.error("CCR verification failed", error);
            }
          }
          if (startedAt == 0 && ticks >= 300) {
            failed.add("QA fixture chunk did not become entity-accessible");
            finish();
            server.halt(false);
            return;
          }
          if (startedAt > 0 && ticks > startedAt && !steps.isEmpty()) {
            try {
              steps.removeFirst().run();
            } catch (Throwable error) {
              failed.add("Native step: " + error);
              CutCleanReborn.LOG.error("CCR verification step failed", error);
            }
          }
          if (startedAt > 0 && ticks > startedAt + 5 && steps.isEmpty()) {
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
            mod.server, mod.server.overworld(), profile, ClientInformation.createDefault());
    player.connection =
        new ServerGamePacketListenerImpl(
            mod.server,
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
    mod.server.getPlayerList().op(player.nameAndId());
    player.setPos(0.5, 100, 0.5);
    // This isolated QA world is reused to test persistence. Old loose items must not satisfy a
    // later drop assertion, or appear to have been created by the operation under test.
    for (var entity :
        player
            .level()
            .getEntities(
                (net.minecraft.world.entity.Entity) null, new AABB(-32, -2048, -32, 64, 150, 64)))
      if (entity instanceof ItemEntity
          || entity instanceof net.minecraft.world.entity.ExperienceOrb) entity.discard();
    check("fabric entrypoint and dedicated server ready", CutCleanReborn.runtime != null);
    check(
        "standalone tool catalogue",
        InventoryRules.tool(Items.MACE)
            && InventoryRules.tool(Items.BOW)
            && InventoryRules.tool(Items.SHEARS)
            && InventoryRules.tool(Items.FLINT_AND_STEEL)
            && InventoryRules.armor(Items.TURTLE_HELMET));
    for (String command :
        List.of("ccr status", "ccr ping", "ccr help", "cutclean status", "crr status")) {
      mod.server
          .getCommands()
          .performPrefixedCommand(mod.server.createCommandSourceStack(), command);
      check(
          "registered command " + command,
          mod.server
              .getCommands()
              .getDispatcher()
              .parse(command, mod.server.createCommandSourceStack())
              .getReader()
              .getRemaining()
              .isEmpty());
    }
    clean();
    cleanScope();
    automatic();
    caps();
    configurationParity();
    enchantingBooks();
    catalogue();
    loot();
    survivalOnly();
    mace();
    maceLifecycle();
    craftTransaction();
    anvilTransaction();
    craftPreview();
    origins();
    menus();
    menuPageReturn();
    timber();
    visualSelectors();
    concurrentStuffEditors(false);
    concurrentStuffEditors(true);
    vanillaMenuActions();
    promptCorrections();
    lifecycleHooks();
    canePhysics();
  }

  private void canePhysics() {
    var level = player.level();
    BlockPos pistonCane = new BlockPos(5, 110, 10);
    BlockPos supportCane = new BlockPos(12, 110, 10);
    steps.add(
        () -> {
          mod.config.set("better-sugar-cane.enabled", true);
          mod.config.set("better-sugar-cane.support.enabled", true);
          mod.config.set("better-sugar-cane.support.chance", 100.0);
          mod.config.set("better-sugar-cane.support.minimum", 4);
          mod.config.set("better-sugar-cane.support.maximum", 4);
          level.setBlockAndUpdate(pistonCane.west(2), Blocks.AIR.defaultBlockState());
          level.setBlockAndUpdate(pistonCane.west(), Blocks.AIR.defaultBlockState());
          naturalCane(pistonCane);
          level.setBlockAndUpdate(
              pistonCane.west(),
              Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
          clearCaneDrops(pistonCane);
          check(
              "native piston cane starts supported",
              level.getBlockState(pistonCane).canSurvive(level, pistonCane));
          level.setBlockAndUpdate(pistonCane.west(2), Blocks.REDSTONE_BLOCK.defaultBlockState());
        });
    for (int i = 0; i < 5; i++) steps.add(() -> {});
    steps.add(
        () -> {
          check(
              "native powered piston physically destroys cane",
              level.getBlockState(pistonCane.west()).is(Blocks.PISTON)
                  && level.getBlockState(pistonCane.west()).getValue(PistonBaseBlock.EXTENDED)
                  && level.getBlockState(pistonCane).is(Blocks.PISTON_HEAD));
          check("native piston supported cane keeps vanilla quantity", caneDrops(pistonCane) == 1);
          clearCaneDrops(pistonCane);
          naturalCane(supportCane);
          clearCaneDrops(supportCane);
          check(
              "native support cane starts supported",
              level.getBlockState(supportCane).canSurvive(level, supportCane));
          level.setBlockAndUpdate(supportCane.below(), Blocks.AIR.defaultBlockState());
        });
    for (int i = 0; i < 3; i++) steps.add(() -> {});
    steps.add(
        () -> {
          check(
              "native true support loss physically removes cane",
              level.getBlockState(supportCane).isAir());
          check("native true support loss keeps bonus quantity", caneDrops(supportCane) == 4);
          clearCaneDrops(supportCane);
          level.setBlockAndUpdate(pistonCane.west(2), Blocks.AIR.defaultBlockState());
          level.setBlockAndUpdate(pistonCane.west(), Blocks.AIR.defaultBlockState());
        });
  }

  private void naturalCane(BlockPos pos) {
    var level = player.level();
    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    for (int x = -1; x <= 1; x++)
      for (int z = -1; z <= 2; z++)
        level.setBlockAndUpdate(pos.offset(x, -1, z), Blocks.DIRT.defaultBlockState());
    level.setBlockAndUpdate(pos.below().south(), Blocks.WATER.defaultBlockState());
    mod.remove(level, pos);
    level.setBlockAndUpdate(pos, Blocks.SUGAR_CANE.defaultBlockState());
  }

  private int caneDrops(BlockPos pos) {
    return player.level().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2.5)).stream()
        .filter(entity -> entity.getItem().is(Items.SUGAR_CANE))
        .mapToInt(entity -> entity.getItem().getCount())
        .sum();
  }

  private void clearCaneDrops(BlockPos pos) {
    player.level().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2.5)).stream()
        .filter(entity -> entity.getItem().is(Items.SUGAR_CANE))
        .forEach(ItemEntity::discard);
  }

  private void clean() {
    ItemStack input = new ItemStack(Items.BIRCH_LOG, 48), output = mod.inventory.clean(input);
    check("clean default logs/count", output.is(Items.OAK_LOG) && output.getCount() == 48);
    ItemStack custom = input.copy();
    custom.set(
        DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Custom block"));
    check("clean named preserved", mod.inventory.clean(custom) == custom);
    mod.config.set("clean-stacks.wood.ACACIA_LOG", "minecraft:missing_item");
    check(
        "invalid clean mapping falls back to default",
        mod.inventory.clean(new ItemStack(Items.ACACIA_LOG)).is(Items.OAK_LOG));
    mod.config.set("clean-stacks.wood.BIRCH_LOG", "minecraft:spruce_log");
    mod.config.set("clean-stacks.wood.SPRUCE_LOG", "minecraft:birch_log");
    ItemStack a = mod.inventory.clean(new ItemStack(Items.BIRCH_LOG)),
        b = mod.inventory.clean(new ItemStack(Items.SPRUCE_LOG));
    check("clean cycle canonical", a.getItem() == b.getItem());
    check("clean cycle idempotent", mod.inventory.clean(a).getItem() == a.getItem());
    mod.inventory.setCleanTarget(Items.BIRCH_PLANKS, Items.SPRUCE_PLANKS);
    check(
        "clean target fixed point",
        mod.inventory.clean(new ItemStack(Items.SPRUCE_PLANKS)).is(Items.SPRUCE_PLANKS));
  }

  private void automatic() {
    var unbreaking = mod.inventory.enchantments().getOrThrow(Enchantments.UNBREAKING);
    var efficiency = mod.inventory.enchantments().getOrThrow(Enchantments.EFFICIENCY);
    mod.config.set("automatic-enchantments.tools.DIAMOND_AXE.minecraft:unbreaking", 3);
    ItemStack stack = new ItemStack(Items.DIAMOND_AXE);
    stack.enchant(efficiency, 2);
    check(
        "automatic first application",
        mod.inventory.auto(stack)
            && EnchantmentHelper.getItemEnchantmentLevel(unbreaking, stack) == 3);
    String identity = InventoryRules.data(stack).getStringOr("ccr:auto_enchant_id", "");
    check("automatic persistent UUID", !identity.isBlank());
    check("automatic exactly once", !mod.inventory.auto(stack));
    mod.config.set("automatic-enchantments.tools.DIAMOND_AXE.minecraft:unbreaking", 1);
    mod.config.set("automatic-enchantments.refresh-version", 1);
    check(
        "automatic refresh",
        mod.inventory.auto(stack)
            && EnchantmentHelper.getItemEnchantmentLevel(unbreaking, stack) == 1);
    check(
        "automatic original enchant preserved",
        EnchantmentHelper.getItemEnchantmentLevel(efficiency, stack) == 2);
    check(
        "automatic identity retained",
        identity.equals(InventoryRules.data(stack).getStringOr("ccr:auto_enchant_id", "")));
    stack.enchant(efficiency, 5);
    mod.config.set("automatic-enchantments.refresh-version", 2);
    check(
        "automatic player edit preserved",
        !mod.inventory.auto(stack)
            && EnchantmentHelper.getItemEnchantmentLevel(efficiency, stack) == 5);
    var fortune = mod.inventory.enchantments().getOrThrow(Enchantments.FORTUNE);
    var silk = mod.inventory.enchantments().getOrThrow(Enchantments.SILK_TOUCH);
    mod.config.set("automatic-enchantments.tools.DIAMOND_PICKAXE.minecraft:fortune", 3);
    ItemStack conflict = new ItemStack(Items.DIAMOND_PICKAXE);
    conflict.enchant(silk, 1);
    mod.inventory.auto(conflict);
    check(
        "automatic conflicts respected",
        EnchantmentHelper.getItemEnchantmentLevel(fortune, conflict) == 0);
    ItemStack partial = new ItemStack(Items.DIAMOND_PICKAXE);
    var partialData = InventoryRules.data(partial);
    partialData.putString("ccr:auto_enchant_id", "partial-existing");
    InventoryRules.data(partial, partialData);
    check(
        "partial automatic identity remains untouched",
        !mod.inventory.auto(partial)
            && InventoryRules.data(partial)
                .getStringOr("ccr:auto_enchant_id", "")
                .equals("partial-existing")
            && EnchantmentHelper.getEnchantmentsForCrafting(partial).isEmpty());
  }

  private void caps() {
    mod.config.set(InventoryRules.ROOT + ".enabled", true);
    mod.config.set(
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
    var sharpness = mod.inventory.enchantments().getOrThrow(Enchantments.SHARPNESS);
    ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
    sword.enchant(sharpness, 5);
    var removed = mod.inventory.enforceCaps(sword);
    check(
        "enchantment cap correction",
        EnchantmentHelper.getItemEnchantmentLevel(sharpness, sword) == 2
            && removed.get(sharpness) == 3);
    player.experienceLevel = 10;
    check(
        "refund cannot mint XP without payment",
        mod.inventory.refund(player, sword, removed, 0) == 0 && player.experienceLevel == 10);
    check(
        "refund bounded by paid levels",
        mod.inventory.refund(player, sword, removed, 2) == 2 && player.experienceLevel == 12);
    mod.config.set(
        InventoryRules.ROOT + ".armor-rules",
        List.of(Map.of("id", "qa-armor", "enabled", true, "target", "DIAMOND", "maximum", 1)));
    player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
    player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
    mod.inventory.correct(player);
    check(
        "armor equipped limit",
        player.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET)
            && player.getItemBySlot(EquipmentSlot.CHEST).isEmpty());
    check(
        "armor overflow preserved",
        player.getInventory().contains(new ItemStack(Items.DIAMOND_CHESTPLATE)));
    mod.config.set(InventoryRules.ROOT + ".armor-rules", List.of());
  }

  private void configurationParity() {
    String path = InventoryRules.ROOT + ".enchantment-caps";
    var saved = mod.config.entries(path);
    var sharpness = mod.inventory.enchantments().getOrThrow(Enchantments.SHARPNESS);
    mod.config.set(
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
                "sharpness",
                "maximum",
                2,
                "refund-xp",
                true,
                "xp-per-removed-level",
                5)));
    check(
        "legacy blank target and unnamespaced cap preserved",
        mod.inventory.cap(Items.DIAMOND_SWORD, sharpness) == 2);
    mod.config.set(
        path,
        List.of(
            Map.of("enabled", true, "target", "SWORDS", "enchantment", "sharpness", "maximum", 0)));
    check(
        "invalid zero cap is ignored rather than changed",
        mod.inventory.cap(Items.DIAMOND_SWORD, sharpness) == -1);
    check(
        "Stuff TOOLS category matches Paper without changing automatic tools",
        InventoryRules.matches(Items.BOW, "TOOLS")
            && !InventoryRules.matches(Items.SHEARS, "TOOLS")
            && InventoryRules.tool(Items.SHEARS));
    check(
        "armor material group distinct from exact gem item",
        InventoryRules.matchesArmor(Items.DIAMOND_CHESTPLATE, "minecraft:DIAMOND")
            && !InventoryRules.matches(Items.DIAMOND_CHESTPLATE, "DIAMOND")
            && InventoryRules.matches(Items.DIAMOND, "DIAMOND"));
    check(
        "mixed invalid exact target does not partially apply",
        !InventoryRules.matches(Items.DIAMOND_SWORD, "minecraft:diamond_sword,missing_item"));
    var copper = InventoryRules.item("minecraft:copper_chestplate");
    check(
        "registered armor families include native copper where available",
        copper == null || InventoryRules.matchesArmor(copper, "COPPER"));
    String autoPath = "automatic-enchantments.tools.DIAMOND_PICKAXE";
    var autoSaved = mod.config.section(autoPath);
    mod.config.set(autoPath, Map.of("minecraft:efficiency", 300));
    ItemStack invalidAuto = new ItemStack(Items.DIAMOND_PICKAXE);
    check(
        "out of range automatic level is ignored without identity mutation",
        !mod.inventory.auto(invalidAuto)
            && !InventoryRules.data(invalidAuto).contains("ccr:auto_enchant_id"));
    mod.config.set(autoPath, autoSaved);
    mod.config.set(path, saved);
  }

  private void enchantingBooks() {
    String path = InventoryRules.ROOT + ".enchantment-caps";
    var saved = mod.config.entries(path);
    mod.config.set(
        path,
        List.of(
            Map.of(
                "enabled",
                true,
                "target",
                "ENCHANTED_BOOK",
                "enchantment",
                "minecraft:sharpness",
                "maximum",
                2)));
    var sharpness = mod.inventory.enchantments().getOrThrow(Enchantments.SHARPNESS);
    ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
    book.enchant(sharpness, 5);
    var removed = mod.inventory.enforceCaps(book);
    check(
        "stored book enchantments capped",
        EnchantmentHelper.getEnchantmentsForCrafting(book).getLevel(sharpness) == 2
            && removed.get(sharpness) == 3);
    List<Map<String, Object>> bookRules = new ArrayList<>();
    mod.inventory
        .enchantments()
        .listElements()
        .forEach(
            holder ->
                bookRules.add(
                    Map.of(
                        "enabled",
                        true,
                        "target",
                        "BOOK",
                        "enchantment",
                        mod.inventory.enchantmentId(holder),
                        "maximum",
                        1,
                        "refund-xp",
                        true,
                        "xp-per-removed-level",
                        10)));
    mod.config.set(path, bookRules);
    var menu =
        new net.minecraft.world.inventory.EnchantmentMenu(
            104,
            player.getInventory(),
            net.minecraft.world.inventory.ContainerLevelAccess.create(
                player.level(), new BlockPos(25, 100, 5)));
    player.containerMenu = menu;
    player.experienceLevel = 40;
    menu.getSlot(0).set(new ItemStack(Items.BOOK));
    menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
    // Search deterministic vanilla seeds for a real table selection with a removable level.
    int selectedSeed = -1;
    var allowed =
        mod.inventory
            .enchantments()
            .getOrThrow(net.minecraft.tags.EnchantmentTags.IN_ENCHANTING_TABLE);
    for (int seed = 0; seed < 100 && selectedSeed < 0; seed++) {
      var random = net.minecraft.util.RandomSource.create(seed + 2);
      var values =
          EnchantmentHelper.selectEnchantment(
              random, new ItemStack(Items.BOOK), 30, allowed.stream());
      if (values.size() > 1) values.remove(random.nextInt(values.size()));
      if (values.stream().anyMatch(value -> value.level() > 1)) selectedSeed = seed;
    }
    check("deterministic vanilla book table fixture", selectedSeed >= 0);
    menu.setData(0, selectedSeed);
    menu.costs[2] = 30;
    boolean accepted = menu.clickMenuButton(player, 2);
    ItemStack result = menu.getSlot(0).getItem();
    check(
        "real table converts and caps stored book",
        accepted
            && result.is(Items.ENCHANTED_BOOK)
            && !EnchantmentHelper.getEnchantmentsForCrafting(result).isEmpty()
            && EnchantmentHelper.getEnchantmentsForCrafting(result).entrySet().stream()
                .allMatch(entry -> entry.getIntValue() <= 1));
    check(
        "real table BOOK target refunds only three paid levels",
        player.experienceLevel == 40 && menu.getSlot(1).getItem().isEmpty());
    player.closeContainer();
    mod.config.set(path, saved);
  }

  private void loot() {
    var level = mod.server.overworld();
    BlockPos pos = new BlockPos(0, 105, 0);
    level.getChunkAt(pos);
    level.setBlockAndUpdate(pos, Blocks.IRON_ORE.defaultBlockState());
    ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
    mod.config.set("ore-drops.auto-smelt", true);
    mod.config.set("ore-drops.iron_ore.xp-bonus", 2);
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
    mod.config.set(
        "ore-drops.iron_ore.vanilla-overrides",
        List.of(Map.of("item", "minecraft:diamond", "minimum", 5, "maximum", 2, "chance", 100.0)));
    Block.dropResources(Blocks.IRON_ORE.defaultBlockState(), level, pos, null, player, pick);
    var entities = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3));
    check(
        "native ore smelt output",
        entities.stream().anyMatch(e -> e.getItem().is(Items.IRON_INGOT)));
    check(
        "invalid ore override never removes vanilla output",
        entities.stream().anyMatch(e -> e.getItem().is(Items.IRON_INGOT))
            && entities.stream().noneMatch(e -> e.getItem().is(Items.DIAMOND)));
    mod.config.set("ore-drops.iron_ore.vanilla-overrides", List.of());
    check(
        "native ore smelt XP",
        level
                .getEntitiesOfClass(
                    net.minecraft.world.entity.ExperienceOrb.class, new AABB(pos).inflate(3))
                .size()
            > xpBefore);
    for (ItemEntity entity : entities) entity.discard();
    ItemStack silk = pick.copy();
    silk.enchant(mod.inventory.enchantments().getOrThrow(Enchantments.SILK_TOUCH), 1);
    Block.dropResources(Blocks.IRON_ORE.defaultBlockState(), level, pos, null, player, silk);
    check(
        "native silk touch untouched",
        level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).stream()
            .anyMatch(e -> e.getItem().is(Items.IRON_ORE)));
    for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)))
      entity.discard();
    mod.config.set("better-sugar-cane.enabled", true);
    mod.config.set("better-sugar-cane.player.minimum", 3);
    mod.config.set("better-sugar-cane.player.maximum", 3);
    Block.dropResources(
        Blocks.SUGAR_CANE.defaultBlockState(), level, pos, null, player, ItemStack.EMPTY);
    check(
        "native sugar player final quantity",
        level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).stream()
            .anyMatch(e -> e.getItem().is(Items.SUGAR_CANE) && e.getItem().getCount() == 3));
    for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)))
      entity.discard();
    mod.mark(level, pos, Blocks.SUGAR_CANE.defaultBlockState());
    Block.dropResources(
        Blocks.SUGAR_CANE.defaultBlockState(), level, pos, null, player, ItemStack.EMPTY);
    check(
        "native sugar placed exclusion",
        level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).stream()
            .allMatch(e -> !e.getItem().is(Items.SUGAR_CANE) || e.getItem().getCount() == 1));
    mod.config.set("better-sugar-cane.support.minimum", 4);
    mod.config.set("better-sugar-cane.support.maximum", 4);
    Block.dropResources(Blocks.SUGAR_CANE.defaultBlockState(), level, pos.east());
    check(
        "native sugar support final quantity",
        level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).stream()
            .anyMatch(e -> e.getItem().is(Items.SUGAR_CANE) && e.getItem().getCount() == 4));
    mod.config.set(
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
    mod.config.set(
        "mobs.COW.drops",
        List.of(Map.of("item", "minecraft:emerald", "minimum", 1, "maximum", 1, "chance", 100.0)));
    var cow =
        net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.stream()
            .filter(
                type ->
                    net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                        .getKey(type)
                        .toString()
                        .equals("minecraft:cow"))
            .findFirst()
            .orElseThrow()
            .create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
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
  }

  private void survivalOnly() {
    var level = player.level();
    BlockPos pos = new BlockPos(25, 105, 1);
    ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
    for (GameType mode : List.of(GameType.ADVENTURE, GameType.CREATIVE, GameType.SPECTATOR)) {
      player.gameMode.changeGameModeForPlayer(mode);
      for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)))
        entity.discard();
      Block.dropResources(Blocks.IRON_ORE.defaultBlockState(), level, pos, null, player, pick);
      check(
          "non-survival ore keeps vanilla " + mode.getName(),
          level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).stream()
                  .anyMatch(entity -> entity.getItem().is(Items.RAW_IRON))
              && level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).stream()
                  .noneMatch(entity -> entity.getItem().is(Items.IRON_INGOT)));
      for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)))
        entity.discard();
      Block.dropResources(
          Blocks.SUGAR_CANE.defaultBlockState(), level, pos, null, player, ItemStack.EMPTY);
      check(
          "non-survival cane keeps vanilla " + mode.getName(),
          level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).stream()
              .anyMatch(
                  entity ->
                      entity.getItem().is(Items.SUGAR_CANE) && entity.getItem().getCount() == 1));
    }
    player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
  }

  private void promptCorrections() {
    steps.add(
        () -> {
          player.containerMenu = player.inventoryMenu;
          mod.config.set(
              InventoryRules.ROOT + ".armor-rules",
              List.of(Map.of("enabled", true, "target", "DIAMOND", "maximum", 0)));
          player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        });
    steps.add(
        () -> {
          check(
              "equipment correction happens next tick",
              player.getItemBySlot(EquipmentSlot.CHEST).isEmpty());
          mod.config.set(InventoryRules.ROOT + ".armor-rules", List.of());
          ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
          sword.enchant(mod.inventory.enchantments().getOrThrow(Enchantments.SHARPNESS), 5);
          player.getInventory().setItem(30, sword);
        });
    steps.add(
        () ->
            check(
                "inventory cap correction happens next tick",
                EnchantmentHelper.getItemEnchantmentLevel(
                        mod.inventory.enchantments().getOrThrow(Enchantments.SHARPNESS),
                        player.getInventory().getItem(30))
                    == 2));
  }

  /** Mechanistic lifecycle-hook test on a native server, not a real integrated client session. */
  private void lifecycleHooks() {
    RuleRuntime[] previous = new RuleRuntime[1];
    String[] maceId = new String[1];
    BlockPos origin = new BlockPos(-14, 100, -2);
    steps.add(
        () -> {
          mod.config.set("clean-stacks.players." + player.getUUID(), true);
          mod.mark(player.level(), origin, Blocks.OAK_LOG.defaultBlockState());
          mod.config.set(InventoryRules.ROOT + ".unique-mace.exists", false);
          ItemStack mace = new ItemStack(Items.MACE);
          mod.inventory.crafted(player, mace);
          maceId[0] = InventoryRules.data(mace).getStringOr("ccr:unique_mace_id", "");
          mod.menus.root(player);
        });
    steps.add(() -> clickLabel("Timber"));
    steps.add(
        () -> {
          player.closeContainer(); // Queue an old semantic parent before the server session ends.
          ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
          sword.enchant(mod.inventory.enchantments().getOrThrow(Enchantments.SHARPNESS), 5);
          player.getInventory().setItem(30, sword); // Queue this UUID in the old correction set.
          previous[0] = mod;
          var server = mod.server;
          CutCleanReborn.endSession(server);
          CutCleanReborn.beginSession(server);
          mod = CutCleanReborn.runtime;
          check("common lifecycle creates a new native runtime", mod != previous[0]);
          check(
              "common lifecycle preserves player UUID preference",
              mod.config.bool("clean-stacks.players." + player.getUUID(), false));
          check(
              "common lifecycle preserves world-local placed origins",
              mod.placed(player.level(), origin));
          check(
              "common lifecycle preserves unique mace identity",
              mod.inventory.maceBlocked()
                  && maceId[0].equals(
                      mod.config.text(InventoryRules.ROOT + ".unique-mace.id", "")));
          player.getInventory().setChanged();
        });
    steps.add(
        () -> {
          check(
              "new lifecycle corrects the same UUID at next tick",
              EnchantmentHelper.getItemEnchantmentLevel(
                      mod.inventory.enchantments().getOrThrow(Enchantments.SHARPNESS),
                      player.getInventory().getItem(30))
                  == 2);
          check(
              "old lifecycle menu cannot reopen in new runtime",
              player.containerMenu == player.inventoryMenu);
          mod.remove(player.level(), origin);
          mod.config.set("clean-stacks.players." + player.getUUID(), false);
        });
  }

  private void vanillaMenuActions() {
    steps.add(
        () -> {
          mod.config.set("mobs.COW.vanilla-overrides", List.of());
          mod.config.set("mobs.COW.xp-bonus", 17);
          mod.config.set("mobs.COW.cooked-food", false);
          mod.menus.root(player);
        });
    steps.add(() -> clickLabel("Mob Drops"));
    steps.add(() -> clickLabel("Search Mobs"));
    steps.add(
        () -> {
          ((net.minecraft.world.inventory.AnvilMenu) player.containerMenu)
              .setItemName("minecraft:cow");
          player.containerMenu.clicked(2, 0, ClickType.PICKUP, player);
        });
    steps.add(() -> clickLabel("Cook Food"));
    steps.add(
        () -> {
          check(
              "restoring inherited cooking removes mob override",
              !mod.config.has("mobs.COW.cooked-food"));
          clickLabel("Cook Food");
        });
    steps.add(() -> clickLabel("Vanilla Drops"));
    steps.add(() -> clickLabel("Preview Looting III"));
    steps.add(() -> clickLabel("Beef"));
    steps.add(
        () -> {
          var base =
              mod.catalog.mob("COW", false).stream()
                  .filter(value -> value.get("item").equals("minecraft:beef"))
                  .findFirst()
                  .orElseThrow();
          var rule = mod.config.entries("mobs.COW.vanilla-overrides").getFirst();
          check(
              "Looting preview creates override from base not boosted quantities",
              base.get("minimum").equals(rule.get("minimum"))
                  && base.get("maximum").equals(rule.get("maximum")));
          player.closeContainer();
        });
    steps.add(() -> clickLabel("Beef", 1));
    steps.add(
        () ->
            check(
                "right click disables native vanilla drop",
                mod.config.entries("mobs.COW.vanilla-overrides").stream()
                    .anyMatch(
                        rule ->
                            rule.get("chance") instanceof Number chance
                                && chance.doubleValue() == 0)));
    steps.add(() -> clickLabel("Beef", 1));
    steps.add(
        () -> {
          check(
              "right click restores native vanilla drop",
              mod.config.entries("mobs.COW.vanilla-overrides").isEmpty());
          player.closeContainer();
        });
    steps.add(
        () -> {
          mod.config.set(
              "mobs.COW.drops",
              List.of(
                  Map.of(
                      "item", "minecraft:diamond", "minimum", 1, "maximum", 1, "chance", 100.0)));
          clickLabel("Reset Drops");
        });
    steps.add(() -> clickLabel("Confirm"));
    steps.add(
        () ->
            check(
                "drop-only reset retains XP and cooking",
                mod.config.entries("mobs.COW.drops").isEmpty()
                    && mod.config.integer("mobs.COW.xp-bonus", 0, 0, 100) == 17
                    && !mod.config.bool("mobs.COW.cooked-food", true)));
    steps.add(() -> mod.menus.root(player));
    steps.add(() -> clickLabel("Timber"));
    steps.add(
        () -> {
          player.closeContainer();
          mod.menus.closeAll();
        });
    steps.add(
        () ->
            check(
                "reload invalidates queued Escape return",
                player.containerMenu == player.inventoryMenu));
  }

  private void concurrentStuffEditors(boolean cap) {
    String path = InventoryRules.ROOT + (cap ? ".enchantment-caps" : ".armor-rules");
    var saved = mod.config.entries(path);
    int originalMaximum = cap ? 5 : 4;
    int[] neighborBeforeDeletion = {originalMaximum};
    steps.add(
        () -> {
          List<Map<String, Object>> rules = new ArrayList<>();
          for (String id : List.of("qa-concurrent-A", "qa-concurrent-B", "qa-concurrent-C")) {
            Map<String, Object> rule = new java.util.LinkedHashMap<>();
            rule.put("id", id);
            rule.put("enabled", false);
            rule.put("target", cap ? "SWORDS" : "DIAMOND");
            rule.put("maximum", originalMaximum);
            if (cap) rule.put("enchantment", "minecraft:sharpness");
            rules.add(rule);
          }
          mod.config.set(path, rules);
          mod.menus.root(player);
        });
    steps.add(() -> clickLabel("Stuff Rules"));
    steps.add(() -> clickLabel(cap ? "Enchantment Caps" : "Armor Limits"));
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 11), 0, ClickType.PICKUP, player));
    steps.add(() -> clickLabel("Maximum"));
    steps.add(
        () -> {
          // A is in B's input screen when another administrator deletes the preceding rule A.
          var rules = mod.config.entries(path);
          rules.removeIf(rule -> rule.get("id").equals("qa-concurrent-A"));
          mod.config.set(path, rules);
          ((net.minecraft.world.inventory.AnvilMenu) player.containerMenu).setItemName("1");
          player.containerMenu.clicked(2, 0, ClickType.PICKUP, player);
        });
    steps.add(
        () -> {
          var rules = mod.config.entries(path);
          check(
              "native concurrent " + (cap ? "cap" : "armor") + " edit follows rule UUID",
              rules.stream()
                      .anyMatch(
                          rule ->
                              rule.get("id").equals("qa-concurrent-B")
                                  && RuleMath.number(rule.get("maximum"), 0) == 1)
                  && rules.stream()
                      .anyMatch(
                          rule ->
                              rule.get("id").equals("qa-concurrent-C")
                                  && RuleMath.number(rule.get("maximum"), 0) == originalMaximum));
          mod.menus.root(player);
        });
    steps.add(() -> clickLabel("Stuff Rules"));
    steps.add(() -> clickLabel(cap ? "Enchantment Caps" : "Armor Limits"));
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 10), 0, ClickType.PICKUP, player));
    steps.add(() -> clickLabel("Maximum"));
    steps.add(
        () -> {
          var rules = mod.config.entries(path);
          neighborBeforeDeletion[0] =
              rules.stream()
                  .filter(rule -> rule.get("id").equals("qa-concurrent-C"))
                  .mapToInt(rule -> RuleMath.number(rule.get("maximum"), 0))
                  .findFirst()
                  .orElseThrow();
          rules.removeIf(rule -> rule.get("id").equals("qa-concurrent-B"));
          mod.config.set(path, rules);
          ((net.minecraft.world.inventory.AnvilMenu) player.containerMenu).setItemName("2");
          player.containerMenu.clicked(2, 0, ClickType.PICKUP, player);
        });
    steps.add(
        () -> {
          var rules = mod.config.entries(path);
          check(
              "native deleted " + (cap ? "cap" : "armor") + " edit never touches neighbor",
              rules.size() == 1
                  && rules.getFirst().get("id").equals("qa-concurrent-C")
                  && RuleMath.number(rules.getFirst().get("maximum"), 0)
                      == neighborBeforeDeletion[0]);
          mod.config.set(path, saved);
          mod.menus.root(player);
        });
    steps.add(() -> resetConcurrentRules(cap));
    steps.add(() -> clickLabel("Stuff Rules"));
    steps.add(() -> clickLabel(cap ? "Enchantment Caps" : "Armor Limits"));
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 11), 0, ClickType.PICKUP, player));
    steps.add(() -> clickLabel("Delete Rule"));
    steps.add(
        () -> {
          mod.config.removeEntryById(path, "qa-concurrent-A");
          clickLabel("Confirm");
        });
    steps.add(
        () ->
            check(
                "native deferred "
                    + (cap ? "cap" : "armor")
                    + " delete follows UUID after reordering",
                mod.config.entries(path).size() == 1
                    && mod.config.entryById(path, "qa-concurrent-C") != null));
    steps.add(() -> resetConcurrentRules(cap));
    steps.add(() -> clickLabel("Stuff Rules"));
    steps.add(() -> clickLabel(cap ? "Enchantment Caps" : "Armor Limits"));
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 11), 0, ClickType.PICKUP, player));
    steps.add(() -> clickLabel("Copy to Another Target"));
    steps.add(
        () -> {
          mod.config.removeEntryById(path, "qa-concurrent-B");
          ((net.minecraft.world.inventory.AnvilMenu) player.containerMenu)
              .setItemName(cap ? "TOOLS" : "IRON");
          player.containerMenu.clicked(2, 0, ClickType.PICKUP, player);
        });
    steps.add(
        () ->
            check(
                "native deleted "
                    + (cap ? "cap" : "armor")
                    + " rule cannot be copied by stale input",
                mod.config.entries(path).size() == 2
                    && mod.config.entryById(path, "qa-concurrent-B") == null));
    steps.add(
        () -> {
          resetConcurrentRules(cap);
          mod.config.editEntryById(path, "qa-concurrent-B", rule -> rule.put("maximum", 1));
        });
    steps.add(() -> clickLabel("Stuff Rules"));
    steps.add(() -> clickLabel(cap ? "Enchantment Caps" : "Armor Limits"));
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 11), 0, ClickType.PICKUP, player));
    steps.add(() -> clickLabel("Target"));
    steps.add(
        () -> {
          mod.config.removeEntryById(path, "qa-concurrent-A");
          player.closeContainer();
        });
    steps.add(
        () -> {
          check(
              "native Escape parent retains "
                  + (cap ? "cap" : "armor")
                  + " identity after reordering",
              java.util.stream.IntStream.range(9, 45)
                  .mapToObj(
                      slot ->
                          player.containerMenu.getSlot(slot).getItem().getHoverName().getString())
                  .anyMatch(name -> name.contains("Maximum") && name.endsWith("1")));
          mod.config.set(path, saved);
          mod.menus.root(player);
        });
  }

  private void resetConcurrentRules(boolean cap) {
    String path = InventoryRules.ROOT + (cap ? ".enchantment-caps" : ".armor-rules");
    List<Map<String, Object>> rules = new ArrayList<>();
    for (String id : List.of("qa-concurrent-A", "qa-concurrent-B", "qa-concurrent-C")) {
      Map<String, Object> rule = new java.util.LinkedHashMap<>();
      rule.put("id", id);
      rule.put("enabled", false);
      rule.put("target", cap ? "SWORDS" : "DIAMOND");
      rule.put("maximum", cap ? 5 : 4);
      if (cap) rule.put("enchantment", "minecraft:sharpness");
      rules.add(rule);
    }
    mod.config.set(path, rules);
    mod.menus.root(player);
  }

  private void mace() {
    mod.config.set(InventoryRules.ROOT + ".unique-mace.enabled", true);
    mod.config.set(InventoryRules.ROOT + ".unique-mace.exists", false);
    ItemStack mace = new ItemStack(Items.MACE);
    mod.inventory.crafted(player, mace);
    check(
        "mace claim persisted",
        mod.inventory.maceBlocked()
            && !InventoryRules.data(mace).getStringOr("ccr:unique_mace_id", "").isBlank());
    ItemStack duplicate = new ItemStack(Items.MACE);
    mod.inventory.crafted(player, duplicate);
    check("mace second craft rejected", duplicate.isEmpty());
    ItemEntity entity = new ItemEntity(player.level(), 0, 110, 0, mace.copy());
    player.level().addFreshEntity(entity);
    entity.discard();
    check("mace arbitrary removal/unload not released", mod.inventory.maceBlocked());
    mod.inventory.destroyed(mace);
    check("mace true destruction released", !mod.inventory.maceBlocked());
  }

  private void maceLifecycle() throws Exception {
    var level = player.level();
    ItemStack broken = new ItemStack(Items.MACE);
    mod.inventory.crafted(player, broken);
    broken.setDamageValue(broken.getMaxDamage() - 1);
    player.setItemSlot(EquipmentSlot.MAINHAND, broken);
    broken.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
    check(
        "native mace durability destruction releases identity",
        broken.isEmpty() && !mod.inventory.maceBlocked());
    ItemStack aged = new ItemStack(Items.MACE);
    mod.inventory.crafted(player, aged);
    ItemEntity despawn = new ItemEntity(level, 0.5, 112, 0.5, aged);
    level.addFreshEntity(despawn);
    ((QaItemEntityAge) (Object) despawn).ccr$setAge(5999);
    despawn.tick();
    check(
        "native mace real despawn releases identity",
        despawn.isRemoved() && !mod.inventory.maceBlocked());
    ItemStack picked = new ItemStack(Items.MACE);
    mod.inventory.crafted(player, picked);
    ItemEntity pickup = new ItemEntity(level, 0.5, 112, 0.5, picked);
    pickup.setNoPickUpDelay();
    level.addFreshEntity(pickup);
    pickup.playerTouch(player);
    check(
        "native mace pickup does not release identity",
        pickup.isRemoved() && mod.inventory.maceBlocked());
    mod.inventory.destroyed(picked);
    ItemStack falling = new ItemStack(Items.MACE);
    mod.inventory.crafted(player, falling);
    ItemEntity voidDrop = new ItemEntity(level, 0.5, -2048, 0.5, falling);
    level.addFreshEntity(voidDrop);
    voidDrop.checkBelowWorld();
    check("native mace void destruction releases identity", !mod.inventory.maceBlocked());
  }

  private void origins() throws Exception {
    var level = mod.server.overworld();
    BlockPos pos = new BlockPos(-16, -60, -1);
    mod.mark(level, pos, Blocks.OAK_LOG.defaultBlockState());
    mod.flushState();
    RuleConfig reloaded =
        new RuleConfig(
            mod.server
                .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("data/cutcleanreborn-state.yml"),
            null);
    Object placed = reloaded.get("placed");
    check(
        "placed marker restart persistence",
        placed instanceof List<?> positions
            && positions.contains(level.dimension().toString() + "/" + pos.asLong()));
    check("placed marker negative coordinates", !mod.placed(level, pos.above()));
    mod.remove(level, pos);
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
            && !InventoryRules.data(mace).getStringOr("ccr:unique_mace_id", "").isBlank()
            && mod.inventory.maceBlocked());
    menu.setCarried(ItemStack.EMPTY);
    menu.getSlot(1).set(new ItemStack(Items.HEAVY_CORE));
    menu.getSlot(4).set(new ItemStack(Items.BREEZE_ROD));
    menu.clicked(0, 0, ClickType.PICKUP, player);
    check(
        "native duplicate craft blocked before ingredients",
        menu.getCarried().isEmpty() && menu.getSlot(1).getItem().is(Items.HEAVY_CORE));
    mod.inventory.destroyed(mace);
    for (ClickType type : List.of(ClickType.SWAP, ClickType.THROW)) {
      menu.clicked(0, 0, type, player);
      check(
          "unique craft rejects " + type + " before ingredients",
          !mod.inventory.maceBlocked() && menu.getSlot(1).getItem().is(Items.HEAVY_CORE));
    }
    menu.setCarried(new ItemStack(Items.STICK));
    menu.clicked(0, 0, ClickType.PICKUP, player);
    check(
        "unique craft rejects occupied cursor before ingredients",
        !mod.inventory.maceBlocked()
            && menu.getSlot(1).getItem().is(Items.HEAVY_CORE)
            && menu.getCarried().is(Items.STICK));
    menu.setCarried(ItemStack.EMPTY);
    menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
    check(
        "native shift unique craft rejected safely",
        !mod.inventory.maceBlocked() && menu.getSlot(1).getItem().is(Items.HEAVY_CORE));
    player.closeContainer();
  }

  private void anvilTransaction() {
    player.experienceLevel = 40;
    var menu =
        new net.minecraft.world.inventory.AnvilMenu(
            100, player.getInventory(), net.minecraft.world.inventory.ContainerLevelAccess.NULL);
    player.containerMenu = menu;
    ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
    sword.setDamageValue(100);
    sword.enchant(mod.inventory.enchantments().getOrThrow(Enchantments.SHARPNESS), 5);
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
                mod.inventory.enchantments().getOrThrow(Enchantments.SHARPNESS), menu.getCarried())
            == 2);
    check("native anvil refund paid transaction only", player.experienceLevel == before);
    menu.setCarried(ItemStack.EMPTY);
    player.closeContainer();
    List<ItemStack> savedInventory = new ArrayList<>();
    for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
      savedInventory.add(player.getInventory().getItem(slot).copy());
    for (int slot = 0; slot < 36; slot++)
      player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
    menu =
        new net.minecraft.world.inventory.AnvilMenu(
            105, player.getInventory(), net.minecraft.world.inventory.ContainerLevelAccess.NULL);
    player.containerMenu = menu;
    sword = new ItemStack(Items.DIAMOND_SWORD);
    sword.setDamageValue(100);
    sword.enchant(mod.inventory.enchantments().getOrThrow(Enchantments.SHARPNESS), 5);
    menu.getSlot(0).set(sword);
    menu.getSlot(1).set(new ItemStack(Items.DIAMOND));
    int beforeShift = player.experienceLevel;
    menu.clicked(2, 0, ClickType.QUICK_MOVE, player);
    check(
        "full inventory anvil shift does not charge",
        player.experienceLevel == beforeShift && !menu.getSlot(2).getItem().isEmpty());
    player.getInventory().setItem(0, ItemStack.EMPTY);
    menu.quickMoveStack(player, 2);
    check(
        "native anvil shift caps before inventory transfer",
        player.getInventory().getItem(0).is(Items.DIAMOND_SWORD)
            && EnchantmentHelper.getItemEnchantmentLevel(
                    mod.inventory.enchantments().getOrThrow(Enchantments.SHARPNESS),
                    player.getInventory().getItem(0))
                == 2);
    check(
        "failed shift retains exact paid refund journal",
        player.experienceLevel == beforeShift && menu.getSlot(0).getItem().isEmpty());
    sword = new ItemStack(Items.DIAMOND_SWORD);
    sword.setDamageValue(100);
    sword.enchant(mod.inventory.enchantments().getOrThrow(Enchantments.SHARPNESS), 2);
    menu.getSlot(0).set(sword);
    menu.getSlot(1).set(new ItemStack(Items.DIAMOND));
    int cost = menu.getCost(), beforeUncapped = player.experienceLevel;
    menu.clicked(2, 0, ClickType.PICKUP, player);
    check(
        "new anvil recipe cannot reuse previous refund",
        cost > 0 && player.experienceLevel == beforeUncapped - cost);
    menu.setCarried(ItemStack.EMPTY);
    player.closeContainer();
    for (int slot = 0; slot < savedInventory.size(); slot++)
      player.getInventory().setItem(slot, savedInventory.get(slot));
  }

  private void cleanScope() {
    List<ItemStack> saved = new ArrayList<>();
    for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
      saved.add(player.getInventory().getItem(slot).copy());
      player.getInventory().setItem(slot, ItemStack.EMPTY);
    }
    String preference = "clean-stacks.players." + player.getUUID();
    boolean before = mod.inventory.cleanEnabled(player);
    var woodBefore = mod.config.section("clean-stacks.wood");
    mod.inventory.setCleanTarget(Items.BIRCH_LOG, Items.OAK_LOG);
    mod.config.set(preference, false);
    player.getInventory().setItem(6, new ItemStack(Items.BIRCH_LOG, 17));
    player.getInventory().setItem(7, new ItemStack(Items.COBBLESTONE, 23));
    var source = mod.server.createCommandSourceStack().withEntity(player);
    mod.server.getCommands().performPrefixedCommand(source, "cleanstacks on");
    check("clean enable changes preference without converting inventory",
        mod.inventory.cleanEnabled(player)
            && player.getInventory().getItem(6).is(Items.BIRCH_LOG)
            && player.getInventory().getItem(6).getCount() == 17);
    mod.server.getCommands().performPrefixedCommand(source, "give @s minecraft:spruce_log 9");
    mod.inventory.correct(player);
    check("clean ignores given items and later inventory corrections",
        cleanCount(Items.SPRUCE_LOG) == 9
            && cleanCount(Items.BIRCH_LOG) == 17
            && cleanCount(Items.COBBLESTONE) == 23);
    var level = player.level();
    ItemEntity pickup = new ItemEntity(level, player.getX(), player.getY(), player.getZ(),
        new ItemStack(Items.ACACIA_LOG, 5));
    pickup.setNoPickUpDelay();
    level.addFreshEntity(pickup);
    pickup.playerTouch(player);
    mod.inventory.correct(player);
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
    mod.server.getCommands().performPrefixedCommand(source, "cleanstacks off");
    level.setBlockAndUpdate(pos, Blocks.BIRCH_LOG.defaultBlockState());
    check("clean disabled native block break succeeds", player.gameMode.destroyBlock(pos));
    drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
    check("clean disabled block drop retains vanilla material and count",
        drops.stream().filter(e -> e.getItem().is(Items.BIRCH_LOG))
            .mapToInt(e -> e.getItem().getCount()).sum() == 1
            && drops.stream().noneMatch(e -> e.getItem().is(Items.OAK_LOG)));
    drops.forEach(ItemEntity::discard);
    mod.config.set(preference, before);
    mod.config.set("clean-stacks.wood", woodBefore);
    for (int slot = 0; slot < saved.size(); slot++)
      player.getInventory().setItem(slot, saved.get(slot));
  }

  private int cleanCount(net.minecraft.world.item.Item item) {
    return java.util.stream.IntStream.range(0, player.getInventory().getContainerSize())
        .mapToObj(slot -> player.getInventory().getItem(slot))
        .filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
  }

  private void craftPreview() {
    mod.config.set("clean-stacks.players." + player.getUUID(), true);
    mod.inventory.setCleanTarget(Items.BIRCH_PLANKS, Items.OAK_PLANKS);
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
    mod.config.set("clean-stacks.players." + player.getUUID(), false);
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
          mod.config.set("tree-cutter.enabled", true);
          mod.config.set("tree-cutter.break-time.enabled", false);
          mod.config.set("tree-cutter.blocks-per-tick", 2);
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
            mod.mark(player.level(), placedTree.above(i), Blocks.OAK_LOG.defaultBlockState());
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
          mod.config.set("tree-cutter.break-time.enabled", true);
          mod.config.set("tree-cutter.break-time.percent", 100.0);
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
          mod.config.set("tree-cutter.break-time.enabled", false);
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
    var level = player.level();
    level.getChunkAt(base);
    for (int y = 0; y < 4; y++) {
      level.setBlockAndUpdate(base.above(y), Blocks.OAK_LOG.defaultBlockState());
      mod.remove(level, base.above(y));
    }
    level.setBlockAndUpdate(
        base.above(4),
        Blocks.OAK_LEAVES
            .defaultBlockState()
            .setValue(LeavesBlock.PERSISTENT, false)
            .setValue(LeavesBlock.DISTANCE, 1));
    player.setPos(base.getX() + 0.5, base.getY(), base.getZ() - 1);
  }

  private void catalogue() {
    var level = mod.server.overworld();
    long before =
        java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(), false).count();
    ItemStack hand = player.getMainHandItem().copy();
    var plain = mod.catalog.mob("COW", false);
    var looting = mod.catalog.mob("COW", true);
    check(
        "native vanilla catalogue cow entries",
        plain.stream().anyMatch(rule -> "minecraft:beef".equals(rule.get("item"))));
    check(
        "native vanilla catalogue looting amounts",
        looting.stream()
                .filter(rule -> "minecraft:beef".equals(rule.get("item")))
                .mapToInt(rule -> RuleMath.number(rule.get("maximum"), 0))
                .max()
                .orElse(0)
            >= plain.stream()
                .filter(rule -> "minecraft:beef".equals(rule.get("item")))
                .mapToInt(rule -> RuleMath.number(rule.get("maximum"), 0))
                .max()
                .orElse(1));
    mod.catalog.mob("WITHER", false);
    check(
        "native preview never spawns creatures or bosses",
        before
            == java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(), false)
                .count());
    check(
        "native preview never changes player equipment",
        ItemStack.matches(hand, player.getMainHandItem()));
    check(
        "ore catalogue Fortune description",
        RuleMath.number(VanillaCatalog.ore("diamond_ore", true).get("maximum"), 0) == 4);
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

  private void clickLabel(String text) {
    clickLabel(text, 0);
  }

  private void clickLabel(String text, int button) {
    for (int slot = 9; slot < player.containerMenu.slots.size() - 36; slot++)
      if (player
          .containerMenu
          .getSlot(slot)
          .getItem()
          .getHoverName()
          .getString()
          .toLowerCase(java.util.Locale.ROOT)
          .contains(text.toLowerCase(java.util.Locale.ROOT))) {
        player.containerMenu.clicked(slot, button, ClickType.PICKUP, player);
        return;
      }
    throw new IllegalStateException("Menu button not found: " + text);
  }

  private void visualSelectors() {
    steps.add(() -> mod.menus.root(player));
    steps.add(() -> clickLabel("Automatic Enchantments"));
    steps.add(() -> clickLabel("Tools & Weapons"));
    steps.add(() -> clickLabel("Bow"));
    steps.add(() -> clickLabel("Choose Enchantment"));
    steps.add(
        () ->
            check(
                "native compatible enchantment picker opens",
                player
                    .containerMenu
                    .getSlot(4)
                    .getItem()
                    .getHoverName()
                    .getString()
                    .contains("Enchantments")));
    steps.add(() -> player.containerMenu.clicked(menuSlot(player, 9), 0, ClickType.PICKUP, player));
    steps.add(
        () -> {
          check(
              "native picker level input opens",
              player.containerMenu instanceof net.minecraft.world.inventory.AnvilMenu);
          ((net.minecraft.world.inventory.AnvilMenu) player.containerMenu).setItemName("2");
          player.containerMenu.clicked(2, 0, ClickType.PICKUP, player);
        });
    steps.add(
        () ->
            check(
                "native visual enchantment selection persists",
                !mod.config.section("automatic-enchantments.tools.BOW").isEmpty()));
    steps.add(() -> clickLabel("Copy to Same Material"));
    steps.add(() -> clickLabel("Confirm"));
    steps.add(
        () ->
            check(
                "native standalone Bow material copy does not crash",
                player
                    .containerMenu
                    .getSlot(4)
                    .getItem()
                    .getHoverName()
                    .getString()
                    .contains("Bow")));
    steps.add(() -> mod.menus.root(player));
    steps.add(() -> clickLabel("Stuff Rules"));
    steps.add(() -> clickLabel("Armor Limits"));
    steps.add(() -> clickLabel("Add Rule"));
    steps.add(() -> clickLabel("Target"));
    steps.add(
        () ->
            check(
                "native stuff target category picker opens",
                player
                    .containerMenu
                    .getSlot(4)
                    .getItem()
                    .getHoverName()
                    .getString()
                    .contains("Choose")));
    steps.add(() -> clickLabel("Specific Items"));
    steps.add(() -> clickLabel("Diamond chestplate"));
    steps.add(
        () ->
            check(
                "native exact armor selection persists",
                mod.config.entries(InventoryRules.ROOT + ".armor-rules").stream()
                    .anyMatch(rule -> "minecraft:diamond_chestplate".equals(rule.get("target")))));
    steps.add(() -> player.closeContainer());
    steps.add(
        () ->
            check(
                "Escape exact selection returns target picker",
                player
                    .containerMenu
                    .getSlot(4)
                    .getItem()
                    .getHoverName()
                    .getString()
                    .contains("Choose")));
  }

  private void menus() {
    mod.menus.root(player);
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
    steps.add(() -> mod.menus.main(player));
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
  }

  private void menuPageReturn() {
    String[] childTitle = {""};
    steps.add(() -> mod.menus.main(player));
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

  private void finish() {
    try {
      if (player != null) player.containerMenu = player.inventoryMenu;
      if (mod != null && original != null) {
        mod.config.restore(original);
      }
      Map<String, Object> result =
          Map.of(
              "platform",
              "Fabric",
              "minecraft",
              net.minecraft.SharedConstants.getCurrentVersion().name(),
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
      CutCleanReborn.LOG.info(
          "CCR native verification: {} passed, {} failed", passed.size(), failed.size());
      if (!failed.isEmpty()) CutCleanReborn.LOG.error("CCR failures: {}", failed);
    } catch (Exception error) {
      CutCleanReborn.LOG.error("CCR report error", error);
    }
  }
}
