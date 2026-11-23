package com.dhj.actinium.render.terrain.compile.pipeline;

import com.dhj.actinium.resourcepack.ctm.TerrainPackRules;
import com.dhj.actinium.resourcepack.ctm.TerrainPackRules.Light;
import com.dhj.actinium.resourcepack.ctm.TerrainPackRules.OutputQuad;
import com.dhj.actinium.resourcepack.ctm.TerrainPackRules.PaneReplacement;
import com.dhj.actinium.resourcepack.item.AtlasEmissive;
import com.dhj.actinium.resourcepack.item.ResourcePackItemFeatures;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Resolves pack rules before a face is submitted to either terrain renderer. */
public record TerrainPackGeometry(TerrainPackRules.Snapshot rules, AtlasEmissive emissive) {
    public static TerrainPackGeometry capture() {
        var rules = TerrainPackRules.snapshot();
        var emissive = ResourcePackItemFeatures.atlasEmissive();
        return rules == null && emissive == null ? null : new TerrainPackGeometry(rules, emissive);
    }

    public BlockRenderLayer targetLayer(IBlockState state, BlockRenderLayer source, boolean shaderOverride) {
        return shaderOverride ? source : TerrainPackRules.targetLayer(rules, state, source);
    }

    boolean needsPaneGroups(IBlockState state) {
        return TerrainPackRules.hasLegacyPaneRules(rules, state);
    }

    PaneGroups preparePane(IBlockState state, IBlockAccess world, BlockPos pos, BlockRenderLayer source, long seed,
            Function<EnumFacing, List<BakedQuad>> load) {
        Map<EnumFacing, List<BakedQuad>> sides = new EnumMap<>(EnumFacing.class);
        for (EnumFacing side : EnumFacing.VALUES) sides.put(side, load.apply(side));
        List<BakedQuad> general = load.apply(null);
        PaneReplacement replacement = TerrainPackRules.prepareLegacyPane(rules, state, world, pos, source, seed,
                side -> side == null ? general : sides.get(side));
        return new PaneGroups(sides, general, replacement);
    }

    record PaneGroups(Map<EnumFacing, List<BakedQuad>> sides, List<BakedQuad> general,
                      @Nullable PaneReplacement replacement) {
        List<BakedQuad> quads(@Nullable EnumFacing side) {
            return side == null ? general : sides.get(side);
        }
    }

    /** Returns null when the input quads can be submitted directly to target. */
    @Nullable
    public List<OutputQuad> transform(IBlockState state, IBlockAccess world, BlockPos pos,
            BlockRenderLayer source, BlockRenderLayer target, boolean shaderOverride, @Nullable EnumFacing side,
            long seed, List<BakedQuad> quads, @Nullable PaneGroups pane) {
        var output = TerrainPackRules.transform(rules, state, world, pos, source, target, side, seed, quads,
                pane == null ? null : pane.replacement());
        if (shaderOverride && output != null) {
            output = output.stream().map(quad -> applyShaderLayer(quad, target)).toList();
        }
        if (emissive == null) {
            return output;
        }
        List<OutputQuad> mapped = null;
        for (int i = 0; i < (output == null ? quads.size() : output.size()); i++) {
            OutputQuad sourceQuad = output == null ? null : output.get(i);
            BakedQuad quad = sourceQuad == null ? quads.get(i) : sourceQuad.quad();
            BakedQuad copy = emissive.copy(quad);
            if (mapped == null && copy == null) {
                continue;
            }
            if (mapped == null) {
                mapped = new ArrayList<>();
                for (int j = 0; j < i; j++) {
                    mapped.add(output == null ? ordinary(quads.get(j), target) : output.get(j));
                }
            }
            if (sourceQuad == null) {
                sourceQuad = ordinary(quad, target);
            }
            mapped.add(sourceQuad);
            if (copy != null) {
                mapped.add(new OutputQuad(copy, sourceQuad.layer(), true, sourceQuad.tintState(),
                        sourceQuad.tintIndex(), new Light(15, 15)));
            }
        }
        return mapped == null ? output : mapped;
    }

    private static OutputQuad ordinary(BakedQuad quad, BlockRenderLayer layer) {
        return new OutputQuad(quad, layer, false, null, quad.getTintIndex(), null);
    }

    private static OutputQuad applyShaderLayer(OutputQuad quad, BlockRenderLayer target) {
        if (quad.appended() || quad.layer() == target || quad.layer().name().equals("BLOOM")) {
            return quad;
        }
        return new OutputQuad(quad.quad(), target, false, quad.tintState(), quad.tintIndex(), quad.light());
    }

    public static int applyMinimumLight(int packed, Light light) {
        return Math.max(packed & 0xffff, light.block() << 4)
                | (Math.max(packed >>> 16, light.sky() << 4) << 16);
    }
}
