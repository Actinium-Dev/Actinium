package com.dhj.actinium.mixin.mod.librarianlib;

import com.dhj.actinium.render.terrain.compile.ChunkBufferBuilder;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.teamwizardry.librarianlib.core.client.RenderHookHandler$blockHooks$1", remap = false)
public abstract class MixinBlockGlowHook {
    @WrapWithCondition(
            method = "invoke(Lnet/minecraft/client/renderer/BlockModelRenderer;"
                    + "Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/renderer/block/model/IBakedModel;"
                    + "Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;"
                    + "Lnet/minecraft/client/renderer/BufferBuilder;)V",
            at = @At(value = "INVOKE", target = "Lcom/teamwizardry/librarianlib/core/client/GlowingHandler;glow("
                    + "Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/renderer/block/model/IBakedModel;"
                    + "Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;"
                    + "Lnet/minecraft/client/renderer/BufferBuilder;)V", remap = false),
            remap = false)
    private boolean actinium$keepVanillaGlow(IBlockAccess world, IBakedModel model, IBlockState state,
                                            BlockPos pos, BufferBuilder buffer) {
        return !(buffer instanceof ChunkBufferBuilder chunkBuffer) || !chunkBuffer.consumeFastModelRender();
    }
}
