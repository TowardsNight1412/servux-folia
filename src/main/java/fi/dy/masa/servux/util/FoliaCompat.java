package fi.dy.masa.servux.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import net.minecraft.server.level.ServerPlayer;

/**
 * Small scheduling abstraction so the same code runs on Folia (regionised, per-entity
 * schedulers) and on a regular Paper/Spigot server (main thread).
 * <p>
 * All world access in Servux must happen on the thread owning the relevant region; on Folia
 * that is the entity scheduler of the player the data belongs to.
 */
public final class FoliaCompat
{
    private static final boolean FOLIA = detectFolia();

    private FoliaCompat() {}

    public static boolean isFolia()
    {
        return FOLIA;
    }

    private static boolean detectFolia()
    {
        try
        {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        }
        catch (Throwable ignored)
        {
            return false;
        }
    }

    /** Run a task on the region thread that owns {@code player}. */
    public static void runForPlayer(Plugin plugin, ServerPlayer player, Runnable task)
    {
        Runnable safe = guard(task);

        if (FOLIA)
        {
            try
            {
                Player bukkitPlayer = player.getBukkitEntity();
                bukkitPlayer.getScheduler().run(plugin, ignored -> safe.run(), null);
                return;
            }
            catch (Throwable t)
            {
                // Fall through to a best-effort direct call.
            }
        }

        if (Bukkit.isPrimaryThread())
        {
            safe.run();
        }
        else
        {
            Bukkit.getScheduler().runTask(plugin, safe);
        }
    }

    /** Run a task on the region thread owning {@code player} after a delay. */
    public static void runForPlayerLater(Plugin plugin, ServerPlayer player, Runnable task, long delayTicks)
    {
        Runnable safe = guard(task);

        if (FOLIA)
        {
            try
            {
                player.getBukkitEntity().getScheduler().runDelayed(plugin, ignored -> safe.run(), null, delayTicks);
                return;
            }
            catch (Throwable ignored)
            {
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, safe, delayTicks);
    }

    /** Run a task on the global (server) thread. */
    public static void runGlobal(Plugin plugin, Runnable task)
    {
        Runnable safe = guard(task);

        if (FOLIA)
        {
            try
            {
                Bukkit.getGlobalRegionScheduler().run(plugin, ignored -> safe.run());
                return;
            }
            catch (Throwable t)
            {
                // Fall through.
            }
        }

        if (Bukkit.isPrimaryThread())
        {
            safe.run();
        }
        else
        {
            Bukkit.getScheduler().runTask(plugin, safe);
        }
    }

    private static Runnable guard(Runnable task)
    {
        return () ->
        {
            try
            {
                task.run();
            }
            catch (Throwable t)
            {
                fi.dy.masa.servux.Servux.LOGGER.error("Exception in Servux scheduled task", t);
            }
        };
    }
}
