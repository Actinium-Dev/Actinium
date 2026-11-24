// Derived from Continuity-For-Actinium (4281538), PepperCode1 and contributors.
// SPDX-License-Identifier: LGPL-3.0-only
package com.dhj.actinium.resourcepack.ctm.client.util;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;

public final class RenderUtil {
	private RenderUtil() {
	}

	public static boolean isMissingSprite(TextureAtlasSprite sprite) {
		return sprite.getIconName().equals(TextureMap.LOCATION_MISSING_TEXTURE.toString())
				|| sprite.getIconName().equals(TextureMap.LOCATION_MISSING_TEXTURE.getPath());
	}
}
