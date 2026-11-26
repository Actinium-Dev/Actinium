/*
 * Copyright © 2020 LambdAurora <email@lambdaurora.dev>
 *
 * This file is part of LambDynamicLights.
 *
 * Licensed under the MIT License. For more information,
 * see the LICENSE file.
 */

package com.dhj.actinium.api.dynamiclights;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.Objects;

/** Defines a client-side light contribution for stacks of a single item. */
public abstract class ItemLightSource {
    private final ResourceLocation id;
    private final Item item;
    private final boolean waterSensitive;

    protected ItemLightSource(ResourceLocation id, Item item, boolean waterSensitive) {
        this.id = Objects.requireNonNull(id);
        this.item = Objects.requireNonNull(item);
        this.waterSensitive = waterSensitive;
    }

    /** Returns the identifier used in registration and reload diagnostics. */
    public final ResourceLocation id() {
        return id;
    }

    /** Returns the item to which this definition applies. */
    public final Item item() {
        return item;
    }

    /** Returns whether this contribution is extinguished in liquid. */
    public final boolean waterSensitive() {
        return waterSensitive;
    }

    /** Returns the light level for the given stack before the dynamic-light range is applied. */
    public abstract int getLuminance(ItemStack stack);

    /** Gives all stacks of an item the same light level. */
    public static final class StaticItemLightSource extends ItemLightSource {
        private final int luminance;

        public StaticItemLightSource(ResourceLocation id, Item item, int luminance, boolean waterSensitive) {
            super(id, item, waterSensitive);
            this.luminance = luminance;
        }

        public StaticItemLightSource(ResourceLocation id, Item item, int luminance) {
            this(id, item, luminance, false);
        }

        @Override
        public int getLuminance(ItemStack stack) {
            return luminance;
        }
    }
}
