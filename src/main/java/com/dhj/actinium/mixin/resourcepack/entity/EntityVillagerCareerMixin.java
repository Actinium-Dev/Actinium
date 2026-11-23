package com.dhj.actinium.mixin.resourcepack.entity;

import com.dhj.actinium.resourcepack.entity.mixinterface.VillagerCareerAccess;
import net.minecraft.entity.passive.EntityVillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(EntityVillager.class)
public abstract class EntityVillagerCareerMixin implements VillagerCareerAccess {
    @Shadow private int careerId;

    @Override
    public int actinium$getCareerId() {
        return careerId;
    }
}
