package com.dhj.actinium.resourcepack.ctm.client.processor.simple;

import com.dhj.actinium.resourcepack.ctm.api.client.ProcessingDataProvider;
import com.dhj.actinium.resourcepack.ctm.client.processor.DirectionMaps;
import com.dhj.actinium.resourcepack.ctm.client.processor.ProcessingDataKeys;
import com.dhj.actinium.resourcepack.ctm.client.properties.OrientedConnectingCtmProperties;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/** Selects the secondary strip only when neither neighbor on the primary axis connects. */
public final class CombinedSpriteProvider extends HorizontalSpriteProvider {
    private final boolean horizontalFirst;

    CombinedSpriteProvider(TextureAtlasSprite[] sprites, OrientedConnectingCtmProperties properties, boolean horizontalFirst) {
        super(sprites, properties.getConnectionPredicate(), properties.getInnerSeams(), properties.getOrientationMode());
        this.horizontalFirst = horizontalFirst;
    }

    @Override
    public TextureAtlasSprite getSprite(BakedQuad quad, TextureAtlasSprite sprite, IBlockAccess level, BlockPos pos,
            IBlockState appearance, IBlockState state, long rand, ProcessingDataProvider data) {
        var directions = DirectionMaps.getDirections(orientationMode, quad, appearance);
        var scratch = data.getData(ProcessingDataKeys.MUTABLE_POS);
        int first = horizontalFirst ? 0 : 1;
        int primary = 0;
        for (int i = 0; i < 2; i++) {
            scratch.setPos(pos).move(directions[first + 2 * i]);
            if (connectionPredicate.shouldConnect(level, pos, appearance, state, scratch, quad.getFace(), sprite, innerSeams)) {
                primary |= 1 << i;
            }
        }
        if (primary != 0) {
            return sprites[SPRITE_INDEX_MAP[primary]];
        }
        int secondary = 0;
        for (int i = 0; i < 2; i++) {
            scratch.setPos(pos).move(directions[1 - first + 2 * i]);
            if (connectionPredicate.shouldConnect(level, pos, appearance, state, scratch, quad.getFace(), sprite, innerSeams)) {
                secondary |= 1 << i;
            }
        }
        return sprites[secondary == 0 ? 3 : SPRITE_INDEX_MAP[secondary] + 4];
    }

    public record Factory(boolean horizontalFirst) implements SpriteProvider.Factory<OrientedConnectingCtmProperties> {
        @Override
        public SpriteProvider createSpriteProvider(TextureAtlasSprite[] sprites, OrientedConnectingCtmProperties properties) {
            return new CombinedSpriteProvider(sprites, properties, horizontalFirst);
        }

        @Override
        public int getSpriteAmount(OrientedConnectingCtmProperties properties) {
            return 7;
        }
    }
}
