package fr.isaac.cutcleanreborn.verification;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Locale;
import org.bukkit.GameMode;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Test-only scene preparation; never included in the production artifact. */
public final class CCRClientQA extends JavaPlugin implements Listener {
    private Location scene;
    private String sceneName = "none";

    @Override public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("REAL CLIENT QA READY: /ccrqa setup, /ccrqa scene <timber|ore|cane|mob|stacks>, /ccrqa observe");
    }

    @EventHandler public void join(PlayerJoinEvent event) {
        // Protect only this isolated QA player while the UI test driver observes menus.
        event.getPlayer().setInvulnerable(true);
    }

    @EventHandler public void respawn(PlayerRespawnEvent event) {
        getServer().getScheduler().runTask(this, () -> event.getPlayer().setInvulnerable(true));
    }

    @EventHandler public void menuOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && isCcr(event.getInventory().getHolder()))
            log(player, "menu-open", event.getInventory().getHolder().getClass().getSimpleName() + ", mode=" + player.getGameMode());
    }

    @EventHandler public void menuClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !isCcr(event.getInventory().getHolder())) return;
        String reason = "Bukkit/no reason API";
        try { reason = String.valueOf(event.getClass().getMethod("getReason").invoke(event)); }
        catch (ReflectiveOperationException ignored) { }
        log(player, "menu-close", event.getInventory().getHolder().getClass().getSimpleName() + ", reason=" + reason + ", mode=" + player.getGameMode());
        getServer().getScheduler().runTaskLater(this, () -> log(player, "menu-after-close",
                "type=" + player.getOpenInventory().getType() + ", holder=" + player.getOpenInventory().getTopInventory().getHolder()), 3L);
    }

    private boolean isCcr(Object holder) {
        return holder != null && holder.getClass().getName().startsWith("fr.isaac.cutcleanreborn.");
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Use this scene helper from the connected QA player."); return true; }
        if (!player.isOp()) { player.sendMessage("QA helper is restricted to operators in this isolated world."); return true; }
        String action = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "setup" -> setup(player);
            case "scene" -> { if (args.length < 2) player.sendMessage("timber / ore / cane / mob / stacks"); else prepare(player, args[1]); }
            case "observe" -> observe(player);
            case "creative" -> player.setGameMode(GameMode.CREATIVE);
            case "survival" -> player.setGameMode(GameMode.SURVIVAL);
            default -> player.sendMessage("/ccrqa setup | scene <timber|ore|cane|mob|stacks> | observe | creative | survival");
        }
        return true;
    }

    private void setup(Player player) {
        World world = player.getWorld();
        world.setTime(6000);
        world.setStorm(false);
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        int y = world.getHighestBlockYAt(0, 0) + 1;
        scene = new Location(world, 0, y, 0);
        for (int x = -7; x <= 7; x++) for (int z = -5; z <= 10; z++) {
            world.getBlockAt(x, y - 1, z).setType(Material.GRASS_BLOCK, false);
            for (int dy = 0; dy < 8; dy++) world.getBlockAt(x, y + dy, z).setType(Material.AIR, false);
        }
        player.setGameMode(GameMode.CREATIVE);
        player.setInvulnerable(true);
        player.teleport(new Location(world, .5, y, 5.5, 180, 15));
        player.setFoodLevel(20); player.setHealth(20); player.setLevel(50);
        log(player, "setup", "Native player connected to " + getServer().getVersion());
        player.sendMessage("CCR QA ready. This is an isolated QA world. /ccr opens the real configuration menu.");
    }

    private void prepare(Player player, String name) {
        if (scene == null) setup(player);
        sceneName = name.toLowerCase(Locale.ROOT);
        World world = scene.getWorld();
        int y = scene.getBlockY();
        for (int x = -3; x <= 3; x++) for (int z = -2; z <= 2; z++)
            for (int dy = 0; dy < 7; dy++) world.getBlockAt(x, y + dy, z).setType(Material.AIR, false);
        player.getInventory().clear();
        player.setGameMode(GameMode.SURVIVAL);
        player.setInvulnerable(true);
        player.setHealth(20); player.setFoodLevel(20);
        player.teleport(new Location(world, .5, y, 4.5, 180, 20));
        // Instant normal mining makes isolated UI clicks usable, without invoking break events synthetically.
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 12000, 64, false, false));
        switch (sceneName) {
            case "timber" -> {
                for (int dy = 0; dy < 4; dy++) world.getBlockAt(0, y + dy, 0).setType(Material.OAK_LOG, false);
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                    var leaf=world.getBlockAt(x, y + 4, z);
                    leaf.setType(Material.OAK_LEAVES, false);
                    var data=(Leaves)leaf.getBlockData();
                    data.setDistance(1);
                    data.setPersistent(false);
                    leaf.setBlockData(data, false);
                }
                player.getInventory().setItem(0, new ItemStack(Material.DIAMOND_AXE));
            }
            case "ore" -> {
                world.getBlockAt(0, y + 1, 0).setType(Material.IRON_ORE, false);
                world.getBlockAt(1, y + 1, 0).setType(Material.DIAMOND_ORE, false);
                world.getBlockAt(-1, y + 1, 0).setType(Material.DEEPSLATE_DIAMOND_ORE, false);
                ItemStack pick = new ItemStack(Material.DIAMOND_PICKAXE);
                pick.addUnsafeEnchantment(Enchantment.FORTUNE, 3);
                player.getInventory().setItem(0, pick);
                ItemStack silk = new ItemStack(Material.DIAMOND_PICKAXE);
                silk.addUnsafeEnchantment(Enchantment.SILK_TOUCH, 1);
                player.getInventory().setItem(1, silk);
            }
            case "cane" -> {
                world.getBlockAt(-1, y - 1, 0).setType(Material.WATER, false);
                for (int dy = 0; dy < 3; dy++) world.getBlockAt(0, y + dy, 0).setType(Material.SUGAR_CANE, false);
                player.getInventory().setItem(0, new ItemStack(Material.DIAMOND_SHOVEL));
            }
            case "mob" -> {
                world.spawnEntity(new Location(world, .5, y, .5), EntityType.COW);
                player.getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD));
            }
            case "stacks" -> {
                player.getInventory().addItem(new ItemStack(Material.OAK_LOG, 17), new ItemStack(Material.COBBLESTONE, 23));
            }
            default -> player.sendMessage("Unknown QA scene.");
        }
        player.getInventory().setHeldItemSlot(0);
        log(player, "scene", sceneName);
        player.sendMessage("QA scene: " + sceneName + ". Interact normally, then /ccrqa observe records actual state (not an automatic PASS).");
    }

    private void observe(Player player) {
        int logs = 0, cane = 0, ores = 0;
        if (scene != null) {
            int y = scene.getBlockY();
            for (int x = -3; x <= 3; x++) for (int z = -2; z <= 2; z++) for (int dy = 0; dy < 7; dy++) {
                Material type = scene.getWorld().getBlockAt(x, y + dy, z).getType();
                if (type == Material.OAK_LOG) logs++;
                if (type == Material.SUGAR_CANE) cane++;
                if (type.name().endsWith("_ORE")) ores++;
            }
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        int damage = hand.getItemMeta() instanceof Damageable meta ? meta.getDamage() : 0;
        String detail = "scene=" + sceneName + ", logs=" + logs + ", cane=" + cane + ", ores=" + ores
                + ", held=" + hand.getType() + ", damage=" + damage + ", level=" + player.getLevel();
        player.sendMessage(detail);
        log(player, "observation", detail + ", inventory=" + java.util.Arrays.toString(player.getInventory().getContents()));
    }

    private void log(Player player, String action, String details) {
        String line = Instant.now() + " | " + player.getName() + " | " + action + " | " + details;
        getLogger().info(line);
        try {
            Files.writeString(getServer().getWorldContainer().toPath().resolve("client-qa-observations.log"), line + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) { getLogger().warning("Could not persist QA observation: " + e.getMessage()); }
    }
}
