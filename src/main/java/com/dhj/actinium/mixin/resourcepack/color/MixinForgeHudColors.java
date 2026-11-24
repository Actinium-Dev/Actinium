package com.dhj.actinium.mixin.resourcepack.color;

import com.dhj.actinium.resourcepack.color.ResourcePackColors;
import net.minecraftforge.client.GuiIngameForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = GuiIngameForge.class, remap = false)
abstract class MixinForgeHudColors {
    @ModifyConstant(method = "renderExperience", constant = @Constant(intValue = 8453920))
    private int actinium$experienceText(int original) {
        return ResourcePackColors.color("text.xpbar", original);
    }
}
