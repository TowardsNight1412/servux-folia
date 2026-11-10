package fi.dy.masa.servux.loggers;

import java.util.concurrent.TimeUnit;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;

import fi.dy.masa.servux.loggers.data.TPSData;
import fi.dy.masa.servux.util.Reflect;
import fi.dy.masa.servux.util.data.tag.CompoundData;
import fi.dy.masa.servux.util.data.tag.util.DataOps;

public class DataLoggerTPS extends DataLoggerBase<CompoundData>
{
    public DataLoggerTPS(DataLogger type)
    {
        super(type);
    }

    @Override
    public CompoundData getResult(MinecraftServer server)
    {
        try
        {
            return (CompoundData) TPSData.CODEC.encodeStart(server.registryAccess().createSerializationContext(DataOps.INSTANCE), this.build(server)).getOrThrow();
        }
        catch (Exception e)
        {
            return new CompoundData();
        }
    }
    
    private TPSData build(MinecraftServer server)
    {
        ServerTickRateManager tickManager = server.tickRateManager();
        boolean frozen = tickManager.isFrozen();
        boolean sprinting = tickManager.isSprinting();
        double mspt;

        try
        {
            mspt = (double) server.getAverageTickTimeNanos() / TimeUnit.MILLISECONDS.toNanos(1L);
        }
        catch (Throwable t)
        {
            // Folia keeps tick times per region; not available globally.
            mspt = 0.0D;
        }
        double tps = 1000.0D / Math.max(sprinting ? 0.0 : tickManager.millisecondsPerTick(), mspt);

        if (frozen)
        {
            tps = 0.0d;
        }
        
        return new TPSData(mspt,
                        tps,
                        Reflect.getLong(tickManager, "remainingSprintTicks"),
                        frozen,
                        sprinting,
                        tickManager.isSteppingForward()
        );
    }
}
