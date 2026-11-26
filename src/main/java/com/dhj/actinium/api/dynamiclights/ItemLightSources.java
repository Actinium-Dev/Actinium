/*
 * Copyright © 2020 LambdAurora <email@lambdaurora.dev>
 *
 * This file is part of LambDynamicLights.
 *
 * Licensed under the MIT License. For more information,
 * see the LICENSE file.
 */

package com.dhj.actinium.api.dynamiclights;

import com.dhj.actinium.render.dynamiclights.rules.ItemLightDefinitions;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/** Resolves stack light from API registrations, resource packs, or the stack's block state. */
public final class ItemLightSources {
    private static final Logger LOGGER = LogManager.getLogger(ItemLightSources.class);
    private static final Map<Item, ItemLightSource> STATIC_SOURCES = new ConcurrentHashMap<>();
    private static volatile Map<Item, ItemLightSource> packSources = Map.of();

    private ItemLightSources() {
    }

    /** Registers a source that takes precedence over resource packs and survives reloads. */
    public static void registerItemLightSource(ItemLightSource source) {
        ItemLightSource previous = STATIC_SOURCES.putIfAbsent(source.item(), source);
        if (previous != null) {
            LOGGER.warn("Ignoring duplicate dynamic item light {} for {}; already registered by {}",
                    source.id(), source.item().getRegistryName(), previous.id());
        }
    }

    /** Replaces the resource-pack definitions after a client resource reload. */
    public static void reload(IResourceManager resources) {
        packSources = ItemLightDefinitions.load(resources);
    }

    /** Returns the stack's dynamic light level; the liquid check is evaluated only for sensitive sources. */
    public static int getLuminance(ItemStack stack, BooleanSupplier submerged) {
        if (stack.isEmpty()) {
            return 0;
        }

        Item item = stack.getItem();
        ItemLightSource source = STATIC_SOURCES.get(item);
        if (source == null) {
            source = packSources.get(item);
        }

        int luminance = 0;
        if (source != null) {
            if (!source.waterSensitive() || !submerged.getAsBoolean()) {
                luminance = source.getLuminance(stack);
            }
        } else if (item instanceof ItemBlock blockItem) {
            luminance = ItemLightDefinitions.blockLuminance(stack, blockItem.getBlock());
        }
        return MathHelper.clamp(luminance, 0, 14);
    }
}
