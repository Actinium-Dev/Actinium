package com.dhj.actinium.resourcepack.ctm;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.dhj.actinium.resourcepack.ctm.client.util.QuadUtil;
import net.minecraft.block.BlockStainedGlassPane;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyGlassPaneTest {
    private static final TextureAtlasSprite GLASS = new Sprite("minecraft:blocks/glass");
    private static final TextureAtlasSprite RIM = new Sprite("minecraft:blocks/glass_pane_top");

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void stainedCornerUsesDifferentTileSegmentsOnTheInsideAndOutside() {
        var model = new LegacyGlassPane.Model(new LegacyGlassPane.Connections(true, false, true, false),
                true, DefaultVertexFormats.BLOCK, GLASS, RIM);
        var walls = new ArrayList<BakedQuad>();
        model.addWall(walls, EnumFacing.NORTH);
        model.addWall(walls, EnumFacing.SOUTH);
        var inside = walls.getFirst();
        var outside = walls.getLast();
        assertEquals(7f / 16, rightmostX(inside));
        assertEquals(9f / 16, rightmostX(outside));
        assertEquals(0, QuadUtil.getU(inside, 0));
        assertEquals(7f / 16, QuadUtil.getU(outside, 0));
        assertEquals(0, QuadUtil.getV(inside, 0));
        assertEquals(1, QuadUtil.getV(inside, 1));
        assertEquals(0, inside.getTintIndex());
        assertFalse(inside.shouldApplyDiffuseLighting());
    }

    @Test
    void loneStainedPaneHasFourTipFacesButClearPaneHasOnlyTheCross() {
        var isolated = new LegacyGlassPane.Connections(false, false, false, false);
        var thick = new LegacyGlassPane.Model(isolated, true, DefaultVertexFormats.BLOCK, GLASS, RIM);
        var thin = new LegacyGlassPane.Model(isolated, false, DefaultVertexFormats.BLOCK, GLASS, RIM);
        var walls = new ArrayList<BakedQuad>();
        thick.addWall(walls, EnumFacing.NORTH);
        assertEquals(2, walls.size());
        var tip = walls.getLast();
        assertEquals(0, QuadUtil.positionComponent(tip, 0, 2));
        assertEquals(7f / 16, QuadUtil.getU(tip, 0));
        assertEquals(9f / 16, QuadUtil.getU(tip, 2));
        assertSame(GLASS, tip.getSprite());
        walls.clear();
        thin.addWall(walls, EnumFacing.NORTH);
        assertEquals(1, walls.size());
        assertEquals(.5f, QuadUtil.positionComponent(walls.getFirst(), 0, 2));
    }

    @Test
    void recognizesTheVanillaPostWithGeneralOrCulledCaps() {
        var state = Blocks.GLASS_PANE.getDefaultState();
        var sides = List.of(wall(EnumFacing.NORTH, 7, 7, 9), wall(EnumFacing.SOUTH, 9, 7, 9),
                wall(EnumFacing.WEST, 7, 7, 9), wall(EnumFacing.EAST, 9, 7, 9));
        var top = cap(EnumFacing.UP, 16);
        var bottom = cap(EnumFacing.DOWN, 0);
        var general = new ArrayList<>(sides);
        general.add(top);
        general.add(bottom);
        assertNotNull(LegacyGlassPane.identify(state, Set.of(), general, face -> List.of()));
        assertNotNull(LegacyGlassPane.identify(state, Set.of(), sides,
                face -> face == EnumFacing.UP ? List.of(top) : face == EnumFacing.DOWN ? List.of(bottom) : List.of()));
    }

    @Test
    void leavesOverriddenModelsAndAlteredRuntimeMeshAlone() {
        var state = Blocks.GLASS_PANE.getDefaultState();
        var quads = new ArrayList<>(List.of(wall(EnumFacing.NORTH, 7, 7, 9), wall(EnumFacing.SOUTH, 9, 7, 9),
                wall(EnumFacing.WEST, 7, 7, 9), wall(EnumFacing.EAST, 9, 7, 9), cap(EnumFacing.UP, 16),
                cap(EnumFacing.DOWN, 0)));
        assertNull(LegacyGlassPane.identify(state, Set.of("glass_pane"), quads, face -> List.of()));
        quads.set(0, wall(EnumFacing.NORTH, 7, 6, 10));
        assertNull(LegacyGlassPane.identify(state, Set.of(), quads, face -> List.of()));
    }

    @Test
    void crossCapCoversTheCenterOnceAndReachesAllArms() {
        var model = new LegacyGlassPane.Model(new LegacyGlassPane.Connections(true, true, true, true),
                true, DefaultVertexFormats.BLOCK, GLASS, RIM);
        var quads = new ArrayList<BakedQuad>();
        model.addThickCap(quads, EnumFacing.UP);

        assertEquals(1, covering(quads, .5f, .5f));
        assertEquals(1, covering(quads, .25f, .5f));
        assertEquals(1, covering(quads, .75f, .5f));
        assertEquals(1, covering(quads, .5f, .25f));
        assertEquals(1, covering(quads, .5f, .75f));
        assertEquals(0, covering(quads, .25f, .25f));
    }

    @Test
    void differentDyesKeepTheirCommonSeam() {
        var pane = Blocks.STAINED_GLASS_PANE.getDefaultState();
        var red = pane.withProperty(BlockStainedGlassPane.COLOR, EnumDyeColor.RED);
        var blue = pane.withProperty(BlockStainedGlassPane.COLOR, EnumDyeColor.BLUE);
        assertTrue(LegacyGlassPane.separatesDyes(red, blue));
        assertFalse(LegacyGlassPane.separatesDyes(red, red));
        assertFalse(LegacyGlassPane.separatesDyes(red, Blocks.GLASS_PANE.getDefaultState()));
    }

    private static long covering(List<BakedQuad> quads, float x, float z) {
        return quads.stream().filter(quad -> spans(quad, x, 0) && spans(quad, z, 2)).count();
    }

    private static boolean spans(BakedQuad quad, float coordinate, int axis) {
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (int vertex = 0; vertex < 4; vertex++) {
            float value = QuadUtil.positionComponent(quad, vertex, axis);
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return coordinate > min && coordinate < max;
    }

    private static BakedQuad wall(EnumFacing side, int plane, int lo, int hi) {
        if (side.getAxis() == EnumFacing.Axis.X) {
            return quad(side, GLASS, plane, 0, lo, plane, 0, hi, plane, 16, hi, plane, 16, lo);
        }
        return quad(side, GLASS, lo, 0, plane, hi, 0, plane, hi, 16, plane, lo, 16, plane);
    }

    private static BakedQuad cap(EnumFacing side, int y) {
        return quad(side, RIM, 7, y, 7, 9, y, 7, 9, y, 9, 7, y, 9);
    }

    private static BakedQuad quad(EnumFacing side, TextureAtlasSprite sprite, int... positions) {
        var format = DefaultVertexFormats.BLOCK;
        int stride = format.getIntegerSize();
        int[] data = new int[4 * stride];
        for (int i = 0; i < 4; i++) {
            for (int a = 0; a < 3; a++) data[i * stride + a] = Float.floatToRawIntBits(positions[i * 3 + a] / 16f);
        }
        return new BakedQuad(data, -1, side, sprite, true, format);
    }

    private static float rightmostX(BakedQuad quad) {
        float result = 0;
        for (int i = 0; i < 4; i++) result = Math.max(result, QuadUtil.positionComponent(quad, i, 0));
        return result;
    }

    private static final class Sprite extends TextureAtlasSprite {
        private Sprite(String name) {
            super(name);
        }

        @Override
        public float getInterpolatedU(double u) {
            return (float) u / 16;
        }

        @Override
        public float getInterpolatedV(double v) {
            return (float) v / 16;
        }
    }
}
