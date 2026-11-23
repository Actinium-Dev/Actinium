// Derived from Continuity-For-Actinium (4281538), PepperCode1 and contributors.
// SPDX-License-Identifier: LGPL-3.0-only
package com.dhj.actinium.resourcepack.ctm.client.model;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;

/** Marks a generated overlay quad so the transformer can route it to its configured render layer. */
public final class OverlayBakedQuad extends BakedQuad {
	private final BlockRenderLayer renderLayer;
	private final IBlockState tintState;
	public OverlayBakedQuad(int[] vertexData, int tintIndex, EnumFacing face, TextureAtlasSprite sprite,
			boolean applyDiffuseLighting, VertexFormat format, BlockRenderLayer renderLayer,
			@Nullable IBlockState tintState) {
		super(vertexData, tintIndex, face, sprite, applyDiffuseLighting, format);
		this.renderLayer = renderLayer;
		this.tintState = tintState;
	}

	public BlockRenderLayer getRenderLayer() {
		return renderLayer;
	}

	@Nullable
	public IBlockState tintState() {
		return tintState;
	}
}
