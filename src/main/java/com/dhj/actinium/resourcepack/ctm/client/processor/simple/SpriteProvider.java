// Derived from Continuity-For-Actinium (4281538), PepperCode1 and contributors.
// SPDX-License-Identifier: LGPL-3.0-only
package com.dhj.actinium.resourcepack.ctm.client.processor.simple;

import javax.annotation.Nullable;

import com.dhj.actinium.resourcepack.ctm.api.client.ProcessingDataProvider;
import com.dhj.actinium.resourcepack.ctm.client.properties.BaseCtmProperties;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

public interface SpriteProvider {
	@Nullable
	TextureAtlasSprite getSprite(BakedQuad quad, TextureAtlasSprite sprite, IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, long rand, ProcessingDataProvider dataProvider);

	interface Factory<T extends BaseCtmProperties> {
		SpriteProvider createSpriteProvider(TextureAtlasSprite[] sprites, T properties);

		int getSpriteAmount(T properties);
	}
}
