package fi.dy.masa.servux.scheduler.tasks;

import java.util.List;
import javax.annotation.Nullable;

import org.bukkit.Bukkit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import fi.dy.masa.servux.Servux;
import fi.dy.masa.servux.plugin.ServuxPlugin;
import fi.dy.masa.servux.scheduler.TaskContext;
import fi.dy.masa.servux.schematic.selection.Box;
import fi.dy.masa.servux.util.FoliaCompat;
import fi.dy.masa.servux.util.StringUtils;
import fi.dy.masa.servux.util.WorldUtils;
import fi.dy.masa.servux.util.game.EntityUtils;
import fi.dy.masa.servux.util.position.IntBoundingBox;

public class TaskFillArea extends TaskProcessChunkMultiPhase
{
    private static final int MAX_FOLIA_RETRIES = 100;

    protected final BlockState fillState;
    @Nullable protected final BlockState replaceState;
    protected final boolean removeEntities;

    // Folia: the global tick only advances the state machine; one chunk at a
    // time is filled on the region thread that owns it.
    private ChunkPos foliaCurrentChunk = null;
    private volatile boolean foliaChunkDone = false;
    private volatile boolean foliaChunkOk = false;
    private int foliaRetries = 0;

    public TaskFillArea(TaskContext ctx,
                        final List<Box> boxes,
                        final BlockState fillState,
                        final @Nullable BlockState replaceState,
                        final boolean removeEntities)
    {
        super(ctx);

        this.fillState = fillState;
        this.replaceState = replaceState;
        this.removeEntities = removeEntities;
        this.addPerChunkBoxes(boxes);
    }

    @Override
    protected boolean canProcessChunk(ChunkPos pos)
    {
        return this.areSurroundingChunksLoaded(pos, this.context.level(), 0);
    }

    @Override
    public boolean execute(ProfilerFiller profiler)
    {
        return FoliaCompat.isFolia() ? this.executeFolia(profiler) : this.executeMultiPhase(profiler);
    }

    /**
     * Folia variant: schedule one chunk at a time on its owning region thread.
     * The global tick never writes blocks; it only retires the finished chunk
     * and starts the next one.
     */
    private boolean executeFolia(ProfilerFiller profiler)
    {
        profiler.push("folia_fill_area");

        try
        {
            if (this.foliaCurrentChunk != null)
            {
                if (!this.foliaChunkDone)
                {
                    return false;
                }

                final ChunkPos finishedPos = this.foliaCurrentChunk;
                final boolean ok = this.foliaChunkOk;
                this.foliaCurrentChunk = null;
                this.foliaChunkDone = false;

                if (ok)
                {
                    this.pendingChunks.remove(finishedPos);
                    this.foliaRetries = 0;
                }
                else if (++this.foliaRetries > MAX_FOLIA_RETRIES)
                {
                    Servux.LOGGER.warn("Servux fill/delete: giving up on chunk {} after {} attempts",
                            finishedPos, MAX_FOLIA_RETRIES);
                    this.pendingChunks.remove(finishedPos);
                    this.foliaRetries = 0;
                }

                this.updateInfoHudLines();

                if (this.pendingChunks.isEmpty())
                {
                    this.finished = true;
                    return true;
                }
            }

            if (this.pendingChunks.isEmpty())
            {
                this.finished = true;
                return true;
            }

            final ChunkPos pos = this.pendingChunks.get(0);
            this.foliaCurrentChunk = pos;
            this.foliaChunkDone = false;
            this.foliaChunkOk = false;

            try
            {
                Bukkit.getRegionScheduler().run(ServuxPlugin.getInstance(),
                        this.context.level().getWorld(), pos.x(), pos.z(), task ->
                        {
                            boolean ok = false;

                            try
                            {
                                this.fillBoxesInChunkFolia(pos);
                                ok = true;
                            }
                            catch (Throwable t)
                            {
                                Servux.LOGGER.error("Servux fill/delete: chunk {} failed", pos, t);
                            }
                            finally
                            {
                                this.foliaChunkOk = ok;
                                this.foliaChunkDone = true;
                            }
                        });
            }
            catch (Throwable t)
            {
                Servux.LOGGER.error("Servux fill/delete: failed to schedule chunk {}", pos, t);
                this.foliaCurrentChunk = null;
                this.finished = true;
                return true;
            }

            return false;
        }
        finally
        {
            profiler.pop();
        }
    }

    private void fillBoxesInChunkFolia(ChunkPos pos)
    {
        for (IntBoundingBox box : this.getBoxesInChunk(pos))
        {
            // 0x32 already suppresses neighbour/physics updates; the global
            // WorldUtils flag is shared across regions, so do not toggle it here.
            this.directFillBox(box, this.removeEntities, false);
        }
    }

    @Override
    protected void onNextChunkFetched(ChunkPos pos)
    {
        this.directFillBoxesInChunk(pos);
    }

    protected void directFillBoxesInChunk(ChunkPos pos)
    {
        for (IntBoundingBox box : this.getBoxesInChunk(pos))
        {
            this.directFillBox(box, this.removeEntities);
        }

        this.finishProcessingChunk(pos);
    }

    protected void directFillBox(IntBoundingBox box, boolean removeEntities)
    {
        this.directFillBox(box, removeEntities, true);
    }

    protected void directFillBox(IntBoundingBox box, boolean removeEntities, boolean suppressUpdates)
    {
        ServerLevel level = this.context.level();

        if (removeEntities)
        {
            directRemoveEntities(box, level);
        }

        if (suppressUpdates)
        {
            WorldUtils.setShouldPreventBlockUpdates(level, true);
        }

        BlockState barrier = Blocks.BARRIER.defaultBlockState();
        BlockPos.MutableBlockPos posMutable = new BlockPos.MutableBlockPos();

        for (int z = box.minZ(); z <= box.maxZ(); ++z)
        {
            for (int x = box.minX(); x <= box.maxX(); ++x)
            {
                for (int y = box.maxY(); y >= box.minY(); --y)
                {
                    posMutable.set(x, y, z);
                    BlockState oldState = level.getBlockState(posMutable);

                    if ((this.replaceState == null && oldState != this.fillState) || oldState == this.replaceState)
                    {
                        BlockEntity te = level.getBlockEntity(posMutable);

                        if (te instanceof Container)
                        {
                            ((Container) te).clearContent();
                            level.setBlock(posMutable, barrier, 0x32);
                        }

                        level.setBlock(posMutable, this.fillState, 0x32);
                    }
                }
            }
        }

        if (suppressUpdates)
        {
            WorldUtils.setShouldPreventBlockUpdates(level, false);
        }
    }

    public static void directRemoveEntities(IntBoundingBox box, Level level)
    {
        AABB aabb = new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
        List<Entity> entities = level.getEntities((Entity) null, aabb, EntityUtils.NOT_PLAYER);

        for (Entity entity : entities)
        {
            if ((entity instanceof Player) == false)
            {
                entity.discard();
            }
        }
    }

    @Override
    protected void onStop()
    {
        this.printCompletionMessage();
        this.sendTaskEndCommands();
        super.onStop();
    }

    protected void printCompletionMessage()
    {
        if (this.finished)
        {
            this.context.listener().addFeedback(StringUtils.translate("servux.scheduler.task.fill_area.successful"));
        }
        else
        {
            this.context.listener().addFeedback(StringUtils.translate("servux.scheduler.task.fill_area.interrupted"));
        }
    }
}
