package com.dhj.actinium.render.terrain.compile.pipeline;

import com.dhj.actinium.render.terrain.compile.ChunkBufferBuilder;
import com.dhj.actinium.render.terrain.compile.pipeline.TerrainPackGeometry.PaneGroups;
import com.dhj.actinium.resourcepack.color.ResourcePackColors;
import com.dhj.actinium.resourcepack.ctm.TerrainPackRules.Light;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.pipeline.LightUtil;
import net.minecraftforge.common.ForgeModContainer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Keeps the dispatcher's choice of model and lighter while giving each output layer its own faces. */
public final class VanillaPackModel extends BakedModelWrapper<IBakedModel> {
    private final Map<EnumFacing, List<BakedQuad>> faces = new EnumMap<>(EnumFacing.class);
    private List<BakedQuad> general = List.of();

    private VanillaPackModel(IBakedModel original) {
        super(original);
    }

    @FunctionalInterface
    public interface Draw {
        /** Draws the layer-specific model through the original Forge renderer. */
        boolean render(IBakedModel model, ChunkBufferBuilder buffer);
    }

    public static boolean render(@Nullable TerrainPackGeometry rules, boolean tintUntinted, IBakedModel model, IBlockState state, IBlockAccess world,
            BlockPos pos, ChunkBufferBuilder original, Draw draw) {
        BlockRenderLayer input = original.getLayer();
        BlockRenderLayer source = MinecraftForgeClient.getRenderLayer();
        if (source == null) {
            source = input;
        }
        boolean shaderOverride = !original.isAllowRenderPassOptimization();
        BlockRenderLayer target = rules == null ? input : rules.targetLayer(state, input, shaderOverride);
        long seed = MathHelper.getPositionRandom(pos);
        var pane = rules == null || !rules.needsPaneGroups(state) ? null
                : rules.preparePane(state, world, pos, source, seed, side -> model.getQuads(state, side, seed));
        Map<BlockRenderLayer, VanillaPackModel> layers = new EnumMap<>(BlockRenderLayer.class);
        for (EnumFacing side : EnumFacing.VALUES) {
            collect(rules, model, state, world, pos, source, target, shaderOverride, tintUntinted, seed, layers, side, pane);
        }
        collect(rules, model, state, world, pos, source, target, shaderOverride, tintUntinted, seed, layers, null, pane);

        boolean drew = false;
        var context = original.getContext();
        for (var entry : layers.entrySet()) {
            BlockRenderLayer layer = entry.getKey();
            if (layer == input) {
                drew |= draw.render(entry.getValue(), original);
                continue;
            }
            var buffer = (ChunkBufferBuilder) context.getBufferForLayer(layer);
            boolean previous = buffer.isAllowRenderPassOptimization();
            buffer.setAllowRenderPassOptimization(false);
            try {
                context.beginVanillaBlockRender(buffer, pos, state, layer);
                try {
                    drew |= draw.render(entry.getValue(), buffer);
                } finally {
                    context.endVanillaRender(buffer);
                }
            } finally {
                buffer.setAllowRenderPassOptimization(previous);
            }
        }
        return drew;
    }

    private static void collect(@Nullable TerrainPackGeometry rules, IBakedModel model, IBlockState state, IBlockAccess world,
            BlockPos pos, BlockRenderLayer source, BlockRenderLayer target, boolean shaderOverride, boolean tintUntinted, long seed,
            Map<BlockRenderLayer, VanillaPackModel> layers, @Nullable EnumFacing side, @Nullable PaneGroups pane) {
        List<BakedQuad> quads = pane == null ? model.getQuads(state, side, seed) : pane.quads(side);
        if (quads.isEmpty()) {
            return;
        }
        var output = rules == null ? null : rules.transform(state, world, pos, source, target, shaderOverride, side, seed, quads, pane);
        if (output == null) {
            var face = layers.computeIfAbsent(target, ignored -> new VanillaPackModel(model)).face(side);
            if (!tintUntinted) {
                face.addAll(quads);
            } else {
                for (BakedQuad quad : quads) {
                    face.add(quad.hasTintIndex() ? quad : applyMetadata(quad, null, -1, null, true, world, pos));
                }
            }
        } else {
            for (var quad : output) {
                layers.computeIfAbsent(quad.layer(), ignored -> new VanillaPackModel(model)).face(side)
                        .add(applyMetadata(quad.quad(), quad.tintState(), quad.tintIndex(), quad.light(), tintUntinted, world, pos));
            }
        }
    }

