// Derived from Continuity-For-Actinium (4281538), PepperCode1 and contributors.
// SPDX-License-Identifier: LGPL-3.0-only
package com.dhj.actinium.resourcepack.ctm.impl.client;

import java.util.Map;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import com.dhj.actinium.resourcepack.ctm.api.client.CtmLoader;
import com.dhj.actinium.resourcepack.ctm.api.client.CtmLoaderRegistry;

public final class CtmLoaderRegistryImpl implements CtmLoaderRegistry {
	public static final CtmLoaderRegistryImpl INSTANCE = new CtmLoaderRegistryImpl();

	private final Map<String, CtmLoader<?>> loaderMap = new Object2ObjectOpenHashMap<>();

	@Override
	public void registerLoader(String method, CtmLoader<?> loader) {
		loaderMap.put(method, loader);
	}

	@Override
	@Nullable
	public CtmLoader<?> getLoader(String method) {
		return loaderMap.get(method);
	}
}
