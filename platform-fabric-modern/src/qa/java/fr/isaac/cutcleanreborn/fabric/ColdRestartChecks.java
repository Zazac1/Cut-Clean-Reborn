package fr.isaac.cutcleanreborn.fabric;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Three different official-server JVMs, with a real chunk chest storing native item NBT. */
public final class ColdRestartChecks implements ModInitializer {
  private static final BlockPos CHEST = new BlockPos(2, 100, 2);
  private static final BlockPos WOOD = new BlockPos(3, 100, 2);
  private static final BlockPos CANE = new BlockPos(4, 100, 2);
  private static final UUID PLAYER = UUID.fromString("e9a57af8-3bb6-4e70-9ccb-04164a8f7701");
  private static final String MACE = InventoryRules.ROOT + ".unique-mace";
  private final List<String> passed = new ArrayList<>(), failed = new ArrayList<>();
  private int ticks;

  @Override
  public void onInitialize() {
    ServerTickEvents.END_SERVER_TICK.register(
        server -> {
          String stage = System.getProperty("ccr.persistence");
          if (!Boolean.getBoolean("ccr.qa")
              || stage == null
              || CutCleanReborn.runtime == null
              || ++ticks != 25) return;
          var mod = CutCleanReborn.runtime;
          String worldNonce = "";
          try {
            var level = server.overworld();
            level.getChunkAt(CHEST);
            RuleConfig expected =
                new RuleConfig(
                    FabricLoader.getInstance().getGameDir().resolve("cold-expected.yml"), null);
            if (stage.equals("write")) {
              mod.config.set("persistence-verification.value", 17);
              mod.config.set("clean-stacks.players." + PLAYER, true);
              mod.config.set("automatic-enchantments.enabled", true);
              mod.config.set(
                  "automatic-enchantments.tools.DIAMOND_PICKAXE",
                  Map.of("minecraft:efficiency", 3));
              mod.config.set(InventoryRules.ROOT + ".enabled", true);
              mod.config.set(MACE + ".enabled", true);
              mod.config.set(MACE + ".exists", false);
              mod.config.set(MACE + ".id", "");
              level.setBlockAndUpdate(CHEST, Blocks.CHEST.defaultBlockState());
              level.setBlockAndUpdate(WOOD, Blocks.OAK_LOG.defaultBlockState());
              level.setBlockAndUpdate(CANE.below(), Blocks.DIRT.defaultBlockState());
              level.setBlockAndUpdate(CANE.below().east(), Blocks.WATER.defaultBlockState());
              level.setBlockAndUpdate(CANE, Blocks.SUGAR_CANE.defaultBlockState());
              mod.mark(level, WOOD, Blocks.OAK_LOG.defaultBlockState());
              mod.mark(level, CANE, Blocks.SUGAR_CANE.defaultBlockState());
              ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(CHEST);
              chest.clearContent();
              ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
              check("native automatic item prepared", mod.inventory.auto(pick));
              ItemStack mace = new ItemStack(Items.MACE);
              mod.inventory.crafted(actor(mod), mace);
              chest.setItem(0, pick);
              chest.setItem(1, mace);
              ItemStack nonce = new ItemStack(Items.PAPER);
              var nonceData = InventoryRules.data(nonce);
              nonceData.putString("ccr:verification_world_nonce", UUID.randomUUID().toString());
              InventoryRules.data(nonce, nonceData);
              chest.setItem(2, nonce);
              chest.setChanged();
              expected.batch(
                  Map.of(
                      "auto-id",
                      InventoryRules.data(pick).getStringOr("ccr:auto_enchant_id", ""),
                      "mace-id",
                      InventoryRules.data(mace).getStringOr("ccr:unique_mace_id", ""),
                      "world-nonce",
                      nonceData.getStringOr("ccr:verification_world_nonce", "")));
              check(
                  "native chest contains both marked items",
                  !expected.text("auto-id", "").isBlank()
                      && !expected.text("mace-id", "").isBlank());
              check("world-local identity reserved before shutdown", mod.inventory.maceBlocked());
              check(
                  "both player-placed markers recorded",
                  mod.placed(level, WOOD) && mod.placed(level, CANE));
              mod.flushState();
            } else if (stage.equals("read")) {
              check(
                  "YAML value survives first cold restart",
                  mod.config.integer("persistence-verification.value", 0, 0, 100) == 17);
              check(
                  "UUID player preference survives first cold restart",
                  mod.config.bool("clean-stacks.players." + PLAYER, false));
              check(
                  "actual log and cane blocks survive world save",
                  level.getBlockState(WOOD).is(Blocks.OAK_LOG)
                      && level.getBlockState(CANE).is(Blocks.SUGAR_CANE));
              check(
                  "placed log and cane markers survive first cold restart",
                  mod.placed(level, WOOD) && mod.placed(level, CANE));
              ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(CHEST);
              ItemStack pick = chest.getItem(0), mace = chest.getItem(1);
              check(
                  "auto UUID survives native chest NBT decode",
                  pick.is(Items.DIAMOND_PICKAXE)
                      && expected
                          .text("auto-id", "missing")
                          .equals(
                              InventoryRules.data(pick).getStringOr("ccr:auto_enchant_id", "")));
              check(
                  "auto enchantment level survives native chest NBT",
                  EnchantmentHelper.getItemEnchantmentLevel(
                          mod.inventory.enchantments().getOrThrow(Enchantments.EFFICIENCY), pick)
                      == 3);
              check(
                  "unchanged automatic item is not reprocessed after restart",
                  !mod.inventory.auto(pick));
              check(
                  "mace UUID survives native chest NBT decode",
                  mace.is(Items.MACE)
                      && expected
                          .text("mace-id", "missing")
                          .equals(InventoryRules.data(mace).getStringOr("ccr:unique_mace_id", "")));
              check(
                  "world lock and item UUID match after restart",
                  mod.inventory.maceBlocked()
                      && mod.config
                          .text(MACE + ".id", "")
                          .equals(expected.text("mace-id", "missing")));
              ItemStack duplicate = new ItemStack(Items.MACE);
              mod.inventory.crafted(actor(mod), duplicate);
              check("cold-restored unique lock blocks a duplicate", duplicate.isEmpty());
              mod.inventory.destroyed(mace);
              chest.setItem(1, ItemStack.EMPTY);
              chest.setChanged();
              mod.remove(level, WOOD);
              mod.remove(level, CANE);
              level.removeBlock(WOOD, false);
              level.removeBlock(CANE, false);
              mod.config.set("clean-stacks.players." + PLAYER, false);
              mod.config.set("persistence-verification.value", 23);
              mod.flushState();
              check(
                  "destruction releases unique lock before second shutdown",
                  !mod.inventory.maceBlocked());
            } else if (stage.equals("reread")) {
              check(
                  "updated YAML survives second cold restart",
                  mod.config.integer("persistence-verification.value", 0, 0, 100) == 23);
              check(
                  "disabled UUID preference does not resurrect",
                  !mod.config.bool("clean-stacks.players." + PLAYER, true));
              check(
                  "removed origin markers do not resurrect",
                  !mod.placed(level, WOOD) && !mod.placed(level, CANE));
              check(
                  "removed actual blocks remain removed",
                  level.getBlockState(WOOD).isAir() && level.getBlockState(CANE).isAir());
              check(
                  "destroyed mace lock does not resurrect",
                  !mod.inventory.maceBlocked() && mod.config.text(MACE + ".id", "").isBlank());
              ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(CHEST);
              check("destroyed mace stays absent from chest NBT", chest.getItem(1).isEmpty());
              check(
                  "surviving automatic item keeps original UUID across two restarts",
                  expected
                      .text("auto-id", "missing")
                      .equals(
                          InventoryRules.data(chest.getItem(0))
                              .getStringOr("ccr:auto_enchant_id", "")));
              ItemStack replacement = new ItemStack(Items.MACE);
              mod.inventory.crafted(actor(mod), replacement);
              check(
                  "a new distinct mace can be crafted after cold-restored destruction",
                  !replacement.isEmpty()
                      && mod.inventory.maceBlocked()
                      && !InventoryRules.data(replacement)
                          .getStringOr("ccr:unique_mace_id", "")
                          .equals(expected.text("mace-id", "")));
              chest.setItem(1, replacement);
              chest.setChanged();
            } else throw new IllegalArgumentException("Unknown persistence stage " + stage);
            var chest = (ChestBlockEntity) level.getBlockEntity(CHEST);
            worldNonce =
                InventoryRules.data(chest.getItem(2))
                    .getStringOr("ccr:verification_world_nonce", "");
            check(
                "native chest world nonce matches this exact test run",
                !worldNonce.isBlank()
                    && worldNonce.equals(expected.text("world-nonce", "missing")));
          } catch (Throwable failure) {
            failed.add("Uncaught persistence failure: " + failure);
            CutCleanReborn.LOG.error("Cold restart verification failed", failure);
          }
          try {
            Files.writeString(
                FabricLoader.getInstance().getGameDir().resolve("cold-" + stage + ".json"),
                new GsonBuilder()
                    .setPrettyPrinting()
                    .create()
                    .toJson(
                        Map.of(
                            "platform",
                            "Fabric",
                            "minecraft",
                            net.minecraft.SharedConstants.getCurrentVersion().name(),
                            "worldNonce",
                            worldNonce,
                            "stage",
                            stage,
                            "processId",
                            ProcessHandle.current().pid(),
                            "passed",
                            passed,
                            "failed",
                            failed,
                            "counts",
                            Map.of("passed", passed.size(), "failed", failed.size()),
                            "realPlayerVisual",
                            false)));
          } catch (Exception failure) {
            CutCleanReborn.LOG.error("Cannot write persistence verification report", failure);
          }
          CutCleanReborn.LOG.info(
              "CCR cold restart {}: {} passed, {} failed", stage, passed.size(), failed.size());
          server.halt(false);
        });
  }

  private void check(String name, boolean success) {
    (success ? passed : failed).add(name);
  }

  private ServerPlayer actor(RuleRuntime mod) {
    var profile = new GameProfile(PLAYER, "CCRPersistence");
    var player =
        new ServerPlayer(
            mod.server, mod.server.overworld(), profile, ClientInformation.createDefault());
    player.connection =
        new ServerGamePacketListenerImpl(
            mod.server,
            new Connection(PacketFlow.SERVERBOUND),
            player,
            CommonListenerCookie.createInitial(profile, false));
    return player;
  }
}
