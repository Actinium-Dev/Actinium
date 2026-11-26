/*
 * Copyright © 2020 LambdAurora <email@lambdaurora.dev>
 *
 * This file is part of SodiumDynamicLights.
 *
 * Licensed under the MIT License. For more information,
 * see the LICENSE file.
 */

package com.dhj.actinium.api.dynamiclights;

import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.MathHelper;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/** Registers additive client-side entity and block entity light contributions by class. */
public final class DynamicLightHandlers {
    private static final Map<Class<?>, Contribution<Entity>> ENTITY_HANDLERS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Contribution<TileEntity>> TILE_HANDLERS = new ConcurrentHashMap<>();

    private DynamicLightHandlers() {
    }

    /** Registers a contribution for the given entity class and its subclasses. */
    public static <T extends Entity> void registerEntityDynamicLightHandler(Class<T> type,
                                                                           DynamicLightHandler<? super T> handler) {
        register(ENTITY_HANDLERS, type, handler);
    }

    /** Registers a contribution for the given block entity class and its subclasses. */
    public static <T extends TileEntity> void registerTileEntityDynamicLightHandler(Class<T> type,
                                                                                   DynamicLightHandler<? super T> handler) {
        register(TILE_HANDLERS, type, handler);
    }

    /** Returns the strongest registered entity contribution; the liquid check is evaluated only when needed. */
    public static int getLuminanceFrom(Entity entity, BooleanSupplier submerged) {
        return getLuminance(ENTITY_HANDLERS, entity, submerged);
    }

    /** Returns the strongest registered block entity contribution; the liquid check is evaluated only when needed. */
    public static int getLuminanceFrom(TileEntity tile, BooleanSupplier submerged) {
        return getLuminance(TILE_HANDLERS, tile, submerged);
    }

    /** Reports whether a block entity can have a registered light contribution. */
    public static boolean hasTileHandlers() {
        return !TILE_HANDLERS.isEmpty();
    }

    private static <S, T extends S> void register(Map<Class<?>, Contribution<S>> handlers, Class<T> type,
                                                  DynamicLightHandler<? super T> handler) {
        Objects.requireNonNull(handler);
        Contribution<S> contribution = (source, submerged) -> {
            T typed = type.cast(source);
            return handler.isWaterSensitive(typed) && submerged.getAsBoolean() ? 0 : handler.getLuminance(typed);
        };
        handlers.merge(type, contribution, (first, second) ->
                (source, submerged) -> Math.max(first.getLuminance(source, submerged), second.getLuminance(source, submerged)));
    }

    private static <S> int getLuminance(Map<Class<?>, Contribution<S>> handlers, S source, BooleanSupplier submerged) {
        if (handlers.isEmpty()) {
            return 0;
        }
        int luminance = 0;
        for (Class<?> type = source.getClass(); type != null; type = type.getSuperclass()) {
            Contribution<S> contribution = handlers.get(type);
            if (contribution != null) {
                luminance = Math.max(luminance, contribution.getLuminance(source, submerged));
            }
        }
        return MathHelper.clamp(luminance, 0, 14);
    }

    @FunctionalInterface
    private interface Contribution<T> {
        int getLuminance(T source, BooleanSupplier submerged);
    }
}
