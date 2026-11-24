// Derived from Continuity-For-Actinium (4281538), PepperCode1 and contributors.
// SPDX-License-Identifier: LGPL-3.0-only
package com.dhj.actinium.resourcepack.ctm.api.client;

import java.util.Collection;
import java.util.Properties;

import javax.annotation.Nullable;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

public interface CtmProperties extends Comparable<CtmProperties> {
	Collection<ResourceLocation> getSpriteDependencies();

	interface Factory<T extends CtmProperties> {
		@Nullable
		T createProperties(Properties properties, ResourceLocation resourceId, String pack, int packPriority, IResourceManager resourceManager, String method);
	}
}
