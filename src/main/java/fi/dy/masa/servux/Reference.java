package fi.dy.masa.servux;

import fi.dy.masa.servux.util.StringUtils;

public class Reference
{
    public static final String MOD_ID = "servux";
    public static final String MOD_NAME = "Servux";
    public static final String MOD_VERSION = StringUtils.getModVersionString(MOD_ID);
    public static final String MC_VERSION = "26.3";
    // Wire-compatibility marker: Litematica/MiniHUD/Tweakeroo validate that the metadata
    // "servux" field starts with "servux-fabric-<mc version>". Keeping "fabric" here is
    // required even though this build is a Paper/Folia plugin.
    public static final String MOD_TYPE = "fabric";
    public static final String MOD_STRING = MOD_ID+"-"+MOD_TYPE+"-"+MC_VERSION+"-"+MOD_VERSION;

    public static final boolean LOCAL_DEBUG = false;                // Enforce DEBUG_MODE ON (Or use Property/Environment Variable)
    public static final boolean EXPERIMENTAL_MODE = false;          // Enforce EXPERIMENTAL_MODE when TRUE (Experimental code)

    public static final boolean RUNNING_IN_IDE = false;
    public static final boolean DEBUG_MODE = isDebug();
    public static final boolean ANSI_MODE = false;

    private static boolean propertyCheck()
    {
        final String override = System.getProperty(MOD_ID+".debug.mode");
        final String envOverride = System.getenv(MOD_ID.toUpperCase()+"_DEBUG_MODE");

        if (override != null) { return Boolean.parseBoolean(override); }
        if (envOverride != null) { return Boolean.parseBoolean(envOverride); }

        return false;
    }

    private static boolean isDebug()
    {
        return (propertyCheck() || LOCAL_DEBUG || EXPERIMENTAL_MODE);
    }
}
