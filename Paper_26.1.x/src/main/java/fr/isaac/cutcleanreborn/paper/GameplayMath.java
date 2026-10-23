package fr.isaac.cutcleanreborn.paper;

import java.util.random.RandomGenerator;

/** Small, server-independent rules used by loot, configuration inputs and their regression tests. */
final class GameplayMath {
    private GameplayMath() { }

    static double percent(double value, double fallback) {
        return Double.isFinite(value) ? Math.clamp(value, 0.0, 100.0) : fallback;
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

    static Integer integer(String input, int minimum, int maximum) {
        if (input == null) return null;
        try {
            int value = Integer.parseInt(input.trim());
            return value >= minimum && value <= maximum ? value : null;
        } catch (NumberFormatException ignored) { return null; }
    }

    static Double percentage(String input) {
        if (input == null) return null;
        try {
            double value = Double.parseDouble(input.trim().replace("%", "").replace(',', '.').trim());
            return Double.isFinite(value) && value >= 0 && value <= 100 ? value : null;
        } catch (NumberFormatException ignored) { return null; }
    }

    static AmountRange range(String input, int minimumAllowed, int maximumAllowed) {
        if (input == null) return null;
        String[] parts = input.trim().split("-", -1);
        if (parts.length < 1 || parts.length > 2) return null;
        Integer minimum = integer(parts[0], minimumAllowed, maximumAllowed);
        Integer maximum = integer(parts[parts.length - 1], minimumAllowed, maximumAllowed);
        return minimum == null || maximum == null || maximum < minimum ? null : new AmountRange(minimum, maximum);
    }

    record AmountRange(int minimum, int maximum) { }
}
