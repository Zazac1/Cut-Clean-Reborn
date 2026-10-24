package fr.isaac.cutcleanreborn.fabric;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RuleMathTest {
  @Test
  void boundedNumbers() {
    assertEquals(4, RuleMath.integer(" 4 ", 0, 4));
    assertNull(RuleMath.integer("5", 0, 4));
    assertNull(RuleMath.integer("2147483648", 0, Integer.MAX_VALUE));
  }

  @Test
  void finitePercentage() {
    assertEquals(12.5, RuleMath.percentage("12,5%"));
    assertNull(RuleMath.percentage("NaN"));
    assertNull(RuleMath.percentage("Infinity"));
    assertNull(RuleMath.percentage("101"));
    assertEquals(0, RuleMath.percent(Double.NaN));
  }

  @Test
  void rangeValidation() {
    assertEquals(new RuleMath.AmountRange(1, 3), RuleMath.range("1-3", 0, 64));
    assertEquals(new RuleMath.AmountRange(5, 5), RuleMath.range("5", 0, 64));
    assertNull(RuleMath.range("3-1", 0, 64));
    assertNull(RuleMath.range("1--2", 0, 64));
  }

  @Test
  void dropBounds() {
    Random random = new Random(3);
    for (int i = 0; i < 1000; i++) {
      int count = RuleMath.roll(Map.of("minimum", 2, "maximum", 4, "chance", 100), random);
      assertTrue(count >= 2 && count <= 4);
    }
    assertEquals(0, RuleMath.roll(Map.of("minimum", 1, "maximum", 64, "chance", 0), random));
    assertEquals(0, RuleMath.roll(Map.of("minimum", 3, "maximum", 2), random));
  }

  @Test
  void bonusBounds() {
    Random random = new Random(6);
    assertEquals(1, RuleMath.fortune(0, random));
    for (int i = 0; i < 1000; i++) {
      int fortune = RuleMath.fortune(3, random);
      assertTrue(fortune >= 1 && fortune <= 4);
      int looting = RuleMath.looting(3, random);
      assertTrue(looting >= 0 && looting <= 3);
    }
  }

  @Test
  void targetCategories() {
    assertTrue(RuleMath.matches("ARMOR", "minecraft:diamond_chestplate"));
    assertTrue(RuleMath.matches("DIAMOND", "minecraft:diamond_boots"));
    assertFalse(RuleMath.matches("DIAMOND", "minecraft:iron_boots"));
    assertTrue(RuleMath.matches("SWORDS", "minecraft:iron_sword"));
    assertFalse(RuleMath.matches("SWORDS", "minecraft:iron_pickaxe"));
    assertTrue(
        RuleMath.matches("minecraft:iron_sword,minecraft:iron_pickaxe", "minecraft:iron_sword"));
  }

  @Test
  void cleanFamilies() {
    assertEquals("wood", RuleMath.category("OAK_LOG"));
    assertEquals("wood", RuleMath.category("STRIPPED_WARPED_STEM"));
    assertEquals("stone", RuleMath.category("TUFF"));
    assertNull(RuleMath.category("OAK_WOOD"));
    assertNull(RuleMath.category("PUMPKIN_STEM"));
    assertNull(RuleMath.category("POLISHED_DIORITE"));
  }

  @Test
  void cycleResolutionIsIdempotent() {
    Map<String, String> mapping = Map.of("a", "b", "b", "c", "c", "b");
    String output = RuleMath.normalised("a", mapping::get);
    assertEquals("b", output);
    assertEquals(output, RuleMath.normalised(output, mapping::get));
  }
}
