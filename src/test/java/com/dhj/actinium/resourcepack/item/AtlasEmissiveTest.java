package com.dhj.actinium.resourcepack.item;

import java.util.Map;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class AtlasEmissiveTest {
    @Test
    void remapsUvsAndAddsFullLightWithoutChangingSourceQuad() {
        TextureAtlasSprite base = sprite("base", 0);
        TextureAtlasSprite glow = sprite("glow", 32);
        VertexFormat item = DefaultVertexFormats.ITEM;
        int uv = item.getUvOffsetById(0) / Integer.BYTES;
        int[] data = new int[item.getIntegerSize() * 4];
        for (int i = 0; i < 4; i++) {
            int offset = i * item.getIntegerSize() + uv;
            data[offset] = Float.floatToRawIntBits(base.getInterpolatedU(4));
            data[offset + 1] = Float.floatToRawIntBits(base.getInterpolatedV(12));
        }
        BakedQuad source = new BakedQuad(data, 2, EnumFacing.NORTH, base, true, item);
        BakedQuad result = new AtlasEmissive(Map.of(base, glow)).copy(source);
        assertSame(glow, result.getSprite());
        assertEquals(source.getTintIndex(), result.getTintIndex());
        assertFalse(result.shouldApplyDiffuseLighting());
        assertFalse(source.getFormat().hasUvOffset(1));
        assertEquals(base.getInterpolatedU(4), Float.intBitsToFloat(source.getVertexData()[uv]));
        for (int i = 0; i < 4; i++) {
            int offset = i * result.getFormat().getIntegerSize();
            assertEquals(glow.getInterpolatedU(4), Float.intBitsToFloat(result.getVertexData()[offset + uv]), 1e-6);
            assertEquals(glow.getInterpolatedV(12), Float.intBitsToFloat(result.getVertexData()[offset + uv + 1]), 1e-6);
            assertEquals(0x00F000F0, result.getVertexData()[offset + result.getFormat().getUvOffsetById(1) / Integer.BYTES]);
        }
        assertNull(new AtlasEmissive(Map.of()).copy(source));
    }

    private static TextureAtlasSprite sprite(String name, int x) {
        TextureAtlasSprite sprite = new TextureAtlasSprite(name) {};
        sprite.setIconWidth(16);
        sprite.setIconHeight(16);
        sprite.initSprite(64, 64, x, 0, false);
        return sprite;
    }
}
