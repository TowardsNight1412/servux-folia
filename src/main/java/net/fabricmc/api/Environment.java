package net.fabricmc.api;

/**
 * Compile-time stub for the Fabric loader annotation. Servux's ported classes still carry
 * {@code @Environment(EnvType.SERVER)}; the annotation has no runtime meaning on Paper.
 */
public @interface Environment
{
    EnvType value();
}
