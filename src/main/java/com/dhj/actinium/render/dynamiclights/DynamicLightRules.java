/*
 * Copyright © 2020 LambdAurora <email@lambdaurora.dev>
 *
 * This file is part of SodiumDynamicLights.
 *
 * Licensed under the MIT License. For more information,
 * see the LICENSE file.
 */

package com.dhj.actinium.render.dynamiclights;

import com.dhj.actinium.api.dynamiclights.DynamicLightHandlers;
import com.dhj.actinium.api.dynamiclights.ItemLightSources;
import com.dhj.actinium.render.dynamiclights.DynamicLightsSettings.ExplosiveMode;
import com.dhj.actinium.render.dynamiclights.rules.DynamicLightFluids;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityMagmaCube;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntitySpectralArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.MathHelper;

import java.util.function.BooleanSupplier;

/** Evaluates vanilla and registered light sources for the client renderer. */
public final class DynamicLightRules {
    private static final BooleanSupplier DRY = () -> false;
    private static final int MAX_LIGHT = 14;
    private static final int VANILLA_TNT_FUSE = 80;

    private DynamicLightRules() {
    }

    public static int entity(Entity entity, EntityPlayer localPlayer, DynamicLightsSettings settings) {
        if (entity.isDead || !isEntityLightEnabled(entity, localPlayer, settings)) {
            return 0;
        }
        if (entity.isBurning() || entity.isGlowing()) {
            return MAX_LIGHT;
        }

        BooleanSupplier bodySubmerged = settings.waterSensitiveCheck ? () -> DynamicLightFluids.isBodyInLiquid(entity) : DRY;
        int luminance = DynamicLightHandlers.getLuminanceFrom(entity, bodySubmerged);
        if (entity instanceof EntityLivingBase living) {
            BooleanSupplier eyesSubmerged = eyesInLiquid(living, settings);
            luminance = Math.max(luminance, equipmentLuminance(living.getHeldEquipment(), eyesSubmerged));
            luminance = Math.max(luminance, equipmentLuminance(living.getArmorInventoryList(), eyesSubmerged));
        }

        int intrinsic = switch (entity) {
            case EntityBlaze ignored -> bodySubmerged.getAsBoolean() ? 0 : 10;
            case EntityCreeper creeper -> creeperLuminance(creeper, settings.creeperLightingMode, bodySubmerged);
            case EntityEnderman enderman -> {
                IBlockState held = enderman.getHeldBlockState();
                yield held == null ? 0 : held.getLightValue();
            }
            case EntityItem item -> ItemLightSources.getLuminance(item.getItem(), bodySubmerged);
            case EntityItemFrame frame -> ItemLightSources.getLuminance(frame.getDisplayedItem(), eyesInLiquid(frame, settings));
            case EntityMagmaCube magmaCube -> magmaCube.squishFactor > 0.6F ? 11 : 8;
            case EntitySpectralArrow ignored -> 8;
            case EntityTNTPrimed tnt -> tntLuminance(tnt, settings.tntLightingMode);
            default -> 0;
        };
        return Math.min(MAX_LIGHT, Math.max(luminance, intrinsic));
    }

    static int tileEntity(TileEntity tile, DynamicLightsSettings settings) {
        if (!settings.blockEntitiesLightSource || tile.isInvalid()) {
            return 0;
        }
        BooleanSupplier submerged = settings.waterSensitiveCheck
                ? () -> tile.getWorld() != null && DynamicLightFluids.isLiquid(tile.getWorld(), tile.getPos())
                : DRY;
        return DynamicLightHandlers.getLuminanceFrom(tile, submerged);
    }

    static boolean hasTileHandlers() {
        return DynamicLightHandlers.hasTileHandlers();
    }

    static int heldItems(EntityPlayer player, ItemStack mainHand, ItemStack offHand, DynamicLightsSettings settings) {
        if (!isEntityLightEnabled(player, player, settings)) {
            return 0;
        }
        BooleanSupplier submerged = eyesInLiquid(player, settings);
        return Math.max(ItemLightSources.getLuminance(mainHand, submerged), ItemLightSources.getLuminance(offHand, submerged));
    }

    static void reload(IResourceManager resources) {
        ItemLightSources.reload(resources);
    }

    private static boolean isEntityLightEnabled(Entity entity, EntityPlayer localPlayer, DynamicLightsSettings settings) {
        if (entity instanceof EntityPlayer player && player.isSpectator()) {
            return false;
        }
        return entity == localPlayer ? settings.selfLightSource : settings.entitiesLightSource;
    }

    private static BooleanSupplier eyesInLiquid(Entity entity, DynamicLightsSettings settings) {
        return settings.waterSensitiveCheck ? () -> DynamicLightFluids.areEyesInLiquid(entity) : DRY;
    }

    private static int equipmentLuminance(Iterable<ItemStack> equipment, BooleanSupplier submerged) {
        int light = 0;
        for (ItemStack stack : equipment) {
            light = Math.max(light, ItemLightSources.getLuminance(stack, submerged));
        }
        return light;
    }

    private static int creeperLuminance(EntityCreeper creeper, ExplosiveMode mode, BooleanSupplier submerged) {
        float flash = creeper.getCreeperFlashIntensity(0.0F);
        if (mode == ExplosiveMode.OFF || flash <= 0.001F || submerged.getAsBoolean()) {
            return 0;
        }
        return mode == ExplosiveMode.SIMPLE ? 10 : (int) (flash * 10.0F);
    }

    private static int tntLuminance(EntityTNTPrimed tnt, ExplosiveMode mode) {
        return switch (mode) {
            case OFF -> 0;
            case SIMPLE -> 10;
            case FANCY -> {
                float fuseRatio = MathHelper.clamp((float) tnt.getFuse() / VANILLA_TNT_FUSE, 0.0F, 1.0F);
                yield 10 - (int) (fuseRatio * fuseRatio * 10.0F);
            }
        };
    }
}
