package fr.isaac.cutcleanreborn.paper;

import java.io.File;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** Friendly entry points for players and administrators; aliases retain their original behavior. */
final class CutCleanCommands implements TabExecutor {
    static final String PREFIX = "§6CCR §8» §7";
    private final CutCleanRebornPlugin plugin;

    CutCleanCommands(CutCleanRebornPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("cleanstacks")) return cleanStacks(sender, args);
        String action = args.length == 0 ? (sender instanceof Player && sender.hasPermission("cutcleanreborn.config") ? "config" : "help") : args[0].toLowerCase(Locale.ROOT);
        if (args.length > 1) { help(sender); return true; }
        switch (action) {
            case "ping" -> sender.sendMessage(PREFIX + "§aPong!");
            case "help" -> help(sender);
            case "status" -> {
                sender.sendMessage(PREFIX + "§eCutCleanReborn §7• " + plugin.getPluginMeta().getVersion());
                sender.sendMessage("§7Timber: " + state(plugin.treeCutterEnabled()) + " §8| §7Auto Smelt: " + state(plugin.oreAutoSmeltEnabled()));
                sender.sendMessage("§7Auto Enchant: " + state(plugin.autoEnchantments().enabled()) + " §8| §7Sugar Cane: " + state(plugin.betterSugarCane().enabled()));
                sender.sendMessage("§7Stuff Rules: " + state(plugin.stuffRules().enabled()));
            }
            case "config" -> {
                if (!admin(sender)) return true;
                if (sender instanceof Player player) new ConfigMainMenu(plugin).open(player);
                else sender.sendMessage(PREFIX + "Open the configuration in-game, or edit config.yml and use /ccr reload.");
            }
            case "reload" -> {
                if (!admin(sender)) return true;
                try {
                    // Validate first: Bukkit's default reload otherwise silently falls back on a broken YAML file.
                    new YamlConfiguration().load(new File(plugin.getDataFolder(), "config.yml"));
                    plugin.reloadConfig();
                    plugin.refreshServices();
                    sender.sendMessage(PREFIX + "§aConfiguration reloaded. Open menus were closed to refresh their values.");
                } catch (Exception exception) {
                    sender.sendMessage(PREFIX + "§cCannot read config.yml: " + exception.getMessage());
                    plugin.getLogger().warning("Configuration reload rejected: " + exception.getMessage());
                }
            }
            default -> help(sender);
        }
        return true;
    }

    private boolean cleanStacks(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage(PREFIX + "Use this command in-game."); return true; }
        if (args.length > 1) { sender.sendMessage(PREFIX + "/cleanstacks [on|off|status]"); return true; }
        String action = args.length == 0 ? "toggle" : args[0].toLowerCase(Locale.ROOT);
        Boolean enabled = switch (action) {
            case "on", "true" -> true;
            case "off", "false" -> false;
            case "toggle" -> !plugin.cleanStacks().enabled(player);
            default -> null;
        };
        if (enabled == null && !action.equals("status")) { sender.sendMessage(PREFIX + "/cleanstacks [on|off|status]"); return true; }
        if (enabled != null) plugin.cleanStacks().setEnabled(player, enabled);
        sender.sendMessage(PREFIX + "Clean Stacks: " + state(plugin.cleanStacks().enabled(player)));
        return true;
    }

    private static boolean admin(CommandSender sender) {
        if (sender.hasPermission("cutcleanreborn.config")) return true;
        sender.sendMessage(PREFIX + "§cYou do not have permission to change the configuration.");
        return false;
    }

    static String state(boolean value) { return value ? "§aON" : "§cOFF"; }

    private static void help(CommandSender sender) {
        sender.sendMessage(PREFIX + "§e/ccr status §7— current gameplay rules");
        sender.sendMessage("§e/cleanstacks [on|off|status] §7— personal wood and stone conversion");
        if (sender.hasPermission("cutcleanreborn.config")) {
            sender.sendMessage("§e/ccr config §7— configure every feature in-game");
            sender.sendMessage("§e/ccr reload §7— reload config.yml");
        }
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        List<String> choices = command.getName().equalsIgnoreCase("cleanstacks") ? List.of("on", "off", "status", "true", "false", "toggle")
                : sender.hasPermission("cutcleanreborn.config") ? List.of("config", "status", "reload", "help", "ping") : List.of("status", "help", "ping");
        return choices.stream().filter(choice -> choice.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
}
