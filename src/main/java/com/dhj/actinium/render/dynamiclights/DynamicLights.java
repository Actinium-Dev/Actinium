package com.dhj.actinium.render.dynamiclights;

import com.dhj.actinium.render.dynamiclights.DynamicLightsSettings.Mode;
import com.dhj.actinium.render.terrain.CameraHelper;
import com.dhj.actinium.runtime.ActiniumRuntime;
import com.dhj.actinium.world.WorldSlice;
import dhj.embeddedt.embeddium.impl.render.viewport.Viewport;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

import java.io.File;
import java.util.function.ToIntFunction;

/** Collects lights in the active client world and publishes snapshots to terrain and object renderers. */
public final class DynamicLights {
    private static final LightTracker.SectionVisibility ALL_SECTIONS = (x, y, z) -> true;
    private static DynamicLightsSettings settings;
    private static volatile DynamicLights active;
    private static long clientTick;

    private final WorldClient world;
    private final LightTracker tracker = new LightTracker();
    private final ToIntFunction<Entity> entityLight = entity -> DynamicLightRules.entity(entity, this.player, settings);
    private final ToIntFunction<TileEntity> tileLight = tile -> DynamicLightRules.tileEntity(tile, settings);
    private EntityPlayer player;
    private volatile LightSnapshot snapshot = LightSnapshot.EMPTY;
    private long lastTick = Long.MIN_VALUE;
    private boolean refresh = true;

    private DynamicLights(WorldClient world) {
        this.world = world;
    }

    public static void initialize(IReloadableResourceManager resources) {
        if (Loader.isModLoaded("celeritasdynamiclights")) {
            ActiniumRuntime.logger().info("Using the installed Celeritas Dynamic Lights mod for dynamic lighting");
            return;
        }
        settings = DynamicLightsSettings.load(new File(Loader.instance().getConfigDir(), "celeritasdynamiclights.cfg"));
        resources.registerReloadListener(manager -> {
            DynamicLightRules.reload(manager);
            settingsChanged();
        });
    }

    public static boolean isBuiltIn() {
        return settings != null;
    }

    public static DynamicLightsSettings settings() {
        return settings;
    }

    public static void settingsChanged() {
        DynamicLights lights = active;
        if (lights != null) lights.refresh = true;
    }

    public static void unload(World world) {
        DynamicLights lights = active;
        if (lights != null && lights.world == world) {
            active = null;
            lights.tracker.clear();
        }
    }

    /** Advances the local clock independently of time updates received from the server. */
    public static void tick() {
        clientTick++;
    }

    /** Called before submitting terrain builds for the main camera, including when the world is paused. */
    public static void update(WorldClient world, EntityPlayer player, Viewport viewport,
                              LightTracker.SectionRebuildRequest rebuild) {
        if (!isBuiltIn()) return;
        DynamicLights lights = active;
        if (settings.mode == Mode.OFF && lights == null) return;
        if (lights == null || lights.world != world) active = lights = new DynamicLights(world);

        long tick = clientTick;
        if (tick != lights.lastTick || lights.refresh || player != lights.player) {
            lights.player = player;
            var camera = viewport.getTransform();
            if (settings.adaptiveTicking && settings.mode != Mode.OFF) {
                var forward = CameraHelper.getForwardDirection();
                lights.collect(tick, camera.x, camera.y, camera.z, forward.x, forward.z);
            } else {
                lights.collect(tick, camera.x, camera.y, camera.z, 0, 0);
            }
        }
        boolean cull = settings.mode != Mode.OFF && settings.frustumCulling;
        lights.tracker.drain(tick, settings.maxWaitTicks,
                cull ? (x, y, z) -> viewport.isBoxVisible((x << 4) + 8, (y << 4) + 8, (z << 4) + 8, 8.0f) : ALL_SECTIONS,
                rebuild);
        if (settings.mode == Mode.OFF && !lights.tracker.hasPendingRebuilds()) active = null;
    }

    private void collect(long tick, double x, double y, double z, double lookX, double lookZ) {
        tracker.beginTick(tick, x, y, z, lookX, lookZ,
                settings.mode.intervalTicks(), settings.adaptiveTicking, settings.slowDistance, settings.slowerDistance,
                settings.backgroundDistance, refresh);
        if (settings.mode != Mode.OFF) {
            if (settings.entitiesLightSource) {
                for (Entity entity : world.loadedEntityList) {
                    if (entity != player && !entity.isDead) visitEntity(entity);
                }
            }
            if (settings.selfLightSource && player != null && player.world == world && !player.isDead) visitEntity(player);
            if (settings.blockEntitiesLightSource && DynamicLightRules.hasTileHandlers()) {
                for (TileEntity tile : world.loadedTileEntityList) {
                    if (tile.isInvalid()) continue;
                    BlockPos pos = tile.getPos();
                    tracker.visit(tile, pos.hashCode(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, tileLight);
                }
            }
        }
        snapshot = tracker.endTick();
        lastTick = tick;
        refresh = false;
    }

    private void visitEntity(Entity entity) {
        tracker.visit(entity, entity.getEntityId(), entity.posX, entity.posY + entity.getEyeHeight(), entity.posZ, entityLight);
    }

    public static LightSnapshot snapshot(IBlockAccess world) {
        DynamicLights lights = active;
        return lights != null && lights.world == world && settings.mode != Mode.OFF ? lights.snapshot : LightSnapshot.EMPTY;
    }

    public static int merge(IBlockAccess world, BlockPos pos, int packed) {
        LightSnapshot light = world instanceof WorldSlice slice ? slice.dynamicLights() : snapshot(world);
        return light.merge(pos, packed);
    }

    public static int blockBrightness(IBlockAccess access, BlockPos pos, int packed) {
        LightSnapshot light = access instanceof WorldSlice slice ? slice.dynamicLights() : snapshot(access);
        if (light == LightSnapshot.EMPTY) return packed;
        // AO treats an unlit opaque sample as dark even when a light source is on its other side.
        var state = access.getBlockState(pos);
        return state.isOpaqueCube() && state.getLightValue(access, pos) == 0 ? packed : light.merge(pos, packed);
    }

    public static int entityBrightness(Entity entity, int packed) {
        DynamicLights lights = active;
        if (lights == null || lights.world != entity.world || settings.mode == Mode.OFF || lights.snapshot == LightSnapshot.EMPTY) return packed;
        int nearby = lights.snapshot.merge(MathHelper.floor(entity.posX), MathHelper.floor(entity.posY), MathHelper.floor(entity.posZ), packed);
        return withMinimum(nearby, lights.tracker.luminance(entity));
    }

    public static int handBrightness(EntityPlayer player, ItemStack mainHand, ItemStack offHand, int packed) {
        DynamicLights lights = active;
        if (lights == null || lights.world != player.world || settings.mode == Mode.OFF) return packed;
        int nearby = lights.snapshot.merge(MathHelper.floor(player.posX), MathHelper.floor(player.posY + player.getEyeHeight()),
                MathHelper.floor(player.posZ), packed);
        return withMinimum(nearby, DynamicLightRules.heldItems(player, mainHand, offHand, settings));
    }

    /** Applies a block-light floor to the low lightmap coordinate without touching the sky coordinate. */
    public static int withMinimum(int packed, int minimum) {
        return (packed & 0xff) >= (minimum << 4) ? packed : (packed & ~0xff) | (minimum << 4);
    }
}
