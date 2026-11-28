package com.dhj.actinium.test.launch;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.spongepowered.asm.launch.MixinTweaker;

import java.util.Map;

/** Keeps Launch's tweaker package exclusion separate from the probe and its fixture mixins. */
public final class ShadowFastTesrTweaker extends MixinTweaker {
    static {
        FMLLaunchHandler.setSide(Side.CLIENT);
        Launch.blackboard.put("forgeLaunchArgs", Map.of());
    }

    public ShadowFastTesrTweaker() {
        MixinExtrasBootstrap.init();
    }

    @Override
    public String getLaunchTarget() {
        return "com.dhj.actinium.test.shadow.ShadowFastTesrProbe";
    }
}
