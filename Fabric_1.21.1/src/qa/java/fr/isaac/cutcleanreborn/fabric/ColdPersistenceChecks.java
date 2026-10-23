package fr.isaac.cutcleanreborn.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
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
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Three independent production JVMs; native chest NBT and chunk files, never a mock item codec. */
public final class ColdPersistenceChecks implements ModInitializer {
  private static final UUID PLAYER = UUID.fromString("99e3c155-cecd-49e5-8cbe-fab111000001");
  private static final BlockPos CHEST = new BlockPos(16, 100, 16),
      LOG = CHEST.east(),
      CANE = CHEST.east(2);
  private final List<String> passed = new ArrayList<>(), failed = new ArrayList<>();
  private int ticks;

  @Override
  public void onInitialize() {
    int phase = Integer.getInteger("ccr.persistence.phase", 0);
    if (phase < 1 || phase > 3) return;
    ServerTickEvents.END_SERVER_TICK.register(
        server -> {
          if (!CutCleanReborn.ready() || ++ticks != 25) return;
          var mod = CutCleanReborn.instance();
          try {
            var level = server.overworld();
            level.getChunkAt(CHEST);
            if (phase == 1) write(mod);
            else read(mod, phase);
            mod.origins().save();
            mod.save();
            server.saveAllChunks(false, true, true);
          } catch (Throwable error) {
            failed.add("Uncaught phase " + phase + ": " + error);
            CutCleanReborn.LOGGER.error("CCR cold persistence phase failed", error);
          }
          try {
            var report =
                Map.of(
                    "stage",
                    phase == 1 ? "write" : phase == 2 ? "read" : "reread",
                    "processId",
                    ProcessHandle.current().pid(),
                    "worldNonce",
                    new Gson()
                        .fromJson(
                            Files.readString(
                                FabricLoader.getInstance()
                                    .getGameDir()
                                    .resolve("ccr-persistence-expect.json")),
                            JsonObject.class)
                        .get("nonce")
                        .getAsString(),
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
                FabricLoader.getInstance()
                    .getGameDir()
                    .resolve(
                        "cold-"
                            + (phase == 1 ? "write" : phase == 2 ? "read" : "reread")
                            + ".json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(report));
            CutCleanReborn.LOGGER.info(
                "CCR cold persistence phase {}: {} passed, {} failed",
                phase,
                passed.size(),
                failed.size());
          } catch (Exception error) {
            throw new IllegalStateException(error);
          }
          server.halt(false);
        });
  }

  private void check(String label, boolean value) {
    (value ? passed : failed).add(label);
  }

  private void write(CutCleanReborn mod) throws Exception {
    var level = mod.server().overworld();
    mod.settings().set("clean-stacks.players." + PLAYER, true);
    mod.settings().set("mobs.COW.xp-bonus", 42);
    mod.settings().set("automatic-enchantments.enabled", true);
    mod.settings().set("automatic-enchantments.refresh-version", 0);
    mod.settings()
        .set("automatic-enchantments.tools.DIAMOND_PICKAXE", Map.of("minecraft:unbreaking", 3));
    mod.settings().set(InventoryRules.ROOT + ".enabled", true);
    mod.settings().set(InventoryRules.ROOT + ".unique-mace.enabled", true);
    mod.settings().set(InventoryRules.ROOT + ".unique-mace.exists", false);
    mod.settings().set(InventoryRules.ROOT + ".unique-mace.id", null);
    level.setBlock(CHEST, Blocks.CHEST.defaultBlockState(), 3);
    level.setBlock(LOG, Blocks.OAK_LOG.defaultBlockState(), 3);
    level.setBlock(CANE.below(), Blocks.DIRT.defaultBlockState(), 3);
    level.setBlock(CANE.below().south(), Blocks.WATER.defaultBlockState(), 3);
    level.setBlock(CANE, Blocks.SUGAR_CANE.defaultBlockState(), 3);
    mod.origins().add(level, LOG, "timber");
    mod.origins().add(level, CANE, "cane");
    ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
    pickaxe.enchant(mod.inventory().enchantments().getHolderOrThrow(Enchantments.EFFICIENCY), 2);
    check(
        "write auto enchantment applied with original preserved",
        mod.inventory().auto(pickaxe) && level(pickaxe, mod, Enchantments.EFFICIENCY) == 2);
    ItemStack mace = new ItemStack(Items.MACE);
    var player =
        new ServerPlayer(
            mod.server(),
            level,
            new GameProfile(PLAYER, "CCRColdPersistence"),
            ClientInformation.createDefault());
    player.connection =
        new net.minecraft.server.network.ServerGamePacketListenerImpl(
            mod.server(),
            new net.minecraft.network.Connection(
                net.minecraft.network.protocol.PacketFlow.SERVERBOUND),
            player,
            net.minecraft.server.network.CommonListenerCookie.createInitial(
                player.getGameProfile(), false));
    mod.inventory().crafted(player, mace);
    ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(CHEST);
    chest.setItem(0, pickaxe);
    chest.setItem(1, mace);
    ItemStack witness = new ItemStack(Items.PAPER);
    var witnessData = InventoryRules.data(witness);
    witnessData.putString(
        "ccr:persistence_nonce", InventoryRules.data(pickaxe).getString("ccr:auto_enchant_id"));
    InventoryRules.data(witness, witnessData);
    chest.setItem(2, witness);
    chest.setChanged();
    check("write unique mace reserved", mod.inventory().maceBlocked());
    check(
        "write native chest contains both marked items",
        !InventoryRules.data(pickaxe).getString("ccr:auto_enchant_id").isBlank()
            && !InventoryRules.data(mace).getString("ccr:unique_mace_id").isBlank());
    Files.writeString(
        FabricLoader.getInstance().getGameDir().resolve("ccr-persistence-expect.json"),
        new Gson()
            .toJson(
                Map.of(
                    "autoId",
                    InventoryRules.data(pickaxe).getString("ccr:auto_enchant_id"),
                    "maceId",
                    InventoryRules.data(mace).getString("ccr:unique_mace_id"),
                    "nonce",
                    InventoryRules.data(pickaxe).getString("ccr:auto_enchant_id"))));
  }

  private void read(CutCleanReborn mod, int phase) throws Exception {
    var level = mod.server().overworld();
    JsonObject expected =
        new Gson()
            .fromJson(
                Files.readString(
                    FabricLoader.getInstance().getGameDir().resolve("ccr-persistence-expect.json")),
                JsonObject.class);
    ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(CHEST);
    check("native chest loaded from chunk after cold restart", chest != null);
    if (chest == null) return;
    check(
        "world chunk nonce survived cold restart",
        expected
            .get("nonce")
            .getAsString()
            .equals(InventoryRules.data(chest.getItem(2)).getString("ccr:persistence_nonce")));
    ItemStack pickaxe = chest.getItem(0), mace = chest.getItem(1);
    check(
        "auto UUID survived native item NBT cold restart",
        pickaxe.is(Items.DIAMOND_PICKAXE)
            && expected
                .get("autoId")
                .getAsString()
                .equals(InventoryRules.data(pickaxe).getString("ccr:auto_enchant_id")));
    if (phase == 2) {
      check(
          "YAML config value survived cold restart",
          mod.settings().integer("mobs.COW.xp-bonus", 0, 0, 100) == 42);
      check(
          "UUID Clean Stacks preference survived cold restart",
          mod.settings().bool("clean-stacks.players." + PLAYER, false));
      check(
          "player placed logs survived cold restart",
          level.getBlockState(LOG).is(Blocks.OAK_LOG)
              && mod.origins().contains(level, LOG, "timber"));
      check(
          "player placed cane survived cold restart",
          level.getBlockState(CANE).is(Blocks.SUGAR_CANE)
              && mod.origins().contains(level, CANE, "cane"));
      check(
          "automatic levels and exact once survived restart",
          level(pickaxe, mod, Enchantments.EFFICIENCY) == 2
              && level(pickaxe, mod, Enchantments.UNBREAKING) == 3
              && !mod.inventory().auto(pickaxe));
      check(
          "unique mace identity and reservation survived cold restart",
          mace.is(Items.MACE)
              && mod.inventory().maceBlocked()
              && expected
                  .get("maceId")
                  .getAsString()
                  .equals(InventoryRules.data(mace).getString("ccr:unique_mace_id"))
              && expected
                  .get("maceId")
                  .getAsString()
                  .equals(mod.settings().text(InventoryRules.ROOT + ".unique-mace.id", "")));
      mod.settings()
          .set("automatic-enchantments.tools.DIAMOND_PICKAXE", Map.of("minecraft:unbreaking", 1));
      mod.settings().set("automatic-enchantments.refresh-version", 1);
      check(
          "persisted original and snapshot permit safe refresh after restart",
          mod.inventory().auto(pickaxe)
              && level(pickaxe, mod, Enchantments.EFFICIENCY) == 2
              && level(pickaxe, mod, Enchantments.UNBREAKING) == 1);
      pickaxe.enchant(mod.inventory().enchantments().getHolderOrThrow(Enchantments.EFFICIENCY), 5);
      mod.settings().set("automatic-enchantments.refresh-version", 2);
      mod.inventory().destroyed(mace);
      chest.setItem(1, ItemStack.EMPTY);
      check(
          "placed origin removal is real before second restart",
          mod.origins().remove(level, LOG, "timber") && mod.origins().remove(level, CANE, "cane"));
      mod.settings().set("clean-stacks.players." + PLAYER, false);
      mod.settings().set("mobs.COW.xp-bonus", 43);
    } else {
      check(
          "changed YAML value survived second cold restart",
          mod.settings().integer("mobs.COW.xp-bonus", 0, 0, 100) == 43);
      check(
          "disabled personal preference not resurrected",
          !mod.settings().bool("clean-stacks.players." + PLAYER, true));
      check(
          "removed log and cane origins not resurrected",
          !mod.origins().contains(level, LOG, "timber")
              && !mod.origins().contains(level, CANE, "cane"));
      check(
          "destroyed unique mace reservation not resurrected",
          mace.isEmpty()
              && !mod.inventory().maceBlocked()
              && !mod.settings().bool(InventoryRules.ROOT + ".unique-mace.exists", true));
      check(
          "player edits and refresh snapshots survive second restart",
          level(pickaxe, mod, Enchantments.EFFICIENCY) == 5
              && level(pickaxe, mod, Enchantments.UNBREAKING) == 1
              && !mod.inventory().auto(pickaxe));
    }
    chest.setChanged();
  }

  private int level(
      ItemStack item,
      CutCleanReborn mod,
      net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> key) {
    return EnchantmentHelper.getItemEnchantmentLevel(
        mod.inventory().enchantments().getHolderOrThrow(key), item);
  }
}
