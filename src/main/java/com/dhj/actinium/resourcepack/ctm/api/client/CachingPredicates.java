// Derived from Continuity-For-Actinium (4281538), PepperCode1 and contributors.
// SPDX-License-Identifier: LGPL-3.0-only
package com.dhj.actinium.resourcepack.ctm.api.client;

import java.util.function.Function;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ResourceLocation;

public interface CachingPredicates {
	boolean affectsSprites();

	boolean affectsSprite(TextureAtlasSprite sprite);

	boolean affectsBlockStates();

	boolean affectsBlockState(IBlockState state);

	boolean isValidForMultipass();

	interface Factory<T extends CtmProperties> {
		CachingPredicates createPredicates(T properties, Function<ResourceLocation, TextureAtlasSprite> spriteGetter);
	}
}
