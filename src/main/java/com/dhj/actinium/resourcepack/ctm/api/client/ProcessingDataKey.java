// Derived from Continuity-For-Actinium (4281538), PepperCode1 and contributors.
// SPDX-License-Identifier: LGPL-3.0-only
package com.dhj.actinium.resourcepack.ctm.api.client;

import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;

public interface ProcessingDataKey<T> {
	ResourceLocation getId();

	int getRawId();

	Supplier<T> getValueSupplier();

	@Nullable
	Consumer<T> getValueResetAction();
}
