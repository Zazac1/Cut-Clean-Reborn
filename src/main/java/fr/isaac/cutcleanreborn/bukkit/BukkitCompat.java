package fr.isaac.cutcleanreborn.bukkit;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Small CraftBukkit adapter: Bukkit has neither close reasons, a tick counter, nor action bars. */
public final class BukkitCompat {
    private static final Set<UUID> PROGRAMMATIC_CLOSES = new HashSet<>();
    private static int tick;
    private static Method chatParser, playerHandle, packetSender;
    private static Field playerConnection;
    private static Constructor<?> actionBarConstructor;
    private BukkitCompat() { }

    static void initialize(CutCleanRebornPlugin plugin) {
        tick = 0;
        PROGRAMMATIC_CLOSES.clear();
        Bukkit.getScheduler().runTaskTimer(plugin, () -> tick++, 1L, 1L);
        // Resolve eagerly so an incompatible server fails visibly rather than silently losing hints.
        try { resolveActionBar(); actionBarPacket("CCR ready"); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Unsupported CraftBukkit action-bar API", failure); }
    }

    static int currentTick() { return tick; }
    @SuppressWarnings("unchecked")
    static boolean blockDrops(org.bukkit.World world) {
        org.bukkit.GameRule<?> rule = org.bukkit.GameRule.getByName("block_drops");
        if (rule == null) rule = org.bukkit.GameRule.getByName("doTileDrops");
        if (rule == null) throw new IllegalStateException("Server has no block-drops game rule");
        return world.getGameRuleValue((org.bukkit.GameRule<Boolean>) rule) != Boolean.FALSE;
    }
    public static boolean isProgrammaticClose(Player player) { return PROGRAMMATIC_CLOSES.contains(player.getUniqueId()); }
    public static void closeInventory(Player player) {
        PROGRAMMATIC_CLOSES.add(player.getUniqueId());
        try { player.closeInventory(); }
        finally { PROGRAMMATIC_CLOSES.remove(player.getUniqueId()); }
    }

    /** Sends the native action-bar packet on CraftBukkit too, without requiring Spigot or Adventure. */
    static void actionBar(Player player, String text) {
        try {
            Object packet = actionBarPacket(text);
            Object handle = playerHandle.invoke(player);
            packetSender.invoke(playerConnection.get(handle), packet);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot send CraftBukkit action bar", failure);
        }
    }

    private static Object actionBarPacket(String text) throws ReflectiveOperationException {
        return actionBarConstructor.newInstance(chatParser.invoke(null, text));
    }

    private static void resolveActionBar() throws ReflectiveOperationException {
        String craft = Bukkit.getServer().getClass().getPackageName();
        Class<?> chat = Class.forName(craft + ".util.CraftChatMessage");
        chatParser = chat.getMethod("fromStringOrNull", String.class);
        Object component = chatParser.invoke(null, "CCR");
        Class<?> packet = Class.forName("net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket");
        for (Constructor<?> constructor : packet.getConstructors())
            if (constructor.getParameterCount() == 1 && constructor.getParameterTypes()[0].isInstance(component)) { actionBarConstructor = constructor; break; }
        if (actionBarConstructor == null) throw new NoSuchMethodException("Action-bar component constructor");
        playerHandle = Class.forName(craft + ".entity.CraftPlayer").getMethod("getHandle");
        for (Field field : playerHandle.getReturnType().getFields()) {
            String type = field.getType().getSimpleName();
            if (type.equals("PlayerConnection") || type.equals("ServerGamePacketListenerImpl")) { playerConnection = field; break; }
        }
        if (playerConnection == null) throw new NoSuchFieldException("Player network connection");
        for (Method method : playerConnection.getType().getMethods()) {
            if (method.getParameterCount() == 1 && method.getReturnType() == void.class
                    && method.getParameterTypes()[0].getName().equals("net.minecraft.network.protocol.Packet")) { packetSender = method; break; }
        }
        if (packetSender == null) throw new NoSuchMethodException("Native packet sender");
    }
}
