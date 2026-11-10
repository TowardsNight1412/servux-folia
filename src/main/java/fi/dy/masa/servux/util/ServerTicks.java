package fi.dy.masa.servux.util;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Global, monotonic server tick counter.
 * <p>
 * Folia's {@code MinecraftServer#getTickCount()} throws {@link UnsupportedOperationException}
 * because ticking is regionised. Servux only needs a monotonic counter for its timeouts, so
 * the plugin wide tick task provides one.
 */
public final class ServerTicks
{
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private ServerTicks() {}

    public static int get()
    {
        return COUNTER.get();
    }

    public static int next()
    {
        return COUNTER.incrementAndGet();
    }
}
