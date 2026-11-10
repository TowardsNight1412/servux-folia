package fi.dy.masa.servux.network;

import net.minecraft.server.level.ServerPlayer;

/**
 * Replaces Fabric's {@code ServerPlayNetworking.Context}: the packet handler simply needs
 * the player that sent the payload. Handlers are always invoked on the player's owning
 * region thread, so no further thread hopping is required inside them.
 */
public record ServuxContext(ServerPlayer player)
{
}
