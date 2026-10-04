package fr.isaac.cutcleanreborn.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.isaac.cutcleanreborn.CutCleanRebornMod;
import fr.isaac.cutcleanreborn.config.CutCleanConfig;
import fr.isaac.cutcleanreborn.config.CutCleanConfigManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.text.Text;

public class CutCleanConfigScreen extends Screen {
	private static final Gson GSON = new GsonBuilder().create();

	private final Screen parent;
	private final CutCleanConfig working;

	public CutCleanConfigScreen(Screen parent) {
		super(Text.literal("CutCleanReborn - Rules"));
		this.parent = parent;
		this.working = GSON.fromJson(GSON.toJson(CutCleanConfigManager.getConfig()), CutCleanConfig.class);
	}

	@Override
	protected void init() {
		int btnW = 260;
		int x = width / 2 - btnW / 2;
		int y = 34;

		MultilineTextWidget titleWidget = new MultilineTextWidget(8, 10, title, textRenderer);
		titleWidget.setMaxWidth(width - 16);
		titleWidget.setCentered(true);
		addDrawableChild(titleWidget);

		addDrawableChild(ButtonWidget.builder(Text.literal("Cleaner Foods"), b ->
			client.setScreen(new KeyValueListScreen(
				this,
				"Cleaner Foods",
				working.cookedDropOverrides,
				v -> working.cookedDropOverrides = v,
				"Cook Animal Drops",
				() -> working.features.cookAnimalDrops,
				enabled -> working.features.cookAnimalDrops = enabled
			))
		).dimensions(x, y, btnW, 20).build());
		y += 24;

		addDrawableChild(ButtonWidget.builder(Text.literal("Cleaner Mining"), b ->
			client.setScreen(new KeyValueListScreen(
				this,
				"Cleaner Mining",
				working.oreSmeltOverrides,
				v -> working.oreSmeltOverrides = v,
				"Auto Smelt Ore Drops",
				() -> working.features.autoSmeltOreDrops,
				enabled -> working.features.autoSmeltOreDrops = enabled
			))
		).dimensions(x, y, btnW, 20).build());
		y += 24;

		addDrawableChild(ButtonWidget.builder(Text.literal("Auto Enchant"), b ->
			client.setScreen(new ToolEnchantingScreen(this, working))
		).dimensions(x, y, btnW, 20).build());
		y += 24;

		addDrawableChild(ButtonWidget.builder(Text.literal("Cleaner Blocks Stacks"), b ->
			client.setScreen(new KeyValueListScreen(
				this,
				"Cleaner Blocks Stacks",
				working.stoneDropOverrides,
				v -> working.stoneDropOverrides = v,
				"Normalize Stone Drops",
				() -> working.features.normalizeStoneDropsToCobblestone,
				enabled -> working.features.normalizeStoneDropsToCobblestone = enabled
			))
		).dimensions(x, y, btnW, 20).build());
		y += 24;

		addDrawableChild(ButtonWidget.builder(Text.literal("Cleaner Mobs Drops"), b ->
			client.setScreen(new AnimalDropsScreen(this, working))
		).dimensions(x, y, btnW, 20).build());
		y += 24;

		addDrawableChild(ButtonWidget.builder(Text.literal("Cleaner Harvest"), b -> {
			client.setScreen(new TreeAndHarvestSettingsScreen(this, working));
		}).dimensions(x, y, btnW, 20).build());

		boolean integratedServer = client != null && client.getServer() != null;
		ButtonWidget save = ButtonWidget.builder(Text.literal(integratedServer ? "Save" : "Server settings"), b -> saveAndClose())
			.dimensions(width / 2 - 104, height - 30, 100, 20).build();
		save.active = integratedServer;
		addDrawableChild(save);
		addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> client.setScreen(parent))
			.dimensions(width / 2 + 4, height - 30, 100, 20).build());
	}

	private void saveAndClose() {
		// A remote client must never overwrite or appear to control server gameplay rules.
		if (client == null || client.getServer() == null) {
			client.setScreen(parent);
			return;
		}
		CutCleanConfig live = CutCleanConfigManager.getConfig();
		live.features = working.features;
		live.cookedDropOverrides = working.cookedDropOverrides;
		live.oreSmeltOverrides = working.oreSmeltOverrides;
		live.toolEnchanting = working.toolEnchanting;
		live.stoneDropOverrides = working.stoneDropOverrides;
		live.animalExtraDrops = working.animalExtraDrops;
		live.treeFeller = working.treeFeller;
		live.sugarCane = working.sugarCane;
		live.legacyMining = working.legacyMining;

		CutCleanConfigManager.save();
		CutCleanRebornMod.reloadFeaturesFromConfig();
		client.setScreen(parent);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
		super.render(context, mouseX, mouseY, delta);
	}
}
