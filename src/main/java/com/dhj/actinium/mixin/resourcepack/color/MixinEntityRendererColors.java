package com.dhj.actinium.mixin.resourcepack.color;

import com.dhj.actinium.resourcepack.color.ResourcePackColors;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
abstract class MixinEntityRendererColors {
    @Shadow @Final private Minecraft mc;
    @Shadow private float torchFlickerX;
    @Shadow @Final private int[] lightmapColors;
    @Shadow protected abstract float getNightVisionBrightness(EntityLivingBase entity, float partialTicks);

    @Inject(method = "updateLightmap", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/DynamicTexture;updateDynamicTexture()V"))
    private void actinium$customLightmap(float partialTicks, CallbackInfo ci) {
        float vision = mc.player.isPotionActive(MobEffects.NIGHT_VISION) ? getNightVisionBrightness(mc.player, partialTicks) : 0;
        ResourcePackColors.lightmap(mc.world, lightmapColors, partialTicks, torchFlickerX, vision, mc.gameSettings.gammaSetting);
    }

    @WrapOperation(method = "updateFogColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getFogColor(F)Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d actinium$customFog(World world, float partialTicks, Operation<Vec3d> original) {
        return ResourcePackColors.atmosphere("fog", world, mc.getRenderViewEntity(), partialTicks, original.call(world, partialTicks));
    }

    @WrapOperation(method = "updateFogColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getFogColor(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Vec3d;F)Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d actinium$customFluidFog(Block block, World world, BlockPos pos, IBlockState state,
                                         Entity camera, Vec3d color, float partialTicks, Operation<Vec3d> original) {
        Vec3d vanilla = original.call(block, world, pos, state, camera, color, partialTicks);
        if (state.getMaterial() == Material.WATER) return ResourcePackColors.submerged("underwater", world, camera, partialTicks, vanilla);
        if (state.getMaterial() == Material.LAVA) return ResourcePackColors.submerged("underlava", world, camera, partialTicks, vanilla);
        return vanilla;
    }
}
