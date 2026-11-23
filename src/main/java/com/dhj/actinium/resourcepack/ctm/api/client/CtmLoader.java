// Derived from Continuity-For-Actinium (4281538), PepperCode1 and contributors.
// SPDX-License-Identifier: LGPL-3.0-only
package com.dhj.actinium.resourcepack.ctm.api.client;

public interface CtmLoader<T extends CtmProperties> {
	CtmProperties.Factory<T> getPropertiesFactory();

	QuadProcessor.Factory<T> getProcessorFactory();

	CachingPredicates.Factory<T> getPredicatesFactory();
}
