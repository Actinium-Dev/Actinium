package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Matches exclusions against the actual tile entity and renderer selected by the dispatcher. */
public final class GlowReplayExclusions {
    private static final Set<Class<?>> registered = ConcurrentHashMap.newKeySet();

    private GlowReplayExclusions() {}

    public static void exclude(Class<?> type) {
        registered.add(Objects.requireNonNull(type, "type"));
    }

    static boolean excludes(TileEntity tile, TileEntitySpecialRenderer<?> renderer) {
        final List<String> names = AngelicaConfig.glowDepthReplayExcludedClasses();
        return (tile != null && matches(tile.getClass(), names)) || matches(renderer.getClass(), names);
    }

    static boolean matches(Class<?> type, List<String> names) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            if (registered.contains(current) || names.contains(current.getName())) return true;
        }
        return false;
    }
}
