package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.function.IntSupplier;

/**
 * Remembers the lightmap the tile entity dispatcher looks up per tile per frame.
 *
 * <p>The combined light only changes inside a client tick: placing or breaking a light source and
 * the day cycle both take effect on the next tick. The cache is therefore keyed by the world's
 * tick count and dropped as soon as that count advances, so it cannot outlive the light it
 * remembers.</p>
 */
public final class TesrLightCache {

    private static final int MISSING = Integer.MIN_VALUE;

    private static final Long2IntOpenHashMap CACHE = new Long2IntOpenHashMap();

    static {
        CACHE.defaultReturnValue(MISSING);
    }

    private static World samplerWorld;
    private static long cachedTick = Long.MIN_VALUE;

    private TesrLightCache() {
    }

    /** Drops every cached value. */
    public static void clear() {
        CACHE.clear();
        samplerWorld = null;
        cachedTick = Long.MIN_VALUE;
    }

    public static int get(World world, BlockPos pos, int minBlockLight, IntSupplier original) {
        if (minBlockLight != 0 || !GLStateManager.isMainThread()) {
            return original.getAsInt();
        }
        if (world != samplerWorld) {
            clear();
            samplerWorld = world;
        }
        return lookup(pos.toLong(), world.getTotalWorldTime(), original);
    }

    /**
     * Looks one position up for the given tick, reusing the value cached for the same tick.
     *
     * <p>The tick is explicit so the refresh boundary is testable without a live world.</p>
     */
    static int lookup(long key, long tick, IntSupplier sampler) {
        if (tick != cachedTick) {
            CACHE.clear();
            cachedTick = tick;
        }

        int packed = CACHE.get(key);
        if (packed == MISSING) {
            packed = sampler.getAsInt();
            CACHE.put(key, packed);
        }
        return packed;
    }

}
