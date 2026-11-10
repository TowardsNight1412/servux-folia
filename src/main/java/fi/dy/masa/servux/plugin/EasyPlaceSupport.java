package fi.dy.masa.servux.plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import fi.dy.masa.servux.Servux;
import fi.dy.masa.servux.dataproviders.ServuxConfigProvider;
import fi.dy.masa.servux.util.PlacementHandler;

/**
 * Server side implementation of "Easy Place" (Litematica / Tweakeroo).
 * <p>
 * The client encodes the desired block state into the fractional X coordinate of the
 * {@code ServerboundUseItemOn} hit vector (Servux protocol V3). Vanilla rejects such a hit
 * because it lies outside the target block, so we rewrite the hit to the block centre and
 * let vanilla place the block; the encoded state is then applied in {@link BlockPlaceEvent}.
 */
public final class EasyPlaceSupport implements Listener
{
    private static final String HANDLER_NAME = "servux_easy_place";
    private static final long MAX_AGE_MS = 3000L;

    private record Pending(BlockPos clicked, Direction side, Vec3 hit, InteractionHand hand, long time) {}

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    /** Install the packet handler on a joining player's connection. */
    public static void attach(ServerPlayer player)
    {
        try
        {
            final Connection connection = player.connection != null ? player.connection.connection : null;

            if (connection == null || connection.channel == null)
            {
                return;
            }

            final ChannelPipeline pipeline = connection.channel.pipeline();

            if (pipeline.get(HANDLER_NAME) != null)
            {
                return;
            }

            final ChannelHandlerContext context = pipeline.context(connection);

            if (context == null)
            {
                return;
            }

            pipeline.addBefore(context.name(), HANDLER_NAME, new Handler(player));
        }
        catch (Throwable t)
        {
            Servux.LOGGER.warn("EasyPlace: failed to attach packet handler: {}", t.toString());
        }
    }

    private static final class Handler extends ChannelInboundHandlerAdapter
    {
        private final ServerPlayer player;

        private Handler(ServerPlayer player)
        {
            this.player = player;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
        {
            if (msg instanceof ServerboundUseItemOnPacket packet)
            {
                try
                {
                    if (this.intercept(packet))
                    {
                        final BlockHitResult hit = packet.hitResult();
                        final BlockPos pos = hit.getBlockPos();
                        final BlockHitResult valid = new BlockHitResult(
                                Vec3.atCenterOf(pos), hit.getDirection(), pos, hit.isInside());

                        ctx.fireChannelRead(new ServerboundUseItemOnPacket(
                                packet.hand(), valid, packet.sequence(), packet.timestamp()));
                        return;
                    }
                }
                catch (Throwable t)
                {
                    Servux.debugLog("EasyPlace: packet intercept failed: {}", t.toString());
                }
            }

            ctx.fireChannelRead(msg);
        }

        private boolean intercept(ServerboundUseItemOnPacket packet)
        {
            final BlockHitResult hit = packet.hitResult();
            final BlockPos pos = hit.getBlockPos();
            final Vec3 loc = hit.getLocation();
            final int protocolValue = (int) (loc.x - (double) pos.getX()) - 2;

            // Normal clicks have their hit inside the block -> protocol value < 0.
            if (protocolValue < 0 || !ServuxConfigProvider.INSTANCE.hasPermission_EasyPlace(this.player))
            {
                return false;
            }

            PENDING.put(this.player.getUUID(),
                    new Pending(pos, hit.getDirection(), loc, packet.hand(), System.currentTimeMillis()));
            return true;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event)
    {
        final Player bukkitPlayer = event.getPlayer();
        final Pending pending = PENDING.get(bukkitPlayer.getUniqueId());

        if (pending == null)
        {
            return;
        }

        final BlockPos placed = new BlockPos(event.getBlockPlaced().getX(),
                event.getBlockPlaced().getY(), event.getBlockPlaced().getZ());
        final BlockPos expected = pending.clicked().relative(pending.side());

        if (!placed.equals(expected) && !placed.equals(pending.clicked()))
        {
            return;
        }

        PENDING.remove(bukkitPlayer.getUniqueId());

        if (System.currentTimeMillis() - pending.time() > MAX_AGE_MS)
        {
            return;
        }

        try
        {
            final ServerPlayer player = ((CraftPlayer) bukkitPlayer).getHandle();
            final ItemStack stack = CraftItemStack.asNMSCopy(event.getItemInHand());

            if (stack == null || !(stack.getItem() instanceof BlockItem blockItem))
            {
                return;
            }

            final BlockHitResult hit = new BlockHitResult(pending.hit(), pending.side(), pending.clicked(), false);
            final BlockPlaceContext ctx = new BlockPlaceContext(player, pending.hand(), stack, hit);
            final BlockState stateOrig = blockItem.getBlock().getStateForPlacement(ctx);

            if (stateOrig == null)
            {
                return;
            }

            final BlockState corrected = PlacementHandler.applyPlacementProtocolV3(
                    stateOrig, PlacementHandler.UseContext.from(ctx, pending.hand()));

            if (corrected != null && !corrected.equals(stateOrig))
            {
                player.level().setBlock(placed, corrected, 3);
            }
        }
        catch (Throwable t)
        {
            Servux.LOGGER.warn("EasyPlace: failed to apply placement state: {}", t.toString());
        }
    }
}
