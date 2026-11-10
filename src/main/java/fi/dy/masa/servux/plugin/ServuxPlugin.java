package fi.dy.masa.servux.plugin;

import org.bukkit.plugin.java.JavaPlugin;

import fi.dy.masa.servux.Servux;

/**
 * Paper/Folia plugin entry point.
 * <p>
 * Servux used to be a Fabric {@code ModInitializer}. On Paper the plugin lifecycle replaces
 * the Fabric lifecycle events; everything else (data providers, packet codecs, NBT helpers)
 * is shared with the original mod sources.
 */
public final class ServuxPlugin extends JavaPlugin
{
    private static ServuxPlugin instance;
    private ServuxBootstrap bootstrap;

    public static ServuxPlugin getInstance()
    {
        return instance;
    }

    @Override
    public void onEnable()
    {
        instance = this;

        try
        {
            this.bootstrap = new ServuxBootstrap(this);
            this.bootstrap.enable();
        }
        catch (Throwable t)
        {
            Servux.LOGGER.error("Servux failed to initialise; disabling plugin", t);
            this.getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable()
    {
        if (this.bootstrap != null)
        {
            try
            {
                this.bootstrap.disable();
            }
            catch (Throwable t)
            {
                Servux.LOGGER.error("Servux failed to shut down cleanly", t);
            }

            this.bootstrap = null;
        }

        instance = null;
    }
}
