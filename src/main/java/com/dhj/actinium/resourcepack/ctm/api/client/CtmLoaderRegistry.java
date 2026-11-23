// Derived from Continuity-For-Actinium (4281538), PepperCode1 and contributors.
// SPDX-License-Identifier: LGPL-3.0-only
package com.dhj.actinium.resourcepack.ctm.api.client;

import javax.annotation.Nullable;

import com.dhj.actinium.resourcepack.ctm.impl.client.CtmLoaderRegistryImpl;

public interface CtmLoaderRegistry {
	static CtmLoaderRegistry get() {
		return CtmLoaderRegistryImpl.INSTANCE;
	}

	void registerLoader(String method, CtmLoader<?> loader);

	@Nullable
	CtmLoader<?> getLoader(String method);
}