    private static BakedQuad applyMetadata(BakedQuad quad, @Nullable IBlockState tintState, int tintIndex,
            @Nullable Light minimum, boolean tintUntinted, IBlockAccess world, BlockPos pos) {
        // A synthetic tint index makes the stock lighter query BlockColors for an explicit pack rule.
        int tint = tintState == null && tintUntinted && tintIndex < 0 ? 0 : tintIndex;
        if (tintState == null && tint == quad.getTintIndex() && minimum == null && ForgeModContainer.forgeLightPipelineEnabled) {
            return quad;
        }
        VertexFormat format = quad.getFormat();
        int[] vertices = quad.getVertexData().clone();
        int stride = format.getIntegerSize();
        int rgb = tintState == null ? -1 : ResourcePackColors.block(tintState, world, pos, tintIndex);
        if (tintState != null && tintIndex >= 0 && rgb < 0) {
            rgb = Minecraft.getMinecraft().getBlockColors().colorMultiplier(tintState, world, pos, tintIndex);
        }
        if (tintState != null && (tintIndex >= 0 || rgb >= 0) && format.hasColor()) {
            int offset = format.getColorOffset() / Integer.BYTES;
            for (int v = 0; v < 4; v++) {
                int index = v * stride + offset;
                int color = vertices[index];
                vertices[index] = (color & 0xff000000) | ((color & 255) * ((rgb >>> 16) & 255) / 255)
                        | ((((color >>> 8) & 255) * ((rgb >>> 8) & 255) / 255) << 8)
                        | ((((color >>> 16) & 255) * (rgb & 255) / 255) << 16);
            }
            tint = -1;
        }
        if (minimum != null) {
            if (!format.hasUvOffset(1)) {
                format = new VertexFormat(format).addElement(DefaultVertexFormats.TEX_2S);
                int[] expanded = new int[4 * format.getIntegerSize()];
                for (int v = 0; v < 4; v++) {
                    System.arraycopy(vertices, v * stride, expanded, v * format.getIntegerSize(), stride);
                }
                vertices = expanded;
                stride = format.getIntegerSize();
            }
            int offset = format.getUvOffsetById(1) / Integer.BYTES;
            for (int v = 0; v < 4; v++) {
                int index = v * stride + offset;
                vertices[index] = TerrainPackGeometry.applyMinimumLight(vertices[index], minimum);
            }
        }
        boolean shade = quad.shouldApplyDiffuseLighting() && (minimum == null || minimum.block() != 15 || minimum.sky() != 15);
        if (!ForgeModContainer.forgeLightPipelineEnabled && !format.equals(DefaultVertexFormats.BLOCK)) {
            int[] converted = new int[4 * DefaultVertexFormats.BLOCK.getIntegerSize()];
            int[] mapping = LightUtil.mapFormats(DefaultVertexFormats.BLOCK, format);
            float[] values = new float[4];
            for (int v = 0; v < 4; v++) {
                for (int e = 0; e < mapping.length; e++) {
                    if (mapping[e] < format.getElementCount()) {
                        LightUtil.unpack(vertices, values, format, v, mapping[e]);
                        LightUtil.pack(values, converted, DefaultVertexFormats.BLOCK, v, e);
                    }
                }
            }
            vertices = converted;
            format = DefaultVertexFormats.BLOCK;
        }
        return minimum == null ? new BakedQuad(vertices, tint, quad.getFace(), quad.getSprite(), shade, format)
                : new PackLightQuad(quad, vertices, tint, shade, format, minimum);
    }

    /** Vanilla writes light after copying a quad's vertices; preserve explicit pack minima at that write. */
    public static void writeBrightness(BakedQuad quad, int a, int b, int c, int d, BrightnessWriter original) {
        if (quad instanceof PackLightQuad lit) {
            Light min = lit.minimum;
            original.write(TerrainPackGeometry.applyMinimumLight(a, min), TerrainPackGeometry.applyMinimumLight(b, min),
                    TerrainPackGeometry.applyMinimumLight(c, min), TerrainPackGeometry.applyMinimumLight(d, min));
        } else {
            original.write(a, b, c, d);
        }
    }

    @FunctionalInterface
    public interface BrightnessWriter {
        /** Writes the four packed light coordinates for the just-submitted quad. */
        void write(int a, int b, int c, int d);
    }

    private static final class PackLightQuad extends BakedQuad {
        private final Light minimum;

        private PackLightQuad(BakedQuad original, int[] data, int tint, boolean shade, VertexFormat format, Light minimum) {
            super(data, tint, original.getFace(), original.getSprite(), shade, format);
            this.minimum = minimum;
        }
    }

    private List<BakedQuad> face(EnumFacing side) {
        if (side != null) {
            return faces.computeIfAbsent(side, ignored -> new ArrayList<>());
        }
        if (general.isEmpty()) {
            general = new ArrayList<>();
        }
        return general;
    }

    @Override
    public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long seed) {
        return side == null ? general : faces.getOrDefault(side, List.of());
    }
}
