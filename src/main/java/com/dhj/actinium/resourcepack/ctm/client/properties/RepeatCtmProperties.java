// Derived from Continuity-For-Actinium (4281538), PepperCode1 and contributors.
// SPDX-License-Identifier: LGPL-3.0-only
package com.dhj.actinium.resourcepack.ctm.client.properties;

import java.util.Properties;

import com.dhj.actinium.resourcepack.ctm.client.CtmRuleTypes;
import com.dhj.actinium.resourcepack.ctm.client.processor.OrientationMode;
import com.dhj.actinium.resourcepack.ctm.client.processor.Symmetry;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

public class RepeatCtmProperties extends BaseCtmProperties {
	protected int width;
	protected int height;
	protected Symmetry symmetry = Symmetry.NONE;
	protected OrientationMode orientationMode = OrientationMode.NONE;

	public RepeatCtmProperties(Properties properties, ResourceLocation resourceId, String pack, int packPriority, IResourceManager resourceManager, String method) {
		super(properties, resourceId, pack, packPriority, resourceManager, method);
	}

	@Override
	public void init() {
		super.init();
		parseWidth();
		parseHeight();
		parseSymmetry();
		parseOrient();
	}

	protected void parseWidth() {
		String widthStr = properties.getProperty("width");
		if (widthStr == null) {
			CtmRuleTypes.LOGGER.error("No 'width' value provided in file '" + resourceId + "' in pack '" + packId + "'");
			valid = false;
			return;
		}

		try {
			int width = Integer.parseInt(widthStr.trim());
			if (width > 0) {
				this.width = width;
				return;
			}
		} catch (NumberFormatException e) {
			// logged below
		}
		CtmRuleTypes.LOGGER.error("Invalid 'width' value '" + widthStr + "' in file '" + resourceId + "' in pack '" + packId + "'");
		valid = false;
	}

	protected void parseHeight() {
		String heightStr = properties.getProperty("height");
		if (heightStr == null) {
			CtmRuleTypes.LOGGER.error("No 'height' value provided in file '" + resourceId + "' in pack '" + packId + "'");
			valid = false;
			return;
		}

		try {
			int height = Integer.parseInt(heightStr.trim());
			if (height > 0) {
				this.height = height;
				return;
			}
		} catch (NumberFormatException e) {
			// logged below
		}
		CtmRuleTypes.LOGGER.error("Invalid 'height' value '" + heightStr + "' in file '" + resourceId + "' in pack '" + packId + "'");
		valid = false;
	}

	protected void parseSymmetry() {
		Symmetry symmetry = PropertiesParsingHelper.parseSymmetry(properties, "symmetry", resourceId, packId);
		if (symmetry != null) {
			this.symmetry = symmetry;
		}
	}

	protected void parseOrient() {
		OrientationMode orientationMode = PropertiesParsingHelper.parseOrientationMode(properties, "orient", resourceId, packId);
		if (orientationMode != null) {
			this.orientationMode = orientationMode;
		}
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public Symmetry getSymmetry() {
		return symmetry;
	}

	public OrientationMode getOrientationMode() {
		return orientationMode;
	}

	public static class Validator<T extends RepeatCtmProperties> implements TileAmountValidator<T> {
		@Override
		public boolean validateTileAmount(int amount, T properties) {
			int targetAmount = properties.getWidth() * properties.getHeight();
			if (amount == targetAmount) {
				return true;
			}
			CtmRuleTypes.LOGGER.error("Method '" + properties.getMethod() + "' requires exactly " + targetAmount + " tiles but " + amount + " were provided in file '" + properties.getResourceId() + "' in pack '" + properties.getPackId() + "'");
			return false;
		}
	}
}
