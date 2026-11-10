package fi.dy.masa.servux.util;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSetSupplier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Permission bridge for the plugin.
 * <p>
 * Fabric used the {@code fabric-permission-api-v1} node check. On Paper the equivalent is a
 * regular Bukkit permission node (so LuckPerms and friends work), falling back to the
 * vanilla operator level when no node is granted.
 */
public class PermissionsUtil
{
    public static final String REGEX_ALLOWED = "[^a-z0-9:_./\\-]+";        // Identifier Safe

    public static boolean require(CommandSourceStack ctx, @NotNull String node, int level)
    {
        final PermissionLevel pl = PermissionLevel.byId(Mth.clamp(level, 0, PermissionLevel.OWNERS.id()));

        if (ctx.isPlayer() && ctx.getPlayer() != null)
        {
            return check(ctx.getPlayer(), node, level);
        }

        return ctx.permissions().hasPermission(new Permission.HasCommandLevel(pl));
    }

    public static boolean check(Entity entity, @NotNull String node, int level)
    {
        final PermissionLevel pl = PermissionLevel.byId(Mth.clamp(level, 0, PermissionLevel.OWNERS.id()));
        final String sanitized = sanitizeNode(node);

        if (entity instanceof ServerPlayer serverPlayer)
        {
            final Player bukkitPlayer = serverPlayer.getBukkitEntity();

            if (bukkitPlayer != null && bukkitPlayer.hasPermission(sanitized))
            {
                return true;
            }

            return serverPlayer.permissions().hasPermission(new Permission.HasCommandLevel(pl));
        }

        if (entity instanceof PermissionSetSupplier supplier)
        {
            return supplier.permissions().hasPermission(new Permission.HasCommandLevel(pl));
        }

        return false;
    }

    public static String sanitizeNode(@NotNull final String node)
    {
        return node.toLowerCase().replaceAll(REGEX_ALLOWED, "");
    }

    public static Identifier nodeId(@NotNull final String node)
    {
        return Identifier.parse(sanitizeNode(node));
    }
}
