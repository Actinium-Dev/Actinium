/*
 * Copyright © 2020 LambdAurora <email@lambdaurora.dev>
 *
 * This file is part of SodiumDynamicLights.
 *
 * Licensed under the MIT License. For more information,
 * see the LICENSE file.
 */

package com.dhj.actinium.api.dynamiclights;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** Defines a client-side light contribution for an entity or block entity. */
@FunctionalInterface
public interface DynamicLightHandler<T> {
    /** Returns the light level before the dynamic-light range is applied. */
    int getLuminance(T source);

    /** Returns whether this contribution is extinguished in liquid. */
    default boolean isWaterSensitive(T source) {
        return false;
    }

    /** Creates a handler with independently computed brightness and liquid sensitivity. */
    static <T> DynamicLightHandler<T> makeHandler(ToIntFunction<T> luminance, Predicate<T> waterSensitive) {
        Objects.requireNonNull(luminance);
        Objects.requireNonNull(waterSensitive);
        return new DynamicLightHandler<>() {
            @Override
            public int getLuminance(T source) {
                return luminance.applyAsInt(source);
            }

            @Override
            public boolean isWaterSensitive(T source) {
                return waterSensitive.test(source);
            }
        };
    }
}
