package com.gtnewhorizons.angelica.rendering.tesr;

import org.junit.jupiter.api.Test;
import net.minecraft.util.math.BlockPos;

import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TesrLightCacheTest {

    @Test
    void reusesTheLookupWithinOneTickAndRepeatsItOnTheNext() {
        final int[] lookups = {0};
        final long key = new BlockPos(3, 64, 7).toLong();
        final IntSupplier sampler = () -> {
            lookups[0]++;
            return 0xF00000 | lookups[0];
        };

        final int first = TesrLightCache.lookup(key, 100L, sampler);

        assertEquals(first, TesrLightCache.lookup(key, 100L, sampler));
        assertEquals(1, lookups[0], "the same tick reads the cached value");

        final int nextTick = TesrLightCache.lookup(key, 101L, sampler);

        assertNotEquals(first, nextTick, "a new tick has to refresh the cached light");
        assertEquals(2, lookups[0]);

        assertEquals(0xF00000 | 2, TesrLightCache.lookup(key, 101L, sampler));
        assertEquals(2, lookups[0], "the refreshed value is reused for the rest of the tick");
    }

    @Test
    void cachesEachPositionSeparately() {
        final int[] lookups = {0};
        final IntSupplier sampler = () -> ++lookups[0];

        final int a = TesrLightCache.lookup(new BlockPos(1, 2, 3).toLong(), 5L, sampler);
        final int b = TesrLightCache.lookup(new BlockPos(1, 2, 4).toLong(), 5L, sampler);

        assertNotEquals(a, b);
        assertEquals(2, lookups[0]);
        assertEquals(a, TesrLightCache.lookup(new BlockPos(1, 2, 3).toLong(), 5L, sampler));
        assertEquals(2, lookups[0]);
    }
}
