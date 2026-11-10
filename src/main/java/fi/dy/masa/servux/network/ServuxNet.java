package fi.dy.masa.servux.network;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import io.netty.buffer.Unpooled;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.player.PlayerUnregisterChannelEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.craftbukkit.entity.CraftPlayer;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import fi.dy.masa.servux.Servux;
import fi.dy.masa.servux.util.FoliaCompat;

/**
 * Transport layer that replaces Fabric's {@code ServerPlayNetworking}.
 * <p>
 * Bukkit plugin messages carry exactly the same {@code minecraft:custom_payload} body that
 * the Fabric networking API would have produced, so the Servux wire format is unchanged and
 * existing Litematica / MiniHUD / Tweakeroo clients work as-is.
 */
public final class ServuxNet implements PluginMessageListener, Listener
{
    private static final ServuxNet LISTENER = new ServuxNet();
    private static final Map<String, IPluginServerPlayHandler> HANDLERS = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<String>> CLIENT_CHANNELS = new ConcurrentHashMap<>();
    private static Plugin plugin;

    private ServuxNet() {}

    public static void init(Plugin owningPlugin)
    {
        plugin = owningPlugin;
        Bukkit.getPluginManager().registerEvents(LISTENER, owningPlugin);
    }

    public static void register(IPluginServerPlayHandler handler)
    {
        final String channel = handler.getPayloadChannel().toString();
        final IPluginServerPlayHandler previous = HANDLERS.putIfAbsent(channel, handler);

        if (previous == null)
        {
            Bukkit.getMessenger().registerIncomingPluginChannel(plugin, channel, LISTENER);
            Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, channel);
        }

        handler.setPlayRegistered(handler.getPayloadChannel());
        Servux.debugLog("ServuxNet: registered channel {}", channel);
    }

    public static void unregister(IPluginServerPlayHandler handler)
    {
        final String channel = handler.getPayloadChannel().toString();

        if (HANDLERS.remove(channel) != null)
        {
            Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, channel, LISTENER);
            Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, channel);
        }

        handler.reset(handler.getPayloadChannel());
    }

    /** Equivalent of Fabric's {@code ServerPlayNetworking.canSend()}. */
    public static boolean canSend(ServerPlayer player, Identifier channel)
    {
        final Set<String> channels = CLIENT_CHANNELS.get(player.getUUID());
        return channels != null && channels.contains(channel.toString());
    }

    /**
     * Announce our channels to a Fabric client.
     * <p>
     * Fabric's {@code ClientPlayNetworking} refuses to send a payload unless the server
     * advertised the channel, and it parses the advertisement as a NUL-separated ASCII list.
     * Paper's own {@code minecraft:register} packet uses the legacy comma-separated form,
     * which Fabric cannot parse, so the client would never send its handshake. Sending the
     * Fabric-format registration here is what makes {@code canSend()} return true.
     */
    public static void sendChannelRegistration(ServerPlayer player)
    {
        if (HANDLERS.isEmpty() || player.connection == null)
        {
            return;
        }

        final List<String> channels = new ArrayList<>(HANDLERS.keySet());
        Collections.sort(channels);

        final byte[] body = String.join("\u0000", channels).getBytes(StandardCharsets.US_ASCII);

        try
        {
            final Identifier registerChannel = Identifier.parse("minecraft:register");
            player.connection.send(new ClientboundCustomPayloadPacket(new DiscardedPayload(registerChannel, body)));
            Servux.debugLog("ServuxNet: advertised {} channels to {}", channels.size(), player.getName().getString());
        }
        catch (Throwable t)
        {
            Servux.LOGGER.warn("ServuxNet: failed to advertise channels to {}: {}", player.getName().getString(), t.toString());
        }
    }

    /**
     * Encode a Servux payload and send it as one custom-payload plugin message.
     * Must be called from a thread that is allowed to touch the player.
     */
    public static boolean send(ServerPlayer player, Identifier channel, IServerPayloadData data)
    {
        final String name = channel.toString();

        if (!canSend(player, channel))
        {
            return false;
        }

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        try
        {
            data.toPacket(buffer);

            final byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);

            // Bukkit's sendPluginMessage() drops payloads for channels the *client* never
            // registered through Paper's (removed) minecraft:register handling. Send the
            // custom payload through the vanilla connection instead, exactly like the
            // Fabric mod did with ServerPlayNetworking.
            final DiscardedPayload payload = new DiscardedPayload(channel, bytes);
            player.connection.send(new ClientboundCustomPayloadPacket(payload));
            return true;
        }
        catch (Throwable t)
        {
            Servux.LOGGER.error("ServuxNet: failed to send payload on channel {}: {}", name, t.toString(), t);
            return false;
        }
        finally
        {
            buffer.release();
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message)
    {
        final IPluginServerPlayHandler handler = HANDLERS.get(channel);

        if (handler == null || !(player instanceof CraftPlayer))
        {
            return;
        }

        final ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
        final byte[] data = message.clone();

        // Paper no longer surfaces minecraft:register, so a channel becomes "sendable" for a
        // player as soon as that player actually speaks on it (which is what a client mod
        // does during its handshake).
        CLIENT_CHANNELS.computeIfAbsent(serverPlayer.getUUID(), k -> ConcurrentHashMap.newKeySet())
                       .add(channel);

        // Decode and handle on the player's owning region thread: data providers read
        // chunks, entities and block entities, which Folia forbids from the Netty thread.
        FoliaCompat.runForPlayer(plugin, serverPlayer, () ->
        {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));

            try
            {
                final IServerPayloadData payload = handler.decode(buffer);

                if (payload != null)
                {
                    handler.receivePlayPayload(payload, new ServuxContext(serverPlayer));
                }
            }
            catch (Throwable t)
            {
                Servux.LOGGER.error("ServuxNet: failed to handle payload on channel {}", channel, t);
            }
            finally
            {
                buffer.release();
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChannelRegistered(PlayerRegisterChannelEvent event)
    {
        CLIENT_CHANNELS.computeIfAbsent(event.getPlayer().getUniqueId(), k -> ConcurrentHashMap.newKeySet())
                       .add(event.getChannel());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChannelUnregistered(PlayerUnregisterChannelEvent event)
    {
        final Set<String> channels = CLIENT_CHANNELS.get(event.getPlayer().getUniqueId());

        if (channels != null)
        {
            channels.remove(event.getChannel());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event)
    {
        CLIENT_CHANNELS.remove(event.getPlayer().getUniqueId());
    }

    public static void clearPlayer(UUID uuid)
    {
        CLIENT_CHANNELS.remove(uuid);
    }
}
