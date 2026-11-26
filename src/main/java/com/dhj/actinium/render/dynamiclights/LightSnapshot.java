/*
 * Copyright © 2020 LambdAurora <email@lambdaurora.dev>
 *
 * This file is part of SodiumDynamicLights.
 *
 * Licensed under the MIT License. For more information,
 * see the LICENSE file.
 */

package com.dhj.actinium.render.dynamiclights;

import dhj.embeddedt.embeddium.impl.util.PositionUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** An immutable section index of light samples, safe to share with chunk workers. */
public final class LightSnapshot implements DynamicLightSampler {
    public static final LightSnapshot EMPTY = new LightSnapshot(new Long2ObjectOpenHashMap<>());

    private static final int BLOCK_LIGHT_COORDINATE_MASK = 0xff;

    // The snapshot exclusively owns the index and its arrays, which are never changed after construction.
    private final Long2ObjectOpenHashMap<LightSample[]> sections;

    private LightSnapshot(Long2ObjectOpenHashMap<LightSample[]> sections) {
        this.sections = sections;
    }

    static LightSnapshot build(Iterable<LightSample> sources) {
        var buckets = new Long2ObjectOpenHashMap<List<LightSample>>();
        for (LightSample source : sources) {
            source.forEachSection(0, (x, y, z) -> buckets
                    .computeIfAbsent(PositionUtil.packSection(x, y, z), key -> new ArrayList<>())
                    .add(source));
        }

        if (buckets.isEmpty()) {
            return EMPTY;
        }

        var index = new Long2ObjectOpenHashMap<LightSample[]>(buckets.size());
        for (var entry : buckets.long2ObjectEntrySet()) {
            index.put(entry.getLongKey(), entry.getValue().toArray(LightSample[]::new));
        }
        return new LightSnapshot(index);
    }

    /** Merges the strongest light at the block center without changing skylight. */
    public int merge(int x, int y, int z, int packedLight) {
        int blockCoordinate = packedLight & BLOCK_LIGHT_COORDINATE_MASK;
        int level = blockCoordinate >>> 4;
        if (level == 15 || sections.isEmpty()) {
            return packedLight;
        }

        LightSample[] sources = sections.get(PositionUtil.packSection(x >> 4, y >> 4, z >> 4));
        if (sources == null) {
            return packedLight;
        }

        for (LightSample source : sources) {
            if (source.luminance() > level) {
                level = Math.max(level, source.levelAt(x + 0.5, y + 0.5, z + 0.5));
            }
        }

        int mergedCoordinate = Math.max(blockCoordinate, level << 4);
        return (packedLight & ~BLOCK_LIGHT_COORDINATE_MASK) | mergedCoordinate;
    }

    /** Merges dynamic block light at the supplied block position. */
    @Override
    public int merge(BlockPos pos, int packedLight) {
        return merge(pos.getX(), pos.getY(), pos.getZ(), packedLight);
    }
}
