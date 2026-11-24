// Derived from Continuity-For-Actinium (4281538), PepperCode1 and contributors.
// SPDX-License-Identifier: LGPL-3.0-only
package com.dhj.actinium.resourcepack.ctm.client.properties;

import java.util.Locale;
import java.util.List;
import java.util.Properties;

import com.dhj.actinium.compat.MissingModelCompat;
import com.dhj.actinium.resourcepack.ctm.client.CtmRuleTypes;
import com.dhj.actinium.resourcepack.ctm.client.processor.ConnectionPredicate;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;

public class BasicConnectingCtmProperties extends BaseCtmProperties {
	protected ConnectionPredicate connectionPredicate;

	public BasicConnectingCtmProperties(Properties properties, ResourceLocation resourceId, String pack, int packPriority, IResourceManager resourceManager, String method) {
		super(properties, resourceId, pack, packPriority, resourceManager, method);
	}

	@Override
	public void init() {
		super.init();
		parseConnect();
		detectConnect();
		validateConnect();
	}

	protected void parseConnect() {
		String connectStr = properties.getProperty("connect");
		if (connectStr == null) {
			return;
		}

		try {
			String type = connectStr.trim().toUpperCase(Locale.ROOT);
			connectionPredicate = ConnectionType.valueOf(type);
		} catch (IllegalArgumentException e) {
			CtmRuleTypes.LOGGER.warn("Unknown 'connect' value '" + connectStr + "' in file '" + resourceId + "' in pack '" + packId + "'");
		}
	}

	protected void detectConnect() {
		if (connectionPredicate == null) {
			if (matchBlocksPredicate != null) {
				connectionPredicate = ConnectionType.BLOCK;
			} else if (matchTilesSet != null) {
				connectionPredicate = ConnectionType.TILE;
			}
		}
	}

	protected void validateConnect() {
		if (connectionPredicate == null) {
			CtmRuleTypes.LOGGER.error("No valid connection type provided in file '" + resourceId + "' in pack '" + packId + "'");
			valid = false;
		}
	}

	public ConnectionPredicate getConnectionPredicate() {
		return connectionPredicate;
	}

	public enum ConnectionType implements ConnectionPredicate {
		SIDES {
			@Override
			public boolean shouldConnect(IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, BlockPos otherPos, IBlockState otherAppearanceState, IBlockState otherState, EnumFacing face, TextureAtlasSprite quadSprite) {
				var block = state.getBlock();
				return block == otherState.getBlock() && block.getMetaFromState(state) == block.getMetaFromState(otherState);
			}
		},
		BLOCK {
			@Override
			public boolean shouldConnect(IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, BlockPos otherPos, IBlockState otherAppearanceState, IBlockState otherState, EnumFacing face, TextureAtlasSprite quadSprite) {
				return appearanceState.getBlock() == otherAppearanceState.getBlock();
			}
		},
		TILE {
			@Override
			public boolean shouldConnect(IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, BlockPos otherPos, IBlockState otherAppearanceState, IBlockState otherState, EnumFacing face, TextureAtlasSprite quadSprite) {
				return stateUsesSprite(level, otherPos, otherAppearanceState, face, quadSprite);
			}
		},
		STATE {
			@Override
			public boolean shouldConnect(IBlockAccess level, BlockPos pos, IBlockState appearanceState, IBlockState state, BlockPos otherPos, IBlockState otherAppearanceState, IBlockState otherState, EnumFacing face, TextureAtlasSprite quadSprite) {
				return appearanceState == otherAppearanceState;
			}
		};

		private static boolean stateUsesSprite(IBlockAccess level, BlockPos pos, IBlockState state, EnumFacing face, TextureAtlasSprite sprite) {
			state = state.getActualState(level, pos);
			IBakedModel model = Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelShapes().getModelForState(state);
			if (model == null || MissingModelCompat.isMissingModel(model)) {
				return false;
			}
			state = state.getBlock().getExtendedState(state, level, pos);
			long seed = MathHelper.getPositionRandom(pos);
			if (face != null && containsSprite(model.getQuads(state, face, seed), sprite)) {
				return true;
			}
			return containsSprite(model.getQuads(state, null, seed), sprite);
		}

		private static boolean containsSprite(List<BakedQuad> quads, TextureAtlasSprite sprite) {
			for (BakedQuad quad : quads) {
				if (quad.getSprite() == sprite) {
					return true;
				}
			}
			return false;
		}
	}
}
