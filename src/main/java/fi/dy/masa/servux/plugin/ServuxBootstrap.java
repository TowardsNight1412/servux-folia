package fi.dy.masa.servux.plugin;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import com.mojang.authlib.GameProfile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.scheduler.BukkitTask;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.profiling.InactiveProfiler;

import fi.dy.masa.servux.Servux;
import fi.dy.masa.servux.commands.CommandProvider;
import fi.dy.masa.servux.commands.ServuxCommand;
import fi.dy.masa.servux.dataproviders.DataProviderManager;
import fi.dy.masa.servux.dataproviders.HudDataProvider;
import fi.dy.masa.servux.event.PlayerHandler;
import fi.dy.masa.servux.event.ServerHandler;
import fi.dy.masa.servux.event.ServerInitHandler;
import fi.dy.masa.servux.network.ServuxNet;
import fi.dy.masa.servux.scheduler.TaskScheduler;
import fi.dy.masa.servux.servux.ServuxInitHandler;
import fi.dy.masa.servux.util.FoliaCompat;

/**
 * Wires Servux into the Bukkit/Folia lifecycle. This replaces the Fabric mixins for
 * {@code MinecraftServer}, {@code DedicatedServer}, {@code PlayerList} and {@code Commands}.
 */
public final class ServuxBootstrap implements Listener
{
    private final ServuxPlugin plugin;
    private final MinecraftServer server;
    private int tickCounter;
    private boolean runtimeStarted;
    private boolean commandsRegistered;
    private BukkitTask bukkitTickTask;
    private Object foliaTickTask;

    ServuxBootstrap(ServuxPlugin plugin)
    {
        this.plugin = plugin;
        this.server = ((CraftServer) Bukkit.getServer()).getServer();
    }

