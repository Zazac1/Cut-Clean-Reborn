package fr.isaac.cutcleanreborn.fabric;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;

/** Inventory geometry only; entry identity, actions and semantic parents stay independent. */
record MenuLayout(int rows, List<Integer> slots) {
  MenuLayout {
    slots = List.copyOf(slots);
    if (rows < 3 || rows > 6 || new HashSet<>(slots).size() != slots.size())
      throw new IllegalArgumentException("Invalid menu layout");
    int footer = (rows - 1) * 9;
    for (int slot : slots)
      if (slot < 9 || slot >= rows * 9 || slot == footer || slot == footer + 8)
        throw new IllegalArgumentException("Content overlaps header or navigation");
  }

  static MenuLayout arranged(int rows, int... slots) {
    return new MenuLayout(rows, Arrays.stream(slots).boxed().toList());
  }

  static MenuLayout forEntries(int count) {
    if (count < 0) throw new IllegalArgumentException("Negative entry count");
    return switch (count) {
      case 0 -> arranged(3);
      case 1 -> arranged(3, 13);
      case 2 -> arranged(3, 11, 15);
      case 3 -> arranged(3, 11, 13, 15);
      case 4 -> arranged(3, 10, 12, 14, 16);
      case 5 -> arranged(4, 11, 13, 15, 21, 23);
      case 6 -> arranged(4, 11, 13, 15, 20, 22, 24);
      case 7 -> arranged(4, 10, 12, 14, 16, 20, 22, 24);
      case 8 -> arranged(4, 10, 12, 14, 16, 19, 21, 23, 25);
      default -> new MenuLayout(6, IntStream.range(9, 45).boxed().toList());
    };
  }
}
