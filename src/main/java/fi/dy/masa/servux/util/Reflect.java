package fi.dy.masa.servux.util;

import java.lang.reflect.Field;

/**
 * Minimal reflection helper used where the Fabric build relied on an access widener
 * (e.g. reading vanilla private fields such as {@code TagValueInput.input}).
 */
public final class Reflect
{
    private Reflect() {}

    public static Field findField(Class<?> type, String name)
    {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass())
        {
            try
            {
                final Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            }
            catch (NoSuchFieldException ignored)
            {
            }
        }

        return null;
    }

    public static Object get(Object target, String name)
    {
        if (target == null)
        {
            return null;
        }

        try
        {
            final Field field = findField(target.getClass(), name);

            if (field == null)
            {
                throw new NoSuchFieldException(name);
            }

            return field.get(target);
        }
        catch (Throwable t)
        {
            throw new IllegalStateException("Cannot read field '" + name + "' from " + target.getClass().getName(), t);
        }
    }

    public static long getLong(Object target, String name)
    {
        final Object value = get(target, name);
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
