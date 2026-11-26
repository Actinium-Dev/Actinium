package com.dhj.actinium.render.dynamiclights;

import net.minecraft.util.math.BlockPos;

/** A read-only view of the dynamic block light available to a render pass. */
@FunctionalInterface
public interface DynamicLightSampler {
    /** Raises the block-light component at the sample position and preserves sky light. */
    int merge(BlockPos pos, int packedLight);
}
