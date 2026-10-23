package fr.isaac.cutcleanreborn.bukkit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Random;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class GameplayRegressionTest {
    @Test void frenchPercentagesAndZeroAreAccepted() {
        assertEquals(4.5, GameplayMath.percentage(" 4,5 % "));
        assertEquals(0.0, GameplayMath.percentage("0"));
        assertEquals(100.0, GameplayMath.percentage("100%"));
    }

    @Test void nonFiniteOrOutOfRangePercentagesAreRejected() {
        for (String input : new String[] {"NaN", "Infinity", "-1", "100.01", "", "apple"})
            assertNull(GameplayMath.percentage(input), input);
        assertEquals(0.5, GameplayMath.percent(Double.NaN, 0.5));
    }

    @Test void quantityRangesDoNotAcceptInvertedOrUnboundedValues() {
        assertEquals(new GameplayMath.AmountRange(2, 5), GameplayMath.range("2 - 5", 0, 64));
        assertEquals(new GameplayMath.AmountRange(0, 0), GameplayMath.range("0", 0, 64));
        for (String input : new String[] {"5-2", "1-65", "1-2-3", "-1", "", "2147483647"})
            assertNull(GameplayMath.range(input, 0, 64), input);
        assertNull(GameplayMath.range("0", 1, 64));
    }

    @Test void fortuneThreeCanActuallyProduceFourTimesTheBaseDrop() {
        Random random = new Random(34);
        HashSet<Integer> seen = new HashSet<>();
        for (int attempt = 0; attempt < 1000; attempt++) seen.add(GameplayMath.fortuneMultiplier(3, random));
        assertEquals(java.util.Set.of(1, 2, 3, 4), seen);
        assertEquals(1, GameplayMath.fortuneMultiplier(0, random));
    }

    @Test void lootingAddsItemsRatherThanMultiplyingTheEntireDrop() {
        Random random = new Random(73);
        HashSet<Integer> seen = new HashSet<>();
        for (int attempt = 0; attempt < 1000; attempt++) seen.add(GameplayMath.lootingBonus(3, random));
        assertEquals(java.util.Set.of(0, 1, 2, 3), seen);
        assertEquals(0, GameplayMath.lootingBonus(0, random));
    }

    @Test void xpDoesNotOverflowIntoNegativeValues() {
        assertEquals(Integer.MAX_VALUE, GameplayMath.addXp(Integer.MAX_VALUE - 2, 100));
        assertEquals(12, GameplayMath.addXp(7, 5));
    }

    @Test void standaloneToolsAreNotLostFromTheEnchantmentCatalogue() {
        for (Material tool : new Material[] {Material.BOW, Material.CROSSBOW, Material.MACE, Material.TRIDENT,
                Material.SHEARS, Material.FISHING_ROD, Material.FLINT_AND_STEEL, Material.BRUSH, Material.DIAMOND_AXE})
            assertTrue(AutoEnchantCategory.TOOLS.matches(tool), tool.name());
        assertFalse(AutoEnchantCategory.TOOLS.matches(Material.DIAMOND));
        assertTrue(AutoEnchantCategory.ARMOR.matches(Material.TURTLE_HELMET));
        assertFalse(AutoEnchantCategory.ARMOR.matches(Material.DIAMOND_SWORD));
    }

    @Test void orePreviewsUseTheirRealBaseRanges() {
        var copper = OreCatalog.vanillaDrops(Material.COPPER_ORE).getFirst();
        assertEquals(2, copper.minimum());
        assertEquals(5, copper.maximum());
        var lapis = OreCatalog.vanillaDrops(Material.DEEPSLATE_LAPIS_ORE).getFirst();
        assertEquals(4, lapis.minimum());
        assertEquals(9, lapis.maximum());
        assertEquals(8, OreCatalog.fortuneThreeDrop(Material.REDSTONE_ORE).maximum());
        assertEquals(1, OreCatalog.fortuneThreeDrop(Material.ANCIENT_DEBRIS).maximum());
    }

    @Test void persistentBlockPositionsDoNotCollideAtNegativeCoordinatesOrY() {
        HashSet<Integer> keys = new HashSet<>();
        for (int y = -64; y < 320; y++) for (int x = -16; x < 0; x++) for (int z = -16; z < 0; z++)
            assertTrue(keys.add(PlacedBlockTracker.localPosition(x, y, z, -64)));
        assertEquals(16 * 16 * 384, keys.size());
    }
}

