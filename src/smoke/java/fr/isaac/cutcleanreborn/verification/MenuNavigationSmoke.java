package fr.isaac.cutcleanreborn.verification;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/** Real listeners/scheduler/native inventories, with a synthetic player instead of a network client. */
final class MenuNavigationSmoke {
    private final CCRSmoke smoke;
    private final JavaPlugin ccr;
    private final List<Step> steps = new ArrayList<>();
    private final Session session = new Session();
    private Object armorRule;
    private Object capRule;
    private boolean textSubmitted;

    MenuNavigationSmoke(CCRSmoke smoke, JavaPlugin ccr) { this.smoke = smoke; this.ccr = ccr; }

    void run(Consumer<Throwable> finished) {
        steps.add(() -> open("TreeCutterMenu"));
        steps.add(() -> { expect("TreeCutterMenu"); click(20); });
        steps.add(() -> { expect("TreeAxeMenu"); click(10); });
        steps.add(() -> { expect("TreeAxeMenu"); click(10); });
        steps.add(() -> { expect("TreeAxeMenu"); escape(); });
        steps.add(() -> { expect("TreeCutterMenu"); click(22); });
        steps.add(() -> { expect("TreeHarvestMenu"); escape(); });
        steps.add(() -> { expect("TreeCutterMenu"); click(24); });
        steps.add(() -> { expect("TreeBreakTimeMenu"); escape(); });
        steps.add(() -> { expect("TreeCutterMenu"); escape(); });
        steps.add(() -> { expect("ConfigMainMenu"); escape(); });
        steps.add(() -> { closed("Escape at the root closes CCR"); open("BetterSugarCaneMenu"); });
        steps.add(() -> {
            expect("BetterSugarCaneMenu");
            smoke.require(top().getItem(20).getType() != top().getItem(24).getType(), "sugar cane causes have distinct icons");
            click(20);
        });
        steps.add(() -> { expect("SugarCaneCauseMenu"); smoke.require(top().getItem(4).getType() == Material.IRON_HOE, "player break identified"); click(11); });
        steps.add(() -> { expect("SugarCaneCauseMenu"); escape(); });
        steps.add(() -> { expect("BetterSugarCaneMenu"); click(24); });
        steps.add(() -> { expect("SugarCaneCauseMenu"); smoke.require(top().getItem(4).getType() == Material.DIRT, "support break identified"); escape(); });
        steps.add(() -> {
            expect("BetterSugarCaneMenu");
            smoke.call(smoke.call(ccr, "betterSugarCane"), "setChance", smoke.enumValue("BetterSugarCaneService$Cause", "PLAYER"), 17.0);
            click(30);
        });
        steps.add(() -> { expect("ConfirmationMenu"); escape(); });
        steps.add(() -> {
            expect("BetterSugarCaneMenu");
            smoke.require((Double) smoke.call(smoke.call(ccr, "betterSugarCane"), "chance", smoke.enumValue("BetterSugarCaneService$Cause", "PLAYER")) == 17.0,
                    "Escape never confirms a reset");
            click(35);
        });
        steps.add(() -> { closed("Close CCR does not reopen the parent"); open("ConfigMainMenu"); });
        steps.add(() -> { expect("ConfigMainMenu"); click(24); });
        steps.add(() -> { expect("StuffRulesMenu"); click(20); });
        steps.add(() -> { expect("ArmorRuleListMenu"); click(40); });
        steps.add(() -> {
            expect("ArmorRuleEditorMenu"); armorRule = field(holder(), "rule");
            smoke.require(!(Boolean) smoke.call(armorRule, "enabled"), "new armor rule starts disabled"); click(12);
        });
        steps.add(() -> { expect("RuleTargetMenu"); click(10); });
        steps.add(() -> {
            expect("ArmorRuleEditorMenu"); armorRule = field(holder(), "rule");
            smoke.require(smoke.call(armorRule, "target").equals("ARMOR"), "clickable armor target saved"); click(24);
        });
        steps.add(() -> { expect("ConfirmationMenu"); escape(); });
        steps.add(() -> {
            expect("ArmorRuleEditorMenu");
            smoke.require(((List<?>) smoke.call(smoke.call(ccr, "stuffRules"), "armorRules")).contains(armorRule), "cancelled deletion preserves rule");
            click(16);
        });
        steps.add(() -> { expect("RuleTargetMenu"); click(11); });
        steps.add(() -> {
            expect("ArmorRuleEditorMenu"); Object copy = field(holder(), "rule");
            smoke.require(!smoke.call(copy, "id").equals(smoke.call(armorRule, "id")) && !(Boolean) smoke.call(copy, "enabled"), "copy gets a new identity and starts disabled");
            smoke.require(smoke.call(copy, "target").equals("LEATHER"), "copy uses chosen target"); escape();
        });
        steps.add(() -> { expect("ArmorRuleListMenu"); escape(); });
        steps.add(() -> { expect("StuffRulesMenu"); click(22); });
        steps.add(() -> { expect("EnchantmentCapListMenu"); click(40); });
        steps.add(() -> { expect("EnchantmentCapEditorMenu"); capRule = field(holder(), "rule"); click(12); });
        steps.add(() -> { expect("RuleTargetMenu"); click(12); });
        steps.add(() -> {
            expect("EnchantmentCapEditorMenu"); capRule = field(holder(), "rule");
            smoke.require(smoke.call(capRule, "target").equals("TOOLS"), "enchantment category selected without text entry"); click(14);
        });
        steps.add(() -> { expect("EnchantmentPickerMenu"); click(51); });
        steps.add(() -> { expect("EnchantmentPickerMenu"); smoke.require((Integer) field(holder(), "page") == 1, "enchantment picker paginates"); escape(); });
        steps.add(() -> { expect("EnchantmentCapEditorMenu"); click(14); });
        steps.add(() -> { expect("EnchantmentPickerMenu"); click(10); });
        steps.add(() -> {
            expect("EnchantmentCapEditorMenu"); Object changed = field(holder(), "rule");
            smoke.require(smoke.call(changed, "target").equals("TOOLS") && smoke.call(changed, "id").equals(smoke.call(capRule, "id")), "enchantment selection preserves other rule settings");
            click(20);
        });
        steps.add(() -> { expect("EnchantmentRefundMenu"); click(11); });
        steps.add(() -> { expect("EnchantmentRefundMenu"); escape(); });
        steps.add(() -> { expect("EnchantmentCapEditorMenu"); click(24); });
        steps.add(() -> { expect("ConfirmationMenu"); escape(); });
        steps.add(() -> { expect("EnchantmentCapEditorMenu"); escape(); });
        steps.add(() -> { expect("EnchantmentCapListMenu"); escape(); });
        steps.add(() -> { expect("StuffRulesMenu"); click(24); });
        steps.add(() -> { expect("UniqueMaceMenu"); escape(); });
        steps.add(() -> { expect("StuffRulesMenu"); click(27); });
        steps.add(() -> { expect("ConfigMainMenu"); open("MobListMenu", 1); });
        steps.add(() -> { expect("MobListMenu"); click(10); });
        steps.add(() -> { expect("MobConfigurationMenu"); escape(); });
        steps.add(() -> { expect("MobListMenu"); smoke.require((Integer) field(holder(), "page") == 1, "Escape retains the originating mob page"); open("TreeCutterMenu"); });
        steps.add(() -> { expect("TreeCutterMenu"); openTextInput(); });
        steps.add(() -> { smoke.require(session.view.getType() == InventoryType.ANVIL, "text input view prepared"); escape(); });
        steps.add(() -> { expect("TreeCutterMenu"); smoke.require(!textSubmitted, "Escape cancels text input without submitting"); escape(); session.foreign(); });
        steps.add(() -> {
            smoke.require(session.view.getType() == InventoryType.CHEST && holder() == null, "delayed Escape does not replace another inventory");
            open("TreeCutterMenu");
        });
        steps.add(() -> { expect("TreeCutterMenu"); escape(); session.permission = false; });
        steps.add(() -> { closed("revoked permission prevents a delayed reopen"); session.permission = true; open("TreeCutterMenu"); });
        steps.add(() -> { expect("TreeCutterMenu"); session.close(InventoryCloseEvent.Reason.PLUGIN); });
        steps.add(() -> closed("programmatic close never navigates back"));

        new BukkitRunnable() {
            private int next;
            @Override public void run() {
                try {
                    if (next < steps.size()) { steps.get(next++).run(); return; }
                    cancel(); finished.accept(null);
                } catch (Throwable failure) {
                    cancel(); finished.accept(new AssertionError("Menu navigation step " + next, failure));
                }
            }
        }.runTaskTimer(smoke, 1L, 3L);
    }

