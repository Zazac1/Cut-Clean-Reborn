package fr.isaac.cutcleanreborn.fabric;

import java.util.random.RandomGenerator;

final class GameplayMath {
  private GameplayMath() {}

  static double percent(double value, double fallback) {
    return Double.isFinite(value) ? Math.clamp(value, 0, 100) : fallback;
  }

  static int fortuneMultiplier(int level, RandomGenerator random) {
    int bounded = Math.clamp(level, 0, 255);
    return bounded == 0 ? 1 : Math.max(1, random.nextInt(bounded + 2));
  }

  static int lootingBonus(int level, RandomGenerator random) {
    return level <= 0 ? 0 : random.nextInt(Math.clamp(level, 0, 255) + 1);
  }

  static int addXp(int base, int extra) {
    return (int) Math.clamp((long) base + extra, 0L, Integer.MAX_VALUE);
  }

  static Integer integer(String input, int min, int max) {
    if (input == null) return null;
    try {
      int value = Integer.parseInt(input.trim());
      return value >= min && value <= max ? value : null;
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  static Double percentage(String input) {
    if (input == null) return null;
    try {
      double value = Double.parseDouble(input.replace("%", "").trim().replace(',', '.'));
      return Double.isFinite(value) && value >= 0 && value <= 100 ? value : null;
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  static AmountRange range(String input, int min, int max) {
    if (input == null) return null;
    String[] parts = input.trim().split("-", -1);
    if (parts.length < 1 || parts.length > 2) return null;
    Integer a = integer(parts[0], min, max), b = integer(parts[parts.length - 1], min, max);
    return a == null || b == null || b < a ? null : new AmountRange(a, b);
  }

  record AmountRange(int minimum, int maximum) {}
}
