package fr.isaac.cutcleanreborn.verification;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Storage tests across three actual JVMs. No plugin reload is treated as a restart.
 * A synthetic UUID is used only to exercise the CleanStacks config preference API;
 * containers, chunks, entities, ItemStacks, registries and service instances are real.
 */
public final class CCRPersistence extends JavaPlugin implements Listener {
    private static final String RULES = "game-rules.stuff-rules";
    private static final UUID PLAYER_ID = UUID.fromString("6adc5505-cc08-4000-bc56-8ec6b5291892");
    private final List<String> checks = new ArrayList<>();
    private final Properties checkpoint = new Properties();
    private final String bootId = UUID.randomUUID().toString();
    private final long pid = ProcessHandle.current().pid();
    private final Instant started = Instant.now();
    private JavaPlugin ccr;
    private World world;
    private Object auto;
    private Object clean;
    private Object stuff;
    private Object timberOrigins;
    private Object caneOrigins;
    private Object tools;
    private Enchantment efficiency;
    private Enchantment unbreaking;
    private Inventory chest;
    private Player identityOnlyPlayer;
    private String nonce;
    private int phase;
    private Path root;
    private Item doomedMace;
    private String removalCause;
    private boolean finished;

    @Override public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { begin(); } catch (Throwable failure) { fail(failure); }
        }, 20L);
    }

    private void begin() throws Exception {
        root = Path.of(System.getProperty("user.dir"));
        phase = Integer.parseInt(System.getProperty("ccr.persistence.phase", "0"));
        nonce = System.getProperty("ccr.persistence.nonce", "");
        require(phase >= 1 && phase <= 3 && !nonce.isBlank(), "explicit isolated harness parameters");
        ccr = (JavaPlugin) Bukkit.getPluginManager().getPlugin("CutCleanReborn");
        require(ccr != null && ccr.isEnabled(), "production CCR plugin enabled");
        require(ccr.getClass().getPackageName().matches("fr\\.isaac\\.cutcleanreborn\\.(paper|bukkit)"), "production package resolved");
        auto = call(ccr, "autoEnchantments");
        clean = call(ccr, "cleanStacks");
        stuff = call(ccr, "stuffRules");
        timberOrigins = field(field(ccr, "timber"), "placed");
        caneOrigins = field(call(ccr, "betterSugarCane"), "playerPlaced");
        Class<?> category = Class.forName(ccr.getClass().getPackageName() + ".AutoEnchantCategory", true, ccr.getClass().getClassLoader());
        tools = Arrays.stream(category.getEnumConstants()).filter(value -> value.toString().equals("TOOLS")).findFirst().orElseThrow();
        efficiency = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("efficiency"));
        unbreaking = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
        require(efficiency != null && unbreaking != null, "native enchantment registry available");
        identityOnlyPlayer = identityPlayer();
        world = Bukkit.getWorlds().getFirst();
        require(world.getName().equals("persistence-world"), "isolated persistence world selected");
        world.getChunkAt(0, 0).setForceLoaded(true);
        if (phase == 1) {
            require(!Files.exists(root.resolve("persistence-checkpoint.properties")), "fresh attempt has no previous checkpoint");
            checkpoint.setProperty("nonce", nonce);
            checkpoint.setProperty("world", world.getUID().toString());
            checkpoint.setProperty("brand", Bukkit.getVersion());
            seed();
            finish();
        } else {
            try (InputStream input = Files.newInputStream(root.resolve("persistence-checkpoint.properties"))) { checkpoint.load(input); }
            require(nonce.equals(checkpoint.getProperty("nonce")), "checkpoint nonce belongs to this attempt");
            require(world.getUID().toString().equals(checkpoint.getProperty("world")), "same saved world loaded by a new JVM");
            require(Bukkit.getVersion().equals(checkpoint.getProperty("brand")), "same native server implementation after restart");
            require(Integer.toString(phase - 1).equals(checkpoint.getProperty("phase")), "previous phase completed before restart");
            for (int previous = 1; previous < phase; previous++) {
                require(!Long.toString(pid).equals(checkpoint.getProperty("pid." + previous)), "distinct OS process from phase " + previous);
                require(!bootId.equals(checkpoint.getProperty("boot." + previous)), "distinct JVM nonce from phase " + previous);
            }
            Block block = block(4, 80, 4);
            require(block.getType() == Material.CHEST && block.getState() instanceof Chest, "real saved chest block loaded");
            chest = ((Chest) block.getState()).getBlockInventory();
            verifyCommon();
            if (phase == 2) mutateAndDespawn();
            else { verifyFinal(); finish(); }
        }
    }

    private void seed() throws Exception {
        call(stuff, "setEnabled", true);
        call(stuff, "setMaceEnabled", true);
        call(auto, "setEnabled", true);
        // Exercise the actual preference setter, not just a YAML field. This proxy
        // deliberately has no NMS/network identity; its empty inventory is synthetic.
        call(clean, "setEnabled", identityOnlyPlayer, true);
        require((boolean) call(clean, "enabled", identityOnlyPlayer), "preference enabled through production service");
        Object wood = enumValue("CleanStackService$Category", "WOOD");
        call(clean, "setTarget", wood, Material.SPRUCE_LOG, Material.BIRCH_LOG);
        call(ccr, "setOreXpBonus", Material.DIAMOND_ORE, 37);
        call(ccr, "setOreAutoSmeltEnabled", true);
        Class<?> cap = nested("StuffRulesService$EnchantmentCap");
        Object capRule = staticCall(cap, "create", "persist-cap", true, "SWORDS", "minecraft:sharpness", 2, true, 3);
        require(capRule != null, "valid cap constructed by production policy");
        call(stuff, "saveCap", capRule);
        Object armor = staticCall(nested("StuffRulesService$ArmorRule"), "create", "persist-armor", true, "DIAMOND", 2);
        require(armor != null, "valid armor rule constructed by production policy");
        call(stuff, "saveArmorRule", armor);
        call(auto, "setEnchantment", tools, Material.DIAMOND_PICKAXE, efficiency, 3);
        ItemStack original = new ItemStack(Material.DIAMOND_PICKAXE);
        original.addUnsafeEnchantment(unbreaking, 1);
        ItemStack edited = original.clone();
        require((boolean) call(auto, "apply", original), "fresh actual tool receives automatic enchantments");
        require((boolean) call(auto, "apply", edited), "second actual tool receives a distinct CCR identity");
        require(!pdcString(original, "auto_enchant_id").equals(pdcString(edited, "auto_enchant_id")), "processed tools have distinct persistent identities");
        edited.addUnsafeEnchantment(efficiency, 5);
        checkpoint.setProperty("tool.original.id", pdcString(original, "auto_enchant_id"));
        checkpoint.setProperty("tool.edited.id", pdcString(edited, "auto_enchant_id"));
        checkpoint.setProperty("tool.original.snapshot", pdcString(original, "auto_enchant_original"));
        checkpoint.setProperty("tool.original.state", pdcString(original, "auto_enchant_state"));
        checkpoint.setProperty("refresh.initial", Integer.toString(ccr.getConfig().getInt("automatic-enchantments.refresh-version", 0)));
        block(4, 80, 4).setType(Material.CHEST, false);
        chest = ((Chest) block(4, 80, 4).getState()).getBlockInventory();
        chest.setItem(0, original);
        chest.setItem(1, edited);
        ItemStack mace = new ItemStack(Material.MACE);
        String maceId = UUID.randomUUID().toString();
        ItemMeta meta = mace.getItemMeta();
        meta.getPersistentDataContainer().set(key("unique_mace_id"), PersistentDataType.STRING, maceId);
        mace.setItemMeta(meta);
        chest.setItem(2, mace);
        checkpoint.setProperty("mace.id", maceId);
        // This seeds an existing reservation; it does not pretend to test a player craft.
        call(ccr, "setConfigValues", Map.of(RULES + ".unique-mace.exists", true, RULES + ".unique-mace.id", maceId));
        for (int x : new int[] {6, 7}) {
            block(x, 80, 4).setType(Material.OAK_LOG, false);
            call(timberOrigins, "add", block(x, 80, 4));
            // A stable storage fixture: floating sand could fall after a fluid update.
            block(x, 79, 8).setType(Material.DIRT, false);
            block(x, 79, 9).setType(Material.WATER, false);
            block(x, 80, 8).setType(Material.SUGAR_CANE, false);
            call(caneOrigins, "add", block(x, 80, 8));
        }
        require((boolean) call(timberOrigins, "contains", block(6, 80, 4)), "real chunk stores timber origin");
        require((boolean) call(caneOrigins, "contains", block(6, 80, 8)), "real chunk stores cane origin");
        require(chest.getItem(0).getEnchantmentLevel(efficiency) == 3, "actual chest contains processed tool");
    }

    private void verifyCommon() throws Exception {
        require((boolean) call(stuff, "enabled"), "Stuff Rules enabled state loaded from saved config");
        require((boolean) call(stuff, "maceEnabled"), "unique mace rule enabled state persisted");
        require((int) call(ccr, "getOreXpBonus", Material.DIAMOND_ORE) == 37, "ore XP setting survives cold restart");
        require((boolean) call(ccr, "oreAutoSmeltEnabled"), "ore auto-smelt setting survives cold restart");
        List<?> caps = (List<?>) call(stuff, "enchantmentCaps");
        Object cap = caps.stream().filter(value -> uncheckedCall(value, "id").equals("persist-cap")).findFirst().orElseThrow();
        require((int) call(cap, "maximum") == 2 && (boolean) call(cap, "refundXp") && (int) call(cap, "xpPerRemovedLevel") == 3, "fresh service cache contains persisted cap and refund policy");
        List<?> armor = (List<?>) call(stuff, "armorRules");
        Object armorRule = armor.stream().filter(value -> uncheckedCall(value, "id").equals("persist-armor")).findFirst().orElseThrow();
        require((int) call(armorRule, "maximum") == 2 && (boolean) call(armorRule, "enabled"), "fresh service cache contains persisted armor rule");
        ItemStack converted = (ItemStack) call(clean, "normalise", new ItemStack(Material.SPRUCE_LOG, 17));
        require(converted.getType() == Material.BIRCH_LOG && converted.getAmount() == 17, "CleanStacks mapping works from saved config after restart");
        ItemStack original = item(0);
        ItemStack edited = item(1);
        require(checkpoint.getProperty("tool.original.id").equals(pdcString(original, "auto_enchant_id")), "chest tool keeps its exact UUID across JVMs");
        require(checkpoint.getProperty("tool.edited.id").equals(pdcString(edited, "auto_enchant_id")), "edited chest tool keeps its distinct UUID across JVMs");
        require(checkpoint.getProperty("tool.original.snapshot").equals(pdcString(original, "auto_enchant_original")), "original enchantment snapshot survives chest serialization");
        require(original.getEnchantmentLevel(unbreaking) == 1 && edited.getEnchantmentLevel(unbreaking) == 1, "pre-existing enchantments remain on both saved tools");
        require(edited.getEnchantmentLevel(efficiency) == 5, "player-edited enchantment survives restart untouched");
        require((boolean) call(timberOrigins, "contains", block(7, 80, 4)), "untouched timber origin survives cold restart");
        require((boolean) call(caneOrigins, "contains", block(7, 80, 8)), "untouched cane origin survives cold restart");
        requireType(block(7, 80, 4), Material.OAK_LOG, "untouched timber block survives world save");
        requireType(block(7, 80, 8), Material.SUGAR_CANE, "untouched cane block survives world save");
    }

    private void mutateAndDespawn() throws Exception {
        require((boolean) call(clean, "enabled", identityOnlyPlayer), "per-UUID preference loaded true after first cold restart");
        require((boolean) call(timberOrigins, "contains", block(6, 80, 4)), "first timber origin loaded from disk");
        require((boolean) call(caneOrigins, "contains", block(6, 80, 8)), "first cane origin loaded from disk");
        requireType(block(6, 80, 4), Material.OAK_LOG, "timber block to consume loaded from disk");
        requireType(block(6, 80, 8), Material.SUGAR_CANE, "cane block to consume loaded from disk");
        require((boolean) call(stuff, "maceExists"), "unique mace reservation survives cold restart");
        ItemStack mace = item(2);
        require(mace.getType() == Material.MACE && checkpoint.getProperty("mace.id").equals(pdcString(mace, "unique_mace_id")), "real chest mace identity matches persistent reservation");
        require(checkpoint.getProperty("mace.id").equals(ccr.getConfig().getString(RULES + ".unique-mace.id")), "config keeps exact unique mace identity");
        ItemStack original = item(0);
        ItemStack edited = item(1);
        require(checkpoint.getProperty("tool.original.state").equals(pdcString(original, "auto_enchant_state")), "automatic enchantment snapshot survives unchanged");
        require(original.getEnchantmentLevel(efficiency) == 3, "configured enchantment level persists in chest");
        require(!(boolean) call(auto, "apply", original), "cold-loaded tool is not enchanted twice");
        Map<?, ?> loaded = (Map<?, ?>) call(auto, "enchantments", Material.DIAMOND_PICKAXE);
        require(Objects.equals(loaded.get(efficiency), 3), "new auto-enchantment service reconstructs saved mapping");
        call(auto, "setEnchantment", tools, Material.DIAMOND_PICKAXE, efficiency, 4);
        call(auto, "requestRefresh");
        require((boolean) call(auto, "apply", original), "explicit refresh updates an unchanged cold-loaded tool");
        require(original.getEnchantmentLevel(efficiency) == 4 && original.getEnchantmentLevel(unbreaking) == 1, "refresh preserves original enchantment while updating CCR level");
        require(!(boolean) call(auto, "apply", edited) && edited.getEnchantmentLevel(efficiency) == 5, "explicit refresh protects a player-modified cold-loaded tool");
        chest.setItem(0, original);
        chest.setItem(1, edited);
        call(clean, "setEnabled", identityOnlyPlayer, false);
        require((boolean) call(timberOrigins, "remove", block(6, 80, 4)), "production tracker consumes one loaded timber origin");
        require((boolean) call(caneOrigins, "remove", block(6, 80, 8)), "production tracker consumes one loaded cane origin");
        block(6, 80, 4).setType(Material.AIR, false);
        block(6, 80, 8).setType(Material.AIR, false);
        // Actual native item despawn, not a hand-created remove event or clearMace().
        chest.setItem(2, null);
        doomedMace = world.dropItem(block(5, 81, 5).getLocation(), mace);
        doomedMace.setPickupDelay(32767);
        doomedMace.setGravity(false);
        doomedMace.setUnlimitedLifetime(false);
        doomedMace.setTicksLived(5999);
        checkpoint.setProperty("mace.entity", doomedMace.getUniqueId().toString());
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                require(!doomedMace.isValid(), "actual dropped mace entity despawned natively");
                require("DESPAWN".equals(removalCause), "native EntityRemoveEvent reports DESPAWN for exact mace entity");
                require(!(boolean) call(stuff, "maceExists"), "production listener released reservation after real entity destruction");
                require(ccr.getConfig().getString(RULES + ".unique-mace.id") == null, "destroyed mace identity removed from persistent config");
                finish();
            } catch (Throwable failure) { fail(failure); }
        }, 60L);
    }

    private void verifyFinal() throws Exception {
        require(!(boolean) call(clean, "enabled", identityOnlyPlayer), "preference change to false persisted through second cold restart");
        require(!(boolean) call(timberOrigins, "contains", block(6, 80, 4)), "consumed timber origin does not return after restart");
        require(!(boolean) call(caneOrigins, "contains", block(6, 80, 8)), "consumed cane origin does not return after restart");
        require(block(6, 80, 4).getType() == Material.AIR && block(6, 80, 8).getType() == Material.AIR, "consumed actual blocks remain absent");
        require(!(boolean) call(stuff, "maceExists") && ccr.getConfig().getString(RULES + ".unique-mace.id") == null, "mace destruction stays released after a new JVM");
        require(chest.getItem(2) == null, "destroyed unique mace does not return to the chest");
        require(world.getEntities().stream().noneMatch(entity -> entity.getUniqueId().toString().equals(checkpoint.getProperty("mace.entity"))), "destroyed native entity does not resurrect from world save");
        ItemStack original = item(0);
        int initialVersion = Integer.parseInt(checkpoint.getProperty("refresh.initial"));
        require(ccr.getConfig().getInt("automatic-enchantments.refresh-version") == initialVersion + 1, "explicit refresh generation survives cold restart");
        require(original.getEnchantmentLevel(efficiency) == 4, "refreshed tool level persists in actual chest");
        require(Objects.equals(original.getItemMeta().getPersistentDataContainer().get(key("auto_enchant_refresh_version"), PersistentDataType.INTEGER), initialVersion + 1), "refreshed item generation persists in PDC");
        require(Objects.equals(item(1).getItemMeta().getPersistentDataContainer().get(key("auto_enchant_refresh_version"), PersistentDataType.INTEGER), initialVersion), "protected edited tool retains original generation");
        require(!(boolean) call(auto, "apply", original) && !(boolean) call(auto, "apply", item(1)), "neither refreshed nor player-edited tool mutates again on cold load");
        require(Objects.equals(((Map<?, ?>) call(auto, "enchantments", Material.DIAMOND_PICKAXE)).get(efficiency), 4), "updated auto-enchantment configuration reconstructed after second restart");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void removed(EntityRemoveEvent event) {
        if (doomedMace != null && event.getEntity().getUniqueId().equals(doomedMace.getUniqueId())) removalCause = event.getCause().name();
    }

    private void finish() throws Exception {
        if (finished) return;
        checkpoint.setProperty("phase", Integer.toString(phase));
        checkpoint.setProperty("pid." + phase, Long.toString(pid));
        checkpoint.setProperty("boot." + phase, bootId);
        checkpoint.setProperty("finished." + phase, Instant.now().toString());
        ccr.saveConfig();
        world.save();
        try (OutputStream output = Files.newOutputStream(root.resolve("persistence-checkpoint.properties"))) {
            checkpoint.store(output, "Isolated CCR cold-restart checkpoint; runner requires orderly process exit too");
        }
        writeResult(true, null);
        finished = true;
        getLogger().info("PERSISTENCE PASS phase " + phase + ": " + checks.size() + " checks; PID " + pid + "; nonce " + nonce);
        Bukkit.shutdown();
    }

    private void fail(Throwable failure) {
        if (finished) return;
        finished = true;
        getLogger().log(java.util.logging.Level.SEVERE, "PERSISTENCE FAIL phase " + phase, failure);
        try { if (root != null) writeResult(false, failure.toString()); }
        catch (Exception writeFailure) { getLogger().log(java.util.logging.Level.SEVERE, "Cannot write failed result", writeFailure); }
        Bukkit.shutdown();
    }

    private void writeResult(boolean passed, String error) throws Exception {
        StringBuilder json = new StringBuilder("{\n");
        json.append("\"status\":").append(quote(passed ? "PASS" : "FAIL"));
        json.append(",\n\"phase\":").append(phase).append(",\n\"pid\":").append(pid);
        json.append(",\n\"nonce\":").append(quote(nonce)).append(",\n\"boot_id\":").append(quote(bootId));
        json.append(",\n\"started\":").append(quote(started.toString())).append(",\n\"finished\":").append(quote(Instant.now().toString()));
        json.append(",\n\"server\":").append(quote(Bukkit.getVersion())).append(",\n\"checks\":[");
        for (int index = 0; index < checks.size(); index++) { if (index > 0) json.append(','); json.append(quote(checks.get(index))); }
        json.append("],\n\"error\":").append(error == null ? "null" : quote(error)).append("\n}\n");
        Files.writeString(root.resolve("persistence-phase-" + phase + ".json"), json.toString(), StandardCharsets.UTF_8);
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t") + "\"";
    }
    private void require(boolean condition, String detail) { if (!condition) throw new AssertionError(detail); checks.add(detail); }
    private void requireType(Block block, Material expected, String detail) {
        Material actual = block.getType();
        require(actual == expected, detail + ": " + actual + " (expected " + expected + ")");
    }
    private Block block(int x, int y, int z) { return world.getBlockAt(x, y, z); }
    private NamespacedKey key(String name) { return new NamespacedKey(ccr, name); }
    private String pdcString(ItemStack item, String name) { return Objects.requireNonNull(item.getItemMeta().getPersistentDataContainer().get(key(name), PersistentDataType.STRING), name); }
    private ItemStack item(int slot) { return Objects.requireNonNull(chest.getItem(slot), "actual chest slot " + slot); }
    private Class<?> nested(String simpleName) throws Exception { return Class.forName(ccr.getClass().getPackageName() + "." + simpleName, true, ccr.getClass().getClassLoader()); }
    private Object enumValue(String type, String name) throws Exception { return Arrays.stream(nested(type).getEnumConstants()).filter(value -> value.toString().equals(name)).findFirst().orElseThrow(); }
    private static Object field(Object target, String name) throws Exception { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
    private static Object uncheckedCall(Object target, String name) { try { return call(target, name); } catch (Exception error) { throw new IllegalStateException(error); } }
    private static Object call(Object target, String name, Object... arguments) throws Exception { return invoke(target.getClass(), target, name, arguments); }
    private static Object staticCall(Class<?> type, String name, Object... arguments) throws Exception { return invoke(type, null, name, arguments); }
    private static Object invoke(Class<?> type, Object target, String name, Object... arguments) throws Exception {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
                Class<?>[] types = method.getParameterTypes();
                boolean compatible = true;
                for (int index = 0; index < types.length; index++) {
                    Class<?> parameter = types[index];
                    if (parameter == int.class) parameter = Integer.class;
                    else if (parameter == boolean.class) parameter = Boolean.class;
                    if (arguments[index] != null && !parameter.isInstance(arguments[index])) compatible = false;
                }
                if (!compatible) continue;
                method.setAccessible(true);
                try { return method.invoke(target, arguments); }
                catch (InvocationTargetException failure) {
                    if (failure.getCause() instanceof Exception exception) throw exception;
                    if (failure.getCause() instanceof Error error) throw error;
                    throw failure;
                }
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name + "/" + arguments.length);
    }

    private static Player identityPlayer() {
        ItemStack[][] inventoryContents = {new ItemStack[41]};
        PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(CCRPersistence.class.getClassLoader(), new Class<?>[] {PlayerInventory.class}, (proxy, method, arguments) -> switch (method.getName()) {
            case "getContents" -> inventoryContents[0].clone();
            case "setContents" -> { inventoryContents[0] = ((ItemStack[]) arguments[0]).clone(); yield null; }
            case "toString" -> "CCR UUID-only preference inventory";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == arguments[0];
            default -> throw new UnsupportedOperationException("Not a real player inventory: " + method.getName());
        });
        return (Player) Proxy.newProxyInstance(CCRPersistence.class.getClassLoader(), new Class<?>[] {Player.class}, (proxy, method, arguments) -> switch (method.getName()) {
            case "getUniqueId" -> PLAYER_ID;
            case "getInventory" -> inventory;
            case "toString" -> "CCR UUID-only preference identity";
            case "hashCode" -> PLAYER_ID.hashCode();
            case "equals" -> proxy == arguments[0];
            default -> throw new UnsupportedOperationException("Not a network player: " + method.getName());
        });
    }
}