    private void open(String name, Object... extra) throws Exception {
        Object[] args = new Object[extra.length + 1]; args[0] = ccr;
        System.arraycopy(extra, 0, args, 1, extra.length);
        smoke.call(smoke.create(name, args), "open", session.player);
    }

    private void click(int slot) {
        InventoryClickEvent event = new InventoryClickEvent(session.view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        Bukkit.getPluginManager().callEvent(event);
        smoke.require(event.isCancelled(), "menu click is protected at slot " + slot);
    }

    private void escape() { session.close(InventoryCloseEvent.Reason.PLAYER); }
    private Inventory top() { return session.view.getTopInventory(); }
    private Object holder() { return top().getHolder(); }
    private void expect(String name) { smoke.require(holder() != null && holder().getClass().getSimpleName().equals(name), "navigation reaches " + name); }
    private void closed(String label) { smoke.require(session.view.getType() == InventoryType.CRAFTING, label); }

    private static Object field(Object value, String name) throws Exception {
        Field field = value.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(value);
    }

    private static void field(Object value, String name, Object replacement) throws Exception {
        Field field = value.getClass().getDeclaredField(name); field.setAccessible(true); field.set(value, replacement);
    }

    @SuppressWarnings("unchecked")
    private void openTextInput() throws Exception {
        // Native MenuType.create requires a network-backed CraftPlayer. Bind a synthetic AnvilView
        // to the real input listener instead, then dispatch the same native close event as Escape.
        Object parent = holder();
        Object input = smoke.create("AnvilTextInput", ccr, "Test Input", "number", "5", (Function<String, Integer>) Integer::valueOf,
                (BiConsumer<Player, Integer>) (player, value) -> textSubmitted = true);
        Inventory nativeAnvil = Bukkit.createInventory(null, InventoryType.ANVIL);
        // Bukkit's custom inventory factory returns a generic inventory even for type ANVIL,
        // whereas AnvilView has a covariant AnvilInventory getter.
        AnvilInventory anvil = (AnvilInventory) Proxy.newProxyInstance(AnvilInventory.class.getClassLoader(),
                new Class<?>[] {AnvilInventory.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> nativeAnvil.getClass().getMethod(method.getName(), method.getParameterTypes()).invoke(nativeAnvil, args);
                });
        AnvilView view = (AnvilView) session.view(anvil, InventoryType.ANVIL, AnvilView.class);
        field(input, "parent", parent); field(input, "view", view);
        Field inputs = smoke.type("AnvilTextInput").getDeclaredField("INPUTS"); inputs.setAccessible(true);
        ((Set<Inventory>) inputs.get(null)).add(anvil);
        Bukkit.getPluginManager().registerEvents((Listener) input, ccr);
        session.close(InventoryCloseEvent.Reason.OPEN_NEW); session.view = view;
    }

    @FunctionalInterface private interface Step { void run() throws Exception; }

    private static final class Session {
        private final UUID identity = UUID.randomUUID();
        private final Inventory personal = Bukkit.createInventory(null, 9);
        private final Inventory bottom = Bukkit.createInventory(null, 9);
        private boolean permission = true;
        private final Player player;
        private InventoryView view;

        private Session() {
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class}, (proxy, method, args) -> switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> identity.hashCode();
                case "toString", "getName" -> "CCR smoke player";
                case "getUniqueId" -> identity;
                case "isOnline", "isValid" -> true;
                case "hasPermission" -> permission;
                case "getLocation" -> Bukkit.getWorlds().getFirst().getSpawnLocation();
                case "getOpenInventory" -> view;
                case "openInventory" -> {
                    close(InventoryCloseEvent.Reason.OPEN_NEW);
                    view = view((Inventory) args[0], InventoryType.CHEST, InventoryView.class); yield view;
                }
                case "closeInventory" -> { close(InventoryCloseEvent.Reason.PLUGIN); yield null; }
                case "playSound", "sendMessage", "sendActionBar" -> null;
                default -> throw new UnsupportedOperationException("Synthetic player: " + method.getName());
            });
            view = view(personal, InventoryType.CRAFTING, InventoryView.class);
        }

        private void close(InventoryCloseEvent.Reason reason) {
            Bukkit.getPluginManager().callEvent(new InventoryCloseEvent(view, reason));
            view = view(personal, InventoryType.CRAFTING, InventoryView.class);
        }

        private void foreign() { view = view(Bukkit.createInventory(null, 9), InventoryType.CHEST, InventoryView.class); }

        private InventoryView view(Inventory top, InventoryType type, Class<? extends InventoryView> api) {
            return (InventoryView) Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[] {api}, (proxy, method, args) -> switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString", "getTitle", "getOriginalTitle" -> "CCR smoke view";
                case "getTopInventory" -> top;
                case "getBottomInventory" -> bottom;
                case "getPlayer" -> player;
                case "getType" -> type;
                case "convertSlot" -> args[0];
                default -> {
                    if (method.isDefault()) yield java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
                    throw new UnsupportedOperationException("Synthetic view: " + method.getName());
                }
            });
        }
    }
}
