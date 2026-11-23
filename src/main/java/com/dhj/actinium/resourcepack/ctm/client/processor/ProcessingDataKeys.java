// Derived from Continuity-For-Actinium (4281538), PepperCode1 and contributors.
// SPDX-License-Identifier: LGPL-3.0-only
package com.dhj.actinium.resourcepack.ctm.client.processor;

import java.util.function.Supplier;

import com.dhj.actinium.resourcepack.ctm.api.client.ProcessingDataKey;
import com.dhj.actinium.resourcepack.ctm.api.client.ProcessingDataKeyRegistry;
import com.dhj.actinium.resourcepack.ctm.client.CtmRuleTypes;
import net.minecraft.util.math.BlockPos;

public final class ProcessingDataKeys {
	public static final ProcessingDataKey<BlockPos.MutableBlockPos> MUTABLE_POS = create("mutable_pos", BlockPos.MutableBlockPos::new);

	private static <T> ProcessingDataKey<T> create(String id, Supplier<T> valueSupplier) {
		return ProcessingDataKeyRegistry.get().registerKey(CtmRuleTypes.asId(id), valueSupplier);
	}

	public static void init() {
	}

	private ProcessingDataKeys() {
	}
}
