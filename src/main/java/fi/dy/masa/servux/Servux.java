package fi.dy.masa.servux;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import fi.dy.masa.servux.dataproviders.ServuxConfigProvider;

public class Servux
{
    public static final Logger LOGGER = LogManager.getLogger(Reference.MOD_ID);

    public static void debugLog(String msg, Object... args)
    {
        if (ServuxConfigProvider.INSTANCE.hasDebugMode())
        {
            String message = "[DEBUG] "+msg;
            LOGGER.info(message, args);
        }
    }

    public static void debugLog(String msg, Throwable t)
    {
        if (ServuxConfigProvider.INSTANCE.hasDebugMode())
        {
            LOGGER.info("[DEBUG] {}", msg, t);
        }
    }
}
