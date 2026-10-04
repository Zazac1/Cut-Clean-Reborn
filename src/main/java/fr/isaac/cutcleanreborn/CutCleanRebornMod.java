package fr.isaac.cutcleanreborn;

import fr.isaac.cutcleanreborn.config.CutCleanConfigManager;
import fr.isaac.cutcleanreborn.feature.AnimalExtraDropManager;
import fr.isaac.cutcleanreborn.feature.CookedDropConverter;
import fr.isaac.cutcleanreborn.feature.CraftedToolEnchanter;
import fr.isaac.cutcleanreborn.feature.LegacyMiningManager;
import fr.isaac.cutcleanreborn.feature.OreSmeltDropConverter;
import fr.isaac.cutcleanreborn.feature.StoneDropNormalizer;
import fr.isaac.cutcleanreborn.feature.SugarCaneDropBooster;
import fr.isaac.cutcleanreborn.feature.TreeFellerManager;
import fr.isaac.cutcleanreborn.feature.UhcDiamondLimitManager;
import fr.isaac.cutcleanreborn.feature.UhcStuffTimerManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CutCleanRebornMod implements ModInitializer {
	public static final String MOD_ID = "cutcleanreborn";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static void reloadFeaturesFromConfig() {
		CookedDropConverter.reloadFromConfig();
		OreSmeltDropConverter.reloadFromConfig();
		CraftedToolEnchanter.reloadFromConfig();
		StoneDropNormalizer.reloadFromConfig();
		AnimalExtraDropManager.reloadFromConfig();
		TreeFellerManager.reloadFromConfig();
		SugarCaneDropBooster.reloadFromConfig();
		LegacyMiningManager.reloadFromConfig();
		UhcDiamondLimitManager.reloadFromConfig();
	}

	@Override
	public void onInitialize() {
		CutCleanConfigManager.load();
		reloadFeaturesFromConfig();
		UhcStuffTimerManager.registerCommands();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (var player : server.getPlayerManager().getPlayerList()) {
				CraftedToolEnchanter.scanAndEnchantPlayerInventory(player);
				UhcDiamondLimitManager.onPlayerTick(player);
			}
			SugarCaneDropBooster.onServerTick(server);
			UhcStuffTimerManager.onServerTick(server);
		});
		ServerLifecycleEvents.SERVER_STARTED.register(LegacyMiningManager::applyPendingOverrideIfAny);
		ServerLifecycleEvents.SERVER_STARTED.register(UhcStuffTimerManager::onServerStarted);
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (world instanceof net.minecraft.server.world.ServerWorld serverWorld) {
				TreeFellerManager.onBlockBroken(serverWorld, pos, state, player);
			}
		});
		LOGGER.info("CutCleanReborn charge (server-side, compatible solo et multi)");
	}
}
