package com.dhj.actinium.resourcepack.ctm;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.dhj.actinium.resourcepack.ctm.api.client.QuadProcessor.ProcessingResult;
import com.dhj.actinium.resourcepack.ctm.client.ctm.CtmMcmetaParser;
import com.dhj.actinium.resourcepack.ctm.client.model.OverlayBakedQuad;
import com.dhj.actinium.resourcepack.ctm.client.processor.BaseCachingPredicates;
import com.google.gson.JsonParser;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainPackRulesTest {
    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void overlayKeepsBaseAndCarriesItsOwnLayerAndTint() {
        var sprite = new Sprite("minecraft:blocks/stone");
        var input = new BakedQuad(new int[28], -1, EnumFacing.UP, sprite, true, DefaultVertexFormats.BLOCK);
        var tint = Blocks.GRASS.getDefaultState();
        var overlay = new OverlayBakedQuad(new int[28], 1, EnumFacing.UP, sprite, true,
                DefaultVertexFormats.BLOCK, BlockRenderLayer.TRANSLUCENT, tint);
        var rule = new TerrainPackRules.Rule((quad, texture, world, pos, appearance, state, seed, pass, ctx) -> {
            ctx.getExtraQuads().add(overlay);
            return ProcessingResult.NEXT_PROCESSOR;
        }, new BaseCachingPredicates(null, null, false), null, null, false);
        var result = TerrainPackRules.transformQuads(new TerrainPackRules.Snapshot(List.of(rule), List.of()),
                Blocks.STONE.getDefaultState(), null, BlockPos.ORIGIN, BlockRenderLayer.SOLID, 0, List.of(input));

        assertEquals(2, result.size());
        assertSame(input, result.getFirst().quad());
        assertFalse(result.getFirst().appended());
        assertSame(BlockRenderLayer.SOLID, result.getFirst().layer());
        assertSame(overlay, result.getLast().quad());
        assertTrue(result.getLast().appended());
        assertSame(BlockRenderLayer.TRANSLUCENT, result.getLast().layer());
        assertSame(tint, result.getLast().tintState());
        assertEquals(overlay.getTintIndex(), result.getLast().tintIndex());
    }

    @Test
    void unmatchedRuleLeavesOriginalQuadsAvailableToCaller() {
        var input = new BakedQuad(new int[28], -1, EnumFacing.UP, new Sprite("minecraft:blocks/stone"),
                true, DefaultVertexFormats.BLOCK);
        var state = Blocks.STONE.getDefaultState();
        var rule = new TerrainPackRules.Rule((quad, texture, world, pos, appearance, block, seed, pass, ctx) -> {
            throw new AssertionError("Unmatched processor ran");
        }, new BaseCachingPredicates(null, block -> block == Blocks.DIRT.getDefaultState(), false), null, null, false);
        assertNull(TerrainPackRules.transformQuads(new TerrainPackRules.Snapshot(List.of(rule), List.of()), state,
                null, BlockPos.ORIGIN, BlockRenderLayer.SOLID, 0, List.of(input)));
    }

    @Test
    void bloomMetadataSurvivesWithoutAnInstalledBloomLayer() {
        var metadata = JsonParser.parseString("""
                {"ctm_version":1,"layer":"BLOOM","extra":{"emissive_fallback":true}}
                """).getAsJsonObject();
        var definition = CtmMcmetaParser.parse(new ResourceLocation("test", "blocks/glow"), metadata,
                "Test pack", 0, Map.of());
        assertTrue(definition.requestsBloom());
        assertTrue(definition.hasEmissiveFallback());
        assertEquals(List.of(BlockRenderLayer.values()).stream().filter(layer -> layer.name().equals("BLOOM"))
                .findFirst().orElse(null), definition.getLayer());
    }

    @Test
    void betterGrassReadsG5FlagsAndTextureNames() {
        Properties properties = new Properties();
        properties.setProperty("grass", "false");
        properties.setProperty("grass_path", "true");
        properties.setProperty("grass.multilayer", "true");
        properties.setProperty("texture.grass_path_side", "custom/path_side");

        var settings = BetterGrass.Settings.parse(properties);
        assertFalse(settings.enabled().get("grass"));
        assertTrue(settings.enabled().get("grass_path"));
        assertTrue(settings.multilayer());
        assertEquals(new ResourceLocation("minecraft", "custom/path_side"), settings.textures().get("grass_path_side"));
    }

    private static final class Sprite extends TextureAtlasSprite {
        Sprite(String name) {
            super(name);
        }
    }
}
