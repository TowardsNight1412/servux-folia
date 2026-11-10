package fi.dy.masa.servux.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import fi.dy.masa.servux.dataproviders.ServuxConfigProvider;

public class StringUtils
{
    // Must stay in sync with the Fabric mod's version string: Minecraft clients parse the
    // full "servux-fabric-<mc>-<version>" identifier when handshaking.
    private static final String SERVUX_VERSION = "0.12.2";
    private static final String MINECRAFT_VERSION = "26.3";

    public static String getModVersionString(String modId)
    {
        if ("servux".equals(modId))
        {
            return SERVUX_VERSION;
        }

        if ("minecraft".equals(modId))
        {
            return MINECRAFT_VERSION;
        }

        return "?";
    }

    public static String removeDefaultMinecraftNamespace(Identifier settingId)
    {
        return settingId.getNamespace().equals("minecraft") ? settingId.getPath() : settingId.toString();
    }

    public static String translateAsString(String translationKey, Object... args)
    {
        if (ServuxConfigProvider.LANG != null)
        {
            return ServuxConfigProvider.LANG.translate(translationKey, args);
        }

        throw new IllegalStateException("LANG Manager is null");
    }

    /**
     * Can replace I18n
     * @param translationKey (key)
     * @param args (...args)
     */
    public static MutableComponent translate(String translationKey, Object... args)
    {
        if (ServuxConfigProvider.LANG != null)
        {
            return ServuxConfigProvider.LANG.translateAsText(translationKey, args);
        }

        throw new IllegalStateException("LANG Manager is null");
    }

    public static CommandSyntaxException translateError(String translationKey, Object... args)
    {
        return new SimpleCommandExceptionType(translate(translationKey, args)).create();
    }
}
