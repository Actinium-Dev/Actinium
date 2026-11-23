package com.dhj.actinium.resourcepack.entity;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.MapMaker;
import net.minecraft.block.BlockBed;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityLlama;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBed;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityShulkerBox;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.IWorldNameable;

import java.util.Map;
import java.util.UUID;

final class RandomTextureSubjects {
    private record Origin(int height, String biome) {}

    private static final Map<World, Cache<UUID, Origin>> ORIGINS = new MapMaker().weakKeys().makeMap();

    private RandomTextureSubjects() {}

    static void entityAdded(Entity entity) {
        if (entity.world != null && entity.world.isRemote) entityOrigin(entity);
    }

    static RandomTextureRules.Subject entity(Entity entity, boolean usesCareer) {
        World world = entity.world;
        if (world == null || !world.isRemote) return null;
        Origin origin = entityOrigin(entity);
        int seed = (int) entity.getUniqueID().getLeastSignificantBits() & Integer.MAX_VALUE;
        EntityLivingBase living = entity instanceof EntityLivingBase value ? value : null;
        String profession = null;
        int career = 0;
        if (entity instanceof EntityVillager villager) {
            ResourceLocation id = villager.getProfessionForge().getRegistryName();
            if (id != null) profession = id.toString();
            if (usesCareer) career = VillagerCareers.read(villager);
        }
        EnumDyeColor color = switch (entity) {
            case EntityWolf wolf when wolf.isTamed() -> wolf.getCollarColor();
            case EntitySheep sheep -> sheep.getFleeceColor();
            case EntityLlama llama -> llama.getColor();
            default -> null;
        };
        return subject(world, origin, seed, entity.hasCustomName() ? entity.getCustomNameTag() : null, profession, career, color, living,
            entity instanceof EntitySlime slime ? slime.getSlimeSize() : -1);
    }

    static RandomTextureRules.Subject tile(TileEntity tile) {
        World world = tile.getWorld();
        if (world == null || !world.isRemote) return null;
        BlockPos pos = tile.getPos();
        if (tile instanceof TileEntityChest chest) {
            chest.checkForAdjacentChests();
            if (chest.adjacentChestXNeg != null) pos = pos.west();
            if (chest.adjacentChestZNeg != null) pos = pos.north();
        } else if (tile instanceof TileEntityBed && world.getBlockState(pos).getBlock() instanceof BlockBed) {
            var state = world.getBlockState(pos);
            if (state.getValue(BlockBed.PART) == BlockBed.EnumPartType.FOOT) {
                pos = pos.offset(state.getValue(BlockBed.FACING));
            }
        }
        int seed = (int) MathHelper.getPositionRandom(pos) & Integer.MAX_VALUE;
        EnumDyeColor color = switch (tile) {
            case TileEntityBed bed -> bed.getColor();
            case TileEntityShulkerBox box -> box.getColor();
            default -> null;
        };
        return subject(world, origin(world, pos), seed, tile instanceof IWorldNameable named && named.hasCustomName() ? named.getName() : null,
            null, -1, color, null, -1);
    }

    private static RandomTextureRules.Subject subject(World world, Origin origin, int seed, String name,
                                                       String profession, int career, EnumDyeColor color,
                                                       EntityLivingBase living, int size) {
        return new RandomTextureRules.Subject(seed, origin.biome, origin.height,
            name, profession, career, color == null ? null : color.getName(), living == null ? 0 : living.getHealth(),
            living == null ? 0 : living.getMaxHealth(), Math.floorMod(world.getWorldTime(), 24000), world.getMoonPhase(),
            world.isThundering() ? "thunder" : world.isRaining() ? "rain" : "clear", size, living != null && living.isChild());
    }

    private static Origin origin(World world, BlockPos pos) {
        ResourceLocation biome = world.getBiome(pos).getRegistryName();
        return new Origin(pos.getY(), biome == null ? null : RandomTextureRules.biomeName(biome.toString()));
    }

    private static Origin entityOrigin(Entity entity) {
        // Spawn packets contain the tracking position; vanilla does not store the original birth location.
        return ORIGINS.computeIfAbsent(entity.world, ignored -> CacheBuilder.newBuilder().maximumSize(65536).build())
            .asMap().computeIfAbsent(entity.getUniqueID(), ignored -> origin(entity.world, entity.getPosition()));
    }
}
