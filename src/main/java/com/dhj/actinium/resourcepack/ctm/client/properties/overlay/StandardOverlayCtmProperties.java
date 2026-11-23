// Derived from Continuity-For-Actinium (4281538), PepperCode1 and contributors.
// SPDX-License-Identifier: LGPL-3.0-only
package com.dhj.actinium.resourcepack.ctm.client.properties.overlay;

import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.dhj.actinium.resourcepack.ctm.client.CtmRuleTypes;
import com.dhj.actinium.resourcepack.ctm.client.properties.BasicConnectingCtmProperties;
import com.dhj.actinium.resourcepack.ctm.client.properties.PropertiesParsingHelper;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;

/** Properties for OptiFine's 17-tile block transition overlay method. */
public class StandardOverlayCtmProperties extends BasicConnectingCtmProperties {
	@Nullable
	protected Set<ResourceLocation> connectTilesSet;
	@Nullable
	protected Predicate<IBlockState> connectBlocksPredicate;
	protected int tintIndex = -1;
	@Nullable
	protected IBlockState tintBlock;
	protected BlockRenderLayer layer = BlockRenderLayer.CUTOUT;

	public StandardOverlayCtmProperties(Properties properties, ResourceLocation resourceId, String pack, int packPriority, IResourceManager resourceManager, String method) {
		super(properties, resourceId, pack, packPriority, resourceManager, method);
	}

	@Override
	public void init() {
		super.init();
		connectTilesSet = PropertiesParsingHelper.parseMatchTiles(properties, "connectTiles", resourceId, packId);
		connectBlocksPredicate = PropertiesParsingHelper.parseBlockStates(properties, "connectBlocks", resourceId, packId);
		parseOverlayOptions();
	}

	public void parseOverlayOptions() {
		parseTintIndex();
		parseTintBlock();
		parseLayer();
	}

	private void parseTintIndex() {
		String value = properties.getProperty("tintIndex");
		if (value == null) {
			return;
		}
		try {
			int parsed = Integer.parseInt(value.trim());
			if (parsed >= -1) {
				tintIndex = parsed;
				return;
			}
		} catch (NumberFormatException ignored) {
			// Reported below.
		}
		CtmRuleTypes.LOGGER.warn("Invalid 'tintIndex' value '{}' in file '{}' in pack '{}'", value, resourceId, packId);
	}

	private void parseTintBlock() {
		String value = properties.getProperty("tintBlock");
		if (value == null || value.isBlank()) {
			return;
		}
		try {
			ResourceLocation id = PropertiesParsingHelper.resolveLegacyVanillaBlockId(new ResourceLocation(value.trim()));
			if (Block.REGISTRY.containsKey(id)) {
				tintBlock = Block.REGISTRY.getObject(id).getDefaultState();
			} else {
				CtmRuleTypes.LOGGER.warn("Unknown block '{}' in 'tintBlock' value '{}' in file '{}' in pack '{}'", id, value, resourceId, packId);
			}
		} catch (RuntimeException e) {
			CtmRuleTypes.LOGGER.warn("Invalid 'tintBlock' value '{}' in file '{}' in pack '{}'", value, resourceId, packId);
		}
	}

	private void parseLayer() {
		String value = properties.getProperty("layer");
		if (value == null) {
			return;
		}
		switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "cutout" -> layer = BlockRenderLayer.CUTOUT;
			case "cutout_mipped" -> layer = BlockRenderLayer.CUTOUT_MIPPED;
			case "translucent" -> layer = BlockRenderLayer.TRANSLUCENT;
			default -> CtmRuleTypes.LOGGER.warn("Unknown 'layer' value '{}' in file '{}' in pack '{}'", value, resourceId, packId);
		}
	}

	@Nullable
	public Set<ResourceLocation> getConnectTilesSet() {
		return connectTilesSet;
	}

	@Nullable
	public Predicate<IBlockState> getConnectBlocksPredicate() {
		return connectBlocksPredicate;
	}

	public int getTintIndex() {
		return tintIndex;
	}

	@Nullable
	public IBlockState getTintBlock() {
		return tintBlock;
	}

	public BlockRenderLayer getLayer() {
		return layer;
	}

	/** Checks the block and/or sprite side targeted by this overlay rule. */
	public boolean matches(IBlockState state, TextureAtlasSprite sprite) {
		if (getMatchBlocksPredicate() != null && !getMatchBlocksPredicate().test(state)) {
			return false;
		}
		if (getMatchTilesSet() != null) {
			return sprite != null && getMatchTilesSet().contains(new ResourceLocation(sprite.getIconName()));
		}
		return true;
	}
}
