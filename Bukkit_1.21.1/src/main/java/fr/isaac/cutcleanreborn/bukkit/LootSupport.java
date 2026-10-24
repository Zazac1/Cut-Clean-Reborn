package fr.isaac.cutcleanreborn.bukkit;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.inventory.ItemStack;

final class LootSupport {
    private LootSupport() { }

    static int roll(CutCleanRebornPlugin.MobDrop drop) {
        var random = ThreadLocalRandom.current();
        if (random.nextDouble(100.0) >= drop.chance()) return 0;
        return random.nextInt(drop.minimum(), drop.maximum() + 1);
    }

    static void addStacks(List<ItemStack> output, ItemStack item, int amount) {
        for (int remaining = amount; remaining > 0;) {
            ItemStack stack = item.clone();
            int quantity = Math.min(remaining, item.getMaxStackSize());
            stack.setAmount(quantity);
            output.add(stack);
            remaining -= quantity;
        }
    }
}

