// Derived from Continuity-For-Actinium (4281538), PepperCode1 and contributors.
// SPDX-License-Identifier: LGPL-3.0-only
package com.dhj.actinium.resourcepack.ctm.client.properties;

import java.util.Properties;

import com.dhj.actinium.resourcepack.ctm.client.processor.OrientationMode;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

public class OrientedConnectingCtmProperties extends ConnectingCtmProperties {
	protected OrientationMode orientationMode;

	public OrientedConnectingCtmProperties(Properties properties, ResourceLocation resourceId, String pack, int packPriority, IResourceManager resourceManager, String method, OrientationMode defaultOrientationMode) {
		super(properties, resourceId, pack, packPriority, resourceManager, method);
		orientationMode = defaultOrientationMode;
	}

	public OrientedConnectingCtmProperties(Properties properties, ResourceLocation resourceId, String pack, int packPriority, IResourceManager resourceManager, String method) {
		this(properties, resourceId, pack, packPriority, resourceManager, method, OrientationMode.TEXTURE);
	}

	@Override
	public void init() {
		super.init();
		parseOrient();
	}

	protected void parseOrient() {
		String key = properties.containsKey("orient") ? "orient" : "orientation";
		OrientationMode orientationMode = PropertiesParsingHelper.parseOrientationMode(properties, key, resourceId, packId);
		if (orientationMode != null) {
			this.orientationMode = orientationMode;
		}
	}

	public OrientationMode getOrientationMode() {
		return orientationMode;
	}
}
