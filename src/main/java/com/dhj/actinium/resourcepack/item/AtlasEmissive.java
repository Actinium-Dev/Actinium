package com.dhj.actinium.resourcepack.item;

import java.util.Map;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.BlockRenderLayer;

/** Atlas-local emissive pairs, safe to retain for the duration of a chunk build. */
public final class AtlasEmissive {
    private final Map<TextureAtlasSprite, TextureAtlasSprite> pairs;

    AtlasEmissive(Map<TextureAtlasSprite, TextureAtlasSprite> pairs) {
        this.pairs = Map.copyOf(pairs);
    }

    /** Finds the emissive counterpart of a sprite in this atlas generation. */
    public TextureAtlasSprite sprite(TextureAtlasSprite base) {
        return base == null ? null : pairs.get(base);
    }

    boolean isEmpty() {
        return pairs.isEmpty();
    }

    /** Builds the emissive copy after CTM or CIT has selected the final sprite. */
    public BakedQuad copy(BakedQuad quad) {
        TextureAtlasSprite sprite = sprite(quad.getSprite());
        return sprite == null ? null : fullBrightCopy(quad, sprite);
    }

    /** Copies an explicit emissive sprite, including CTM-declared emissive outputs. */
    public static BakedQuad fullBrightCopy(BakedQuad quad, TextureAtlasSprite sprite) {
        return copyQuad(quad, sprite, true);
    }

    /** Resolves the extra draw layer; null means that Bloom has no configured fallback. */
    public static BlockRenderLayer targetLayer(BlockRenderLayer source, boolean bloom, boolean fallback) {
        if (!bloom) return source;
        for (BlockRenderLayer layer : BlockRenderLayer.values()) {
            if (layer.name().equals("BLOOM")) return layer;
        }
        return fallback ? source : null;
    }

    static BakedQuad copyQuad(BakedQuad quad, TextureAtlasSprite sprite, boolean fullBright) {
        VertexFormat source = quad.getFormat();
        VertexFormat target = source;
        if (fullBright && !source.hasUvOffset(1)) {
            target = new VertexFormat(source);
            target.addElement(DefaultVertexFormats.TEX_2S);
        }
        int sourceStride = source.getIntegerSize();
        int stride = target.getIntegerSize();
        int uv = source.getUvOffsetById(0) / Integer.BYTES;
        int light = fullBright ? target.getUvOffsetById(1) / Integer.BYTES : -1;
        int[] original = quad.getVertexData();
        int count = original.length / sourceStride;
        int[] vertices = new int[count * stride];
        TextureAtlasSprite base = quad.getSprite();
        for (int vertex = 0; vertex < count; vertex++) {
            int offset = vertex * stride;
            System.arraycopy(original, vertex * sourceStride, vertices, offset, sourceStride);
            if (sprite != base) {
                float u = Float.intBitsToFloat(vertices[offset + uv]);
                float v = Float.intBitsToFloat(vertices[offset + uv + 1]);
                vertices[offset + uv] = Float.floatToRawIntBits(sprite.getInterpolatedU(base.getUnInterpolatedU(u)));
                vertices[offset + uv + 1] = Float.floatToRawIntBits(sprite.getInterpolatedV(base.getUnInterpolatedV(v)));
            }
            if (fullBright) vertices[offset + light] = 0x00F000F0;
        }
        return new BakedQuad(vertices, quad.getTintIndex(), quad.getFace(), sprite,
            !fullBright && quad.shouldApplyDiffuseLighting(), target);
    }
}
