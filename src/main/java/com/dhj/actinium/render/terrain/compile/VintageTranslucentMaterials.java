package com.dhj.actinium.render.terrain.compile;

import dhj.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import dhj.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockRenderLayer;
import org.jetbrains.annotations.Nullable;

/** Selects terrain materials for fluids and stained glass in the translucent layer. */
public final class VintageTranslucentMaterials {
    private VintageTranslucentMaterials() {
    }

    public static Material select(RenderPassConfiguration<?> configuration, BlockRenderLayer layer,
                                  Material layerMaterial, @Nullable Block block, boolean fluid, boolean writeStainedGlassDepth) {
        if (fluid) {
            return VanillaQuadMaterial.selectFluid(layerMaterial, configuration, layer);
        }
        if (writeStainedGlassDepth && layer == BlockRenderLayer.TRANSLUCENT && isStainedGlass(block)) {
            // The composite needs glass depth even where the opaque depth still represents the sky.
            return configuration.defaultFluidMaterial();
        }
        return layerMaterial;
    }

    static boolean isStainedGlass(@Nullable Block block) {
        return block != null && (block == Blocks.STAINED_GLASS || block == Blocks.STAINED_GLASS_PANE);
    }
}
