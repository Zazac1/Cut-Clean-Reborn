package fr.isaac.cutcleanreborn.feature;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class UhcStuffTimerManager {
	private static final String OBJECTIVE = "uhc_stuff_timer";
	private static final String ENTRY = "Chrono";
	private static final int[] AUTO_MILESTONES_SECONDS = new int[] {300, 600, 900, 1200, 1500, 1800, 2400};

	private static boolean running = false;
	private static long startNanos = 0L;
	private static long stoppedElapsedNanos = 0L;
	private static long updateTickCounter = 0L;
	private static final Map<Integer, Long> reachedMilestones = new LinkedHashMap<>();
	private static final List<String> manualTimestamps = new ArrayList<>();

	private UhcStuffTimerManager() {
	}

	public static void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(literal("stuffstop").requires(UhcStuffTimerManager::canManageTimer).executes(ctx -> stopTimer(ctx.getSource())));
			dispatcher.register(
				literal("stuffmark").requires(UhcStuffTimerManager::canManageTimer)
					.then(argument("label", StringArgumentType.greedyString())
						.executes(ctx -> addManualTimestamp(ctx.getSource(), StringArgumentType.getString(ctx, "label"))))
			);
		});
	}

	public static void onServerStarted(MinecraftServer server) {
		startNanos = System.nanoTime();
		stoppedElapsedNanos = 0L;
		updateTickCounter = 0L;
		running = true;
		reachedMilestones.clear();
		manualTimestamps.clear();
		setupScoreboard(server);
		updateScoreboard(server);
	}

	public static void onServerTick(MinecraftServer server) {
		if (!running) {
			return;
		}

		updateTickCounter++;
		if (updateTickCounter % 2L == 0L) {
			updateScoreboard(server);
		}
		checkAutomaticMilestones(server);
	}

	private static int stopTimer(ServerCommandSource source) {
		if (!running) {
			source.sendFeedback(() -> Text.literal("Le timer stuff est deja arrete."), false);
			return Command.SINGLE_SUCCESS;
		}

		stoppedElapsedNanos = getElapsedNanos();
		running = false;
		updateScoreboard(source.getServer());
		source.sendFeedback(() -> Text.literal("Timer stoppe a " + formatDuration(getElapsedNanos()) + "."), true);
		if (!manualTimestamps.isEmpty()) {
			source.sendFeedback(() -> Text.literal("Timestamps manuels: " + String.join(" | ", manualTimestamps)), true);
		}
		return Command.SINGLE_SUCCESS;
	}

	private static boolean canManageTimer(ServerCommandSource source) {
		return source.getPermissions().hasPermission(new Permission.Level(PermissionLevel.GAMEMASTERS));
	}

	private static int addManualTimestamp(ServerCommandSource source, String label) {
		String clean = label == null ? "" : label.trim();
		if (clean.isEmpty()) {
			source.sendError(Text.literal("Utilise /stuffmark <label>."));
			return 0;
		}

		String stamp = formatDuration(getElapsedNanos());
		String line = stamp + " - " + clean;
		manualTimestamps.add(line);
		source.sendFeedback(() -> Text.literal("Timestamp ajoute: " + line), true);
		return Command.SINGLE_SUCCESS;
	}

	private static void setupScoreboard(MinecraftServer server) {
		ServerCommandSource source = server.getCommandSource().withSilent();
		executeCommand(server, source, "scoreboard objectives remove " + OBJECTIVE);
		executeCommand(server, source, "scoreboard objectives add " + OBJECTIVE + " dummy");
		executeCommand(server, source, "scoreboard objectives setdisplay sidebar " + OBJECTIVE);
		executeCommand(server, source, "scoreboard players set " + ENTRY + " " + OBJECTIVE + " 1");
	}

	private static void updateScoreboard(MinecraftServer server) {
		ServerCommandSource source = server.getCommandSource().withSilent();
		executeCommand(server, source, "scoreboard players set " + ENTRY + " " + OBJECTIVE + " 1");

		String titleJson = "{\"text\":\"Stuff Timer " + formatDuration(getElapsedNanos()) + "\"}";
		executeCommand(server, source, "scoreboard objectives modify " + OBJECTIVE + " displayname " + titleJson);
	}

	private static void checkAutomaticMilestones(MinecraftServer server) {
		long totalSeconds = getElapsedNanos() / 1_000_000_000L;
		for (int milestone : AUTO_MILESTONES_SECONDS) {
			if (totalSeconds < milestone || reachedMilestones.containsKey(milestone)) {
				continue;
			}

			reachedMilestones.put(milestone, totalSeconds);
			String label = milestoneLabel(milestone);
			String message = "[Timestamp] " + label + " atteint a " + formatDuration(getElapsedNanos());
			server.getPlayerManager().broadcast(Text.literal(message), false);
		}
	}

	private static String milestoneLabel(int seconds) {
		int minutes = seconds / 60;
		return minutes + "m";
	}

	private static void executeCommand(MinecraftServer server, ServerCommandSource source, String command) {
		var commandManager = server.getCommandManager();
		var parsed = commandManager.getDispatcher().parse(command, source);
		commandManager.execute(parsed, command);
	}

	private static long getElapsedNanos() {
		return running ? Math.max(0L, System.nanoTime() - startNanos) : stoppedElapsedNanos;
	}

	private static String formatDuration(long elapsedNanos) {
		long totalMillis = elapsedNanos / 1_000_000L;
		long hours = totalMillis / 3_600_000L;
		long minutes = (totalMillis % 3_600_000L) / 60_000L;
		long seconds = (totalMillis % 60_000L) / 1_000L;
		long millis = totalMillis % 1_000L;
		if (hours > 0L) {
			return String.format("%d:%02d:%02d.%03d", hours, minutes, seconds, millis);
		}
		return String.format("%02d:%02d.%03d", minutes, seconds, millis);
	}
}
