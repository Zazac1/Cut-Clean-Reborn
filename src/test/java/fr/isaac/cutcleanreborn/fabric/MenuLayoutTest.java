package fr.isaac.cutcleanreborn.fabric;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class MenuLayoutTest {
  @Test void smallPagesUseCenteredRows() {
    assertEquals(List.of(13), MenuLayout.forEntries(1).slots());
    assertEquals(List.of(11, 15), MenuLayout.forEntries(2).slots());
    assertEquals(List.of(11, 13, 15), MenuLayout.forEntries(3).slots());
    assertEquals(3, MenuLayout.forEntries(4).rows());
  }

  @Test void largerSettingsUseTwoBalancedRows() {
    for (int count = 5; count <= 8; count++) {
      MenuLayout layout = MenuLayout.forEntries(count);
      assertEquals(4, layout.rows());
      assertEquals(count, layout.slots().size());
      assertTrue(layout.slots().stream().allMatch(slot -> slot >= 10 && slot <= 25));
    }
  }

  @Test void catalogueKeepsAllThirtySixEntriesPerPage() {
    for (int count : new int[] {9, 36, 37, 100}) {
      MenuLayout layout = MenuLayout.forEntries(count);
      assertEquals(6, layout.rows());
      assertEquals(36, layout.slots().size());
      assertEquals(9, layout.slots().getFirst());
      assertEquals(44, layout.slots().getLast());
    }
  }

  @Test void dashboardsCanSeparateMasterAndFooterUtility() {
    MenuLayout layout = MenuLayout.arranged(4, 13, 20, 24, 31);
    assertEquals(13, layout.slots().getFirst());
    assertEquals(31, layout.slots().getLast());
    assertFalse(layout.slots().contains(27));
    assertFalse(layout.slots().contains(35));
  }

  @Test void contentCannotReplaceHeaderOrNavigation() {
    assertThrows(IllegalArgumentException.class, () -> MenuLayout.arranged(4, 4));
    assertThrows(IllegalArgumentException.class, () -> MenuLayout.arranged(4, 27));
    assertThrows(IllegalArgumentException.class, () -> MenuLayout.arranged(4, 35));
    assertThrows(IllegalArgumentException.class, () -> MenuLayout.arranged(4, 13, 13));
    assertThrows(IllegalArgumentException.class, () -> MenuLayout.forEntries(-1));
  }

  @Test void emptyAndStoredLayoutsAreSafe() {
    assertTrue(MenuLayout.forEntries(0).slots().isEmpty());
    var source = new java.util.ArrayList<>(List.of(13));
    MenuLayout layout = new MenuLayout(3, source);
    source.clear();
    assertEquals(List.of(13), layout.slots());
    assertThrows(UnsupportedOperationException.class, () -> layout.slots().clear());
  }
}
