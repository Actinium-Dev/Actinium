package com.dhj.actinium.mixin.mod.librarianlib.mixinterface;

import com.teamwizardry.librarianlib.core.client.GlowingHandler;
import com.teamwizardry.librarianlib.features.base.block.IGlowingBlock;
import net.minecraft.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = GlowingHandler.class, remap = false)
public interface AccessorGlowingHandler {
    @Accessor("blockRenderSpecialHandlers")
    static Map<Block, IGlowingBlock> actinium$getBlockHandlers() {
        throw new AssertionError();
    }
}
