package com.dhj.actinium.resourcepack.ctm.client.processor.simple;

import java.util.Arrays;

import com.dhj.actinium.resourcepack.ctm.api.client.QuadProcessor;
import com.dhj.actinium.resourcepack.ctm.client.CtmRuleTypes;
import com.dhj.actinium.resourcepack.ctm.client.ctm.CtmSubmap;
import com.dhj.actinium.resourcepack.ctm.client.ctm.QuadClipper;
import com.dhj.actinium.resourcepack.ctm.client.processor.AbstractQuadProcessorFactory;
import com.dhj.actinium.resourcepack.ctm.client.processor.BaseProcessingPredicate;
import com.dhj.actinium.resourcepack.ctm.client.processor.DirectionMaps;
import com.dhj.actinium.resourcepack.ctm.client.processor.ProcessingDataKeys;
import com.dhj.actinium.resourcepack.ctm.client.properties.OrientedConnectingCtmProperties;
import com.dhj.actinium.resourcepack.ctm.client.util.RenderUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/** Applies compact CTM to model geometry using the input sprite's UV quadrants. */
public final class CompactQuadProcessor implements QuadProcessor {
    private final OrientedConnectingCtmProperties properties;
    private final TextureAtlasSprite[] tiles;
    private final BaseProcessingPredicate predicate;
    private final int[] replacements = new int[47];

    private CompactQuadProcessor(OrientedConnectingCtmProperties properties, TextureAtlasSprite[] tiles) {
        this.properties = properties;
        this.tiles = tiles;
        this.predicate = BaseProcessingPredicate.fromProperties(properties);
        Arrays.fill(replacements, -1);
        for (int i = 0; i < replacements.length; i++) {
            String value = properties.getProperties().getProperty("ctm." + i);
            if (value == null) continue;
            try {
                int tile = Integer.parseInt(value.trim());
                if (tile >= 0 && tile < tiles.length) {
                    replacements[i] = tile;
                    continue;
                }
            } catch (NumberFormatException ignored) {
                // Report malformed indexes with the same context as out-of-range indexes.
            }
            CtmRuleTypes.LOGGER.warn("Invalid compact CTM replacement ctm.{}={} in {} ({})", i, value,
                    properties.getResourceId(), properties.getPackId());
        }
    }

    @Override
    public ProcessingResult processQuad(BakedQuad quad, TextureAtlasSprite sprite, IBlockAccess world, BlockPos pos,
            IBlockState appearance, IBlockState state, long seed, int pass, ProcessingContext context) {
        if (quad.getFace() == null || !quad.getFormat().hasUvOffset(0)
                || !predicate.shouldProcessQuad(quad, sprite, world, pos, appearance, state, context)) {
            return ProcessingResult.NEXT_PROCESSOR;
        }
        var directions = DirectionMaps.getDirections(properties.getOrientationMode(), quad, appearance);
        int bits = CtmSpriteProvider.getConnections(directions, properties.getConnectionPredicate(),
                properties.getInnerSeams(), context.getData(ProcessingDataKeys.MUTABLE_POS), world, pos,
                appearance, state, quad.getFace(), sprite);
        int replacement = replacements[CtmSpriteProvider.SPRITE_INDEX_MAP[bits]];
        if (replacement >= 0) {
            return SimpleQuadProcessor.process(quad, sprite, tiles[replacement], context);
        }
        if (bits == 0 || bits == 255) {
            return SimpleQuadProcessor.process(quad, sprite, tiles[bits == 0 ? 0 : 1], context);
        }
        BakedQuad[] parts = new BakedQuad[4];
        for (int i = 0; i < parts.length; i++) {
            parts[i] = QuadClipper.clipSpriteQuadrant(quad, sprite, i);
            var tile = tiles[tileIndex(bits, i)];
            if (parts[i] == null || tile != null && RenderUtil.isMissingSprite(tile)) {
                return ProcessingResult.NEXT_PROCESSOR;
            }
        }
        for (int i = 0; i < parts.length; i++) {
            var tile = tiles[tileIndex(bits, i)];
            context.getExtraQuads().add(tile == null ? parts[i] : QuadClipper.transformUVs(parts[i], sprite, tile, CtmSubmap.X1));
        }
        return ProcessingResult.STOP;
    }

    private static int tileIndex(int bits, int quadrant) {
        int horizontal = (quadrant & 1) == 0 ? 0 : 4;
        int vertical = quadrant < 2 ? 6 : 2;
        boolean h = (bits & 1 << horizontal) != 0;
        boolean v = (bits & 1 << vertical) != 0;
        if (h && v) {
            int corner = (horizontal == 0 && vertical == 6 ? 7 : (horizontal + vertical) / 2);
            return (bits & 1 << corner) != 0 ? 1 : 4;
        }
        return h ? 3 : v ? 2 : 0;
    }

    public static final class Factory extends AbstractQuadProcessorFactory<OrientedConnectingCtmProperties> {
        @Override
        public QuadProcessor createProcessor(OrientedConnectingCtmProperties properties, TextureAtlasSprite[] sprites) {
            return new CompactQuadProcessor(properties, sprites);
        }

        @Override
        public int getSpriteAmount(OrientedConnectingCtmProperties properties) {
            return properties.getSpriteIds().size();
        }
    }
}
