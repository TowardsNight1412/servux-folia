package fi.dy.masa.servux.network;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Channel handler contract. Mirrors the original Fabric-based interface but is backed by
 * Bukkit plugin messages instead of {@code ServerPlayNetworking}.
 */
public interface IPluginServerPlayHandler
{
    int MAX_FAILURES = 2;

    /** Channel id, e.g. {@code servux:structures}. */
    Identifier getPayloadChannel();

    boolean isPlayRegistered(Identifier channel);

    void setPlayRegistered(Identifier channel);

    void reset(Identifier channel);

    /**
     * Decode the raw payload body of this channel.
     *
     * @param input buffer positioned at the start of the payload
     * @return the decoded data, or {@code null} when the payload is invalid
     */
    @Nullable
    IServerPayloadData decode(FriendlyByteBuf input);

    /**
     * Handle a decoded payload. Invoked on the owning region thread of {@code ctx.player()}.
     */
    void receivePlayPayload(IServerPayloadData payload, ServuxContext ctx);

    /** Provider-specific dispatch. */
    default void decodeServerData(Identifier channel, ServerPlayer player, IServerPayloadData data) {}

    /** Provider-specific encoding + send. */
    void encodeServerData(ServerPlayer player, IServerPayloadData data);

    /** Send one slice produced by the packet splitter. */
    void encodeWithSplitter(ServerPlayer player, FriendlyByteBuf buffer);

    /** Low level: encode {@code data} and send it on this handler's channel. */
    default boolean sendPlayPayload(@Nonnull ServerPlayer player, @Nonnull IServerPayloadData data)
    {
        if (this.isPlayRegistered(this.getPayloadChannel()) && this.checkFailures(player))
        {
            return ServuxNet.send(player, this.getPayloadChannel(), data);
        }

        return false;
    }

    default int maxFailures()
    {
        return MAX_FAILURES;
    }

    void tickFailures(ServerPlayer player);

    boolean checkFailures(ServerPlayer player);
}
