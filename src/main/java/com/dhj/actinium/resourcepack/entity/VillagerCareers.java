package com.dhj.actinium.resourcepack.entity;

import com.dhj.actinium.resourcepack.entity.mixinterface.VillagerCareerAccess;
import com.google.common.collect.MapMaker;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraftforge.fml.common.registry.VillagerRegistry.VillagerProfession;

import java.util.Map;
import java.util.concurrent.Future;

final class VillagerCareers {
    private static final Map<EntityVillager, Lookup> LOOKUPS = new MapMaker().weakKeys().makeMap();

    private static final class Lookup {
        final VillagerProfession profession;
        volatile int career;
        Future<?> pending;
        int retryAt;

        Lookup(VillagerProfession profession) { this.profession = profession; }
    }

    private VillagerCareers() {}

    static int read(EntityVillager villager) {
        int local = ((VillagerCareerAccess) villager).actinium$getCareerId();
        if (local > 0) return local;

        Minecraft minecraft = Minecraft.getMinecraft();
        var connection = minecraft.getConnection();
        var server = minecraft.getIntegratedServer();
        if (server == null || connection == null || !connection.getNetworkManager().isLocalChannel()) return 0;

        var profession = villager.getProfessionForge();
        Lookup lookup = LOOKUPS.compute(villager, (ignored, existing) ->
            existing != null && existing.profession == profession ? existing : new Lookup(profession));
        int age = villager.ticksExisted;
        if (age >= lookup.retryAt && (lookup.pending == null || lookup.pending.isDone())) {
            var uuid = villager.getUniqueID();
            int dimension = villager.dimension;
            int professionId = villager.getProfession();
            lookup.retryAt = age + 100;
            lookup.pending = server.addScheduledTask(() -> {
                lookup.career = server.getEntityFromUuid(uuid) instanceof EntityVillager host && host.dimension == dimension
                    && host.getProfession() == professionId ? ((VillagerCareerAccess) host).actinium$getCareerId() : 0;
            });
        }
        return lookup.career;
    }
}