    void enable()
    {
        final Path serverRoot = this.plugin.getServer().getWorldContainer().toPath().toAbsolutePath().normalize();

        // DataProviderManager expects the path of server.properties; it derives <root>/config from it.
        DataProviderManager.INSTANCE.onCaptureRootDir(serverRoot.resolve("server.properties"));
        DataProviderManager.INSTANCE.onCaptureImmutable(this.server.registryAccess());

        ServerInitHandler.getInstance().registerServerInitHandler(new ServuxInitHandler());
        ((ServerInitHandler) ServerInitHandler.getInstance()).onServerInit();

        CommandProvider.getInstance().registerCommand(new ServuxCommand());

        ServuxNet.init(this.plugin);
        Bukkit.getPluginManager().registerEvents(this, this.plugin);
        Bukkit.getPluginManager().registerEvents(new EasyPlaceSupport(), this.plugin);

        ((ServerHandler) ServerHandler.getInstance()).onServerStarting(this.server);

        Servux.LOGGER.info("Servux {} (Minecraft {}) loading on {}",
                fi.dy.masa.servux.Reference.MOD_VERSION,
                fi.dy.masa.servux.Reference.MC_VERSION,
                FoliaCompat.isFolia() ? "Folia" : "Paper");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerLoad(ServerLoadEvent event)
    {
        if (event.getType() == ServerLoadEvent.LoadType.STARTUP)
        {
            this.startRuntime();
        }
    }

    /** Called once the server has finished starting: schedulers and command dispatcher exist. */
    private void startRuntime()
    {
        if (this.runtimeStarted)
        {
            return;
        }

        this.runtimeStarted = true;

        ((ServerHandler) ServerHandler.getInstance()).onServerStarted(this.server);
        this.registerCommands();
        this.applyWorldSpawn();
        this.startTickTask();

        Servux.LOGGER.info("Servux runtime started ({} data providers active)",
                DataProviderManager.INSTANCE.getAllProviders().stream().filter(p -> p.isEnabled()).count());
    }

    private void startTickTask()
    {
        final Runnable tick = this::tick;

        if (FoliaCompat.isFolia())
        {
            this.foliaTickTask = Bukkit.getGlobalRegionScheduler()
                                       .runAtFixedRate(this.plugin, task -> tick.run(), 1L, 1L);
        }
        else
        {
            this.bukkitTickTask = Bukkit.getScheduler().runTaskTimer(this.plugin, tick, 1L, 1L);
        }
    }

    private void tick()
    {
        this.tickCounter = fi.dy.masa.servux.util.ServerTicks.next();

        try
        {
            TaskScheduler.getInstance().runTasks(InactiveProfiler.INSTANCE);
            DataProviderManager.INSTANCE.tickProviders(this.server, this.tickCounter, InactiveProfiler.INSTANCE);
        }
        catch (Throwable t)
        {
            Servux.LOGGER.error("Servux tick failed at tick {}", this.tickCounter, t);
        }
    }

    private void registerCommands()
    {
        if (this.commandsRegistered)
        {
            return;
        }

        try
        {
            final Commands commands = this.server.getCommands();

            if (commands == null)
            {
                Servux.LOGGER.warn("Command dispatcher not available yet; /servux will be unavailable");
                return;
            }

            ((CommandProvider) CommandProvider.getInstance()).registerCommands(
                    commands.getDispatcher(),
                    Commands.createValidationContext(this.server.registryAccess()),
                    Commands.CommandSelection.DEDICATED);
            this.commandsRegistered = true;
        }
        catch (Throwable t)
        {
            Servux.LOGGER.error("Failed to register the /servux command", t);
        }
    }

    private void applyWorldSpawn()
    {
        if (!HudDataProvider.INSTANCE.isEnabled())
        {
            return;
        }

        final World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);

        if (world == null)
        {
            return;
        }

        final Location spawn = world.getSpawnLocation();
        final BlockPos pos = new BlockPos(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ());
        final GlobalPos globalPos = GlobalPos.of(((CraftWorld) world).getHandle().dimension(), pos);
        HudDataProvider.INSTANCE.setSpawnPos(globalPos);
        HudDataProvider.INSTANCE.checkWorldSeed(this.server);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event)
    {
        if (!this.runtimeStarted)
        {
            this.startRuntime();
        }

        final Player player = event.getPlayer();

        if (!(player instanceof CraftPlayer))
        {
            return;
        }

        final ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
        final InetSocketAddress address = player.getAddress() != null
                ? player.getAddress()
                : new InetSocketAddress("127.0.0.1", 0);
        final GameProfile profile = new GameProfile(player.getUniqueId(), player.getName());

        try
        {
            ((PlayerHandler) PlayerHandler.getInstance()).onPlayerJoin(address, profile, serverPlayer);
        }
        catch (Throwable t)
        {
            Servux.LOGGER.error("Error in Servux player join handling", t);
        }

        // Fabric clients only send custom payloads to channels the server advertised.
        // Announce in Fabric's NUL-separated format, then repeat once the client's play
        // network addon is definitely installed.
        ServuxNet.sendChannelRegistration(serverPlayer);
        FoliaCompat.runForPlayerLater(this.plugin, serverPlayer,
                () -> ServuxNet.sendChannelRegistration(serverPlayer), 40L);

        EasyPlaceSupport.attach(serverPlayer);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event)
    {
        final Player player = event.getPlayer();

        if (!(player instanceof CraftPlayer))
        {
            return;
        }

        try
        {
            ((PlayerHandler) PlayerHandler.getInstance()).onPlayerLeave(((CraftPlayer) player).getHandle());
        }
        catch (Throwable t)
        {
            Servux.LOGGER.error("Error in Servux player quit handling", t);
        }

        ServuxNet.clearPlayer(player.getUniqueId());
    }

    void disable()
    {
        if (this.bukkitTickTask != null)
        {
            this.bukkitTickTask.cancel();
            this.bukkitTickTask = null;
        }

        if (this.foliaTickTask != null)
        {
            try
            {
                ((io.papermc.paper.threadedregions.scheduler.ScheduledTask) this.foliaTickTask).cancel();
            }
            catch (Throwable ignored)
            {
            }

            this.foliaTickTask = null;
        }

        try
        {
            ((ServerHandler) ServerHandler.getInstance()).onServerStopping(this.server);
            ((ServerHandler) ServerHandler.getInstance()).onServerStopped(this.server);
        }
        catch (Throwable t)
        {
            Servux.LOGGER.error("Error while stopping Servux", t);
        }

        try
        {
            TaskScheduler.getInstance().clearTasks();
        }
        catch (Throwable ignored)
        {
        }

        for (var provider : DataProviderManager.INSTANCE.getAllProviders())
        {
            try
            {
                provider.unregisterHandler();
            }
            catch (Throwable ignored)
            {
            }
        }
    }
}
