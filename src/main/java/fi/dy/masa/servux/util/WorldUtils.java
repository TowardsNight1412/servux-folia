package fi.dy.masa.servux.util;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.world.level.Level;

/**
 * Tracks the "prevent block updates" flag per level.
 * <p>
 * The Fabric build implemented the actual suppression with a Mixin into {@code LevelChunk};
 * that is not available to a plugin, so this only records the intent. Placement code still
 * works, it just performs regular block updates.
 */
public class WorldUtils
{
    private static final Set<Level> PREVENT_UPDATES =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    public static boolean shouldPreventBlockUpdates(Level world)
    {
        return PREVENT_UPDATES.contains(world);
    }

    public static void setShouldPreventBlockUpdates(Level world, boolean preventUpdates)
    {
        if (preventUpdates)
        {
            PREVENT_UPDATES.add(world);
        }
        else
        {
            PREVENT_UPDATES.remove(world);
        }
    }
}
