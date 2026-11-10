package fi.dy.masa.servux.scheduler.tasks;

import java.util.ArrayList;
import java.util.Collection;
import com.google.common.collect.ArrayListMultimap;
import org.bukkit.Bukkit;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;

import fi.dy.masa.servux.Servux;
import fi.dy.masa.servux.plugin.ServuxPlugin;
import fi.dy.masa.servux.scheduler.TaskContext;
import fi.dy.masa.servux.schematic.placement.SchematicPlacement;
import fi.dy.masa.servux.util.FoliaCompat;
import fi.dy.masa.servux.util.PasteLayerBehavior;
import fi.dy.masa.servux.util.ReplaceBehavior;
import fi.dy.masa.servux.util.SchematicPlacingUtils;
import fi.dy.masa.servux.util.StringUtils;
import fi.dy.masa.servux.util.position.LayerRange;

public class TaskPasteSchematicPerChunkDirect extends TaskPasteSchematicPerChunkBase
{
	private final ArrayListMultimap<ChunkPos, SchematicPlacement> placementsPerChunk = ArrayListMultimap.create();

	// Folia: one chunk is processed at a time on the region thread that owns it.
	private static final int MAX_FOLIA_RETRIES = 100;
	private ChunkPos foliaCurrentChunk = null;
	private volatile boolean foliaChunkDone = false;
	private volatile boolean foliaChunkOk = false;
	private int foliaRetries = 0;

	public TaskPasteSchematicPerChunkDirect(TaskContext context,
	                                        final Collection<SchematicPlacement> placements,
	                                        final LayerRange layerRange,
	                                        final ReplaceBehavior replaceBehavior,
	                                        final PasteLayerBehavior layerBehavior,
	                                        final boolean changedBlockOnly,
	                                        final boolean ignoreBlocks,
	                                        final boolean ignoreEntities)
	{
		super(context, placements, layerRange, replaceBehavior, layerBehavior, changedBlockOnly, ignoreBlocks, ignoreEntities);
	}

	@Override
	public boolean canExecute()
	{
		return super.canExecute() && this.context.level() != null;
	}

	@Override
	protected void onChunkAddedForHandling(ChunkPos pos, SchematicPlacement placement)
	{
		super.onChunkAddedForHandling(pos, placement);

		this.placementsPerChunk.put(pos, placement);
	}

	@Override
	public boolean execute(ProfilerFiller profiler)
	{
		// Nothing to do
		if (this.ignoreBlocks && this.ignoreEntities)
		{
			return true;
		}

		if (FoliaCompat.isFolia())
		{
			return this.executeFolia();
		}

		profiler.push("per_chunk_paste");

		MinecraftServer server = this.context.server();
		if (server == null) return true;
		long[] tickTimes;

		try
		{
			tickTimes = server.getTickTimesNanos();
		}
		catch (Throwable t)
		{
			// Folia has per-region tick times; the global array is unavailable.
			tickTimes = new long[100];
		}

		final long vanillaTickTime = tickTimes[Math.floorMod(fi.dy.masa.servux.util.ServerTicks.get(), 100)];
		final long timeStart = Util.getNanos();

		this.sortChunkList();

		for (int chunkIndex = 0; chunkIndex < this.pendingChunks.size(); ++chunkIndex)
		{
			long currentTime = Util.getNanos();
			long elapsedTickTime = vanillaTickTime + (currentTime - timeStart);

			if (elapsedTickTime >= 60000000L)
			{
				break;
			}

			profiler.push("process_chunk");

			ChunkPos pos = this.pendingChunks.get(chunkIndex);

			if (this.canProcessChunk(pos) && this.processChunk(pos))
			{
				this.pendingChunks.remove(chunkIndex);
				--chunkIndex;
			}

			profiler.pop();
		}

		if (this.pendingChunks.isEmpty())
		{
			this.finished = true;
			profiler.pop();
			return true;
		}

		this.updateInfoHudLines();

		profiler.pop();
		return false;
	}

	/**
	 * Folia variant: the global tick thread only advances the state machine, while the
	 * actual block/entity placement for one chunk runs on that chunk's owning region
	 * thread. One chunk is in flight at a time, so the task's collections stay
	 * effectively single threaded.
	 */
	private boolean executeFolia()
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
				Servux.LOGGER.warn("Servux paste: giving up on chunk {} after {} attempts", finishedPos, MAX_FOLIA_RETRIES);
				this.pendingChunks.remove(finishedPos);
				this.foliaRetries = 0;
			}

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
							ok = this.processChunk(pos);
						}
						catch (Throwable t)
						{
							Servux.LOGGER.error("Servux paste: chunk {} failed", pos, t);
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
			Servux.LOGGER.error("Servux paste: failed to schedule chunk {}", pos, t);
			this.foliaCurrentChunk = null;
			this.finished = true;
			return true;
		}

		return false;
	}

	@Override
	protected boolean processChunk(ChunkPos pos)
	{
		// TODO ignoreBlocks and ignoreEntities

		// New list to avoid CME
		ArrayList<SchematicPlacement> placements = new ArrayList<>(this.placementsPerChunk.get(pos));

		for (SchematicPlacement placement : placements)
		{
			if (SchematicPlacingUtils.placeToWorldWithinChunk(this.context.level(), pos, placement, this.replaceBehavior, this.layerBehavior, this.layerRange, false))
			{
				this.placementsPerChunk.remove(pos, placement);
			}
		}

		return this.placementsPerChunk.containsKey(pos) == false;
	}

	@Override
	protected void onStop()
	{
		if (this.finished)
		{
			this.context.listener().addFeedback(StringUtils.translate("servux.scheduler.task.paste.successful"));
		}
		else
		{
			this.context.listener().addFeedback(StringUtils.translate("servux.scheduler.task.paste.failed"));
		}

//		InfoHud.getInstance().removeInfoHudRenderer(this, false);

		super.onStop();
	}
}
