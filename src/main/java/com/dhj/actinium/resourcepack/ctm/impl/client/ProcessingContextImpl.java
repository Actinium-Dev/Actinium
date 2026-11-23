// Derived from Continuity-For-Actinium (4281538), PepperCode1 and contributors.
// SPDX-License-Identifier: LGPL-3.0-only
package com.dhj.actinium.resourcepack.ctm.impl.client;

import java.util.List;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import com.dhj.actinium.resourcepack.ctm.api.client.ProcessingDataKey;
import com.dhj.actinium.resourcepack.ctm.api.client.ProcessingDataKeyRegistry;
import com.dhj.actinium.resourcepack.ctm.api.client.QuadProcessor;
import net.minecraft.client.renderer.block.model.BakedQuad;

public class ProcessingContextImpl implements QuadProcessor.ProcessingContext {
	protected final List<BakedQuad> extraQuads = new ObjectArrayList<>();
	protected final Object[] processingData = new Object[ProcessingDataKeyRegistry.get().getRegisteredAmount()];

	@Override
	public List<BakedQuad> getExtraQuads() {
		return extraQuads;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getData(ProcessingDataKey<T> key) {
		int index = key.getRawId();
		T data = (T) processingData[index];
		if (data == null) {
			data = key.getValueSupplier().get();
			processingData[index] = data;
		}
		return data;
	}

	public void reset() {
		extraQuads.clear();
		List<ProcessingDataKey<?>> allResettable = ProcessingDataKeyRegistryImpl.INSTANCE.getAllResettable();
		for (ProcessingDataKey<?> key : allResettable) {
			resetData(key);
		}
	}

	protected <T> void resetData(ProcessingDataKey<T> key) {
		T value = getDataOrNull(key);
		if (value != null) {
			key.getValueResetAction().accept(value);
		}
	}

	@SuppressWarnings("unchecked")
	@javax.annotation.Nullable
	protected <T> T getDataOrNull(ProcessingDataKey<T> key) {
		return (T) processingData[key.getRawId()];
	}
}
