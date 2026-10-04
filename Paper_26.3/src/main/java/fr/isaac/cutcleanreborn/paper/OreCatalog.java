package fr.isaac.cutcleanreborn.paper;

import java.util.List;
import org.bukkit.Material;

/** Vanilla ore families and their normal, unenchanted drops. */
final class OreCatalog {
    static final List<OreDefinition> ORES = List.of(
            pair("Coal Ore", Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE, Material.COAL),
            pair("Copper Ore", Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE, Material.RAW_COPPER),
            pair("Iron Ore", Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE, Material.RAW_IRON),
            pair("Gold Ore", Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.RAW_GOLD),
            pair("Redstone Ore", Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE, Material.REDSTONE),
            pair("Lapis Ore", Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE, Material.LAPIS_LAZULI),
            pair("Diamond Ore", Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, Material.DIAMOND),
            pair("Emerald Ore", Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE, Material.EMERALD),
            single("Nether Quartz Ore", Material.NETHER_QUARTZ_ORE, Material.QUARTZ),
            single("Nether Gold Ore", Material.NETHER_GOLD_ORE, Material.GOLD_NUGGET),
            single("Ancient Debris", Material.ANCIENT_DEBRIS, Material.ANCIENT_DEBRIS));

    private OreCatalog() { }
    static OreDefinition find(Material block) {
        return ORES.stream().filter(ore -> ore.stone() == block || ore.deepslate() == block).findFirst().orElse(null);
    }
    static List<CutCleanRebornPlugin.MobDrop> vanillaDrops(Material block) {
        OreDefinition ore = find(block);
        return ore == null ? List.of() : List.of(new CutCleanRebornPlugin.MobDrop(ore.normalDrop(), 1, 1, 100.0D));
    }
    /** Exact possible quantity range produced by vanilla Fortune III for this ore's regular drop. */
    static CutCleanRebornPlugin.MobDrop fortuneThreeDrop(Material block) {
        OreDefinition ore = find(block);
        if (ore == null) return null;
        int baseMinimum = 1, baseMaximum = 1;
        switch (ore.normalDrop()) {
            case RAW_COPPER -> { baseMinimum = 2; baseMaximum = 5; }
            case REDSTONE -> { baseMinimum = 4; baseMaximum = 5; }
            case LAPIS_LAZULI -> { baseMinimum = 4; baseMaximum = 9; }
            case GOLD_NUGGET -> { baseMinimum = 2; baseMaximum = 6; }
            case ANCIENT_DEBRIS -> { return new CutCleanRebornPlugin.MobDrop(ore.normalDrop(), 1, 1, 100.0D); }
            default -> { }
        }
        return new CutCleanRebornPlugin.MobDrop(ore.normalDrop(), baseMinimum, baseMaximum * 4, 100.0D);
    }
    private static OreDefinition pair(String name, Material stone, Material deepslate, Material drop) {
        return new OreDefinition(name, stone, deepslate, drop);
    }
    private static OreDefinition single(String name, Material block, Material drop) { return new OreDefinition(name, block, null, drop); }
}

record OreDefinition(String name, Material stone, Material deepslate, Material normalDrop) { }
