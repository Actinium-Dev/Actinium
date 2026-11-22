package com.dhj.actinium.debug;

import com.dhj.actinium.compat.ichunutil.PortalRenderState;
import com.dhj.actinium.render.GuiGlStateBoundary;
import com.dhj.actinium.render.RenderWorldRecursionGuard;
import com.dhj.actinium.render.RenderingState;
import com.dhj.actinium.render.terrain.ActiniumWorldRenderer;
import com.dhj.actinium.runtime.ActiniumRuntime;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.coderbot.iris.shadows.ShadowRenderingState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.tileentity.TileEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Captures world inventory snapshots on the client thread and writes them on a dedicated worker. */
public final class WorldRenderInventory {
    private static final Logger LOGGER = LogManager.getLogger("ActiniumRenderInventory");
    private static final long SNAPSHOT_INTERVAL = TimeUnit.SECONDS.toNanos(5);
    private static final Executor REPORT_WRITER = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("Actinium Render Inventory Writer").daemon().factory());
    private static @Nullable WorldRenderInventory active;

    private final WorldClient world;
    private final TerrainRenderInventory terrain;
    private final Path directory;
    private final IntOpenHashSet entities = new IntOpenHashSet();
    private final LongOpenHashSet blockEntities = new LongOpenHashSet();
    private final Map<String, Integer> entityTypes = new TreeMap<>();
    private final Map<String, Integer> blockEntityTypes = new TreeMap<>();
    // Only the writer accesses the report path.
    private @Nullable Path report;
    private volatile boolean writeFailed;
    private @Nullable CompletableFuture<Void> lastWrite;
    private boolean dirty = true;
    private long submittedTerrainRevision;
    private long lastSnapshot;

    public WorldRenderInventory(WorldClient world, TerrainRenderInventory terrain, Path gameDirectory) {
        this.world = world;
        this.terrain = terrain;
        this.directory = gameDirectory.resolve("logs").resolve("actinium-render-inventory");
    }

    /** Makes the current renderer's session available during the outer world pass. */
    public static void beginWorldPass(Minecraft client) {
        if (ActiniumRuntime.options().debug.enableRenderInventory && client.world != null) {
            var renderer = ActiniumWorldRenderer.instanceNullable();
            if (renderer != null && renderer.isRenderingWorld(client.world)) {
                active = renderer.worldRenderInventory();
            }
        }
    }

    /** Leaves the recording scope and queues a changed snapshot when the writer is available. */
    public static void endWorldPass() {
        var inventory = active;
        active = null;
        if (inventory != null && (inventory.lastWrite == null
                || (inventory.lastWrite.isDone() && System.nanoTime() - inventory.lastSnapshot >= SNAPSHOT_INTERVAL))) {
            inventory.submitSnapshot();
        }
    }

    private static @Nullable WorldRenderInventory recording(@Nullable WorldClient world) {
        var inventory = active;
        if (inventory == null || inventory.writeFailed || inventory.world != world
                || PortalRenderState.isRenderingPortal() || RenderWorldRecursionGuard.isNested()
                || ShadowRenderingState.areShadowsCurrentlyBeingRendered()
                || GuiGlStateBoundary.isEntityGuiSurface() || RenderingState.isRenderingGuiItem()) {
            return null;
        }
        return inventory;
    }

    /** Counts a dispatched renderer or mesh provider once per block position. */
    public static void recordBlockEntity(TileEntity tile, TileEntitySpecialRenderer<?> renderer) {
        var inventory = recording(tile.getWorld() instanceof WorldClient world ? world : null);
        if (inventory != null && inventory.blockEntities.add(tile.getPos().toLong())) {
            String key = tile.getBlockType().getRegistryName() + "\t" + tile.getClass().getName()
                    + "\t" + renderer.getClass().getName();
            inventory.blockEntityTypes.merge(key, 1, Integer::sum);
            inventory.dirty = true;
        }
    }

    /** Counts a world entity dispatched to its renderer. */
    public static void recordEntity(Entity entity, Render<?> renderer) {
        var inventory = recording(entity.world instanceof WorldClient world ? world : null);
        if (inventory != null) {
            inventory.addEntity(entity, renderer);
        }
    }

    /** Counts an entity submitted through shadow replay without a main-pass renderer callback. */
    public static void recordReplayedEntity(Entity entity, RenderManager renderManager) {
        var inventory = recording(entity.world instanceof WorldClient world ? world : null);
        if (inventory != null && !inventory.entities.contains(entity.getEntityId())) {
            inventory.addEntity(entity, renderManager.getEntityRenderObject(entity));
        }
    }

    private void addEntity(Entity entity, @Nullable Render<?> renderer) {
        if (renderer != null && this.entities.add(entity.getEntityId())) {
            var id = EntityList.getKey(entity);
            String type = id == null ? entity.getClass().getName() : id.toString();
            this.entityTypes.merge(type + "\t\t" + renderer.getClass().getName(), 1, Integer::sum);
            this.dirty = true;
        }
    }

    /** Saves the final snapshot before unloading or rebuilding the renderer. */
    public void close() {
        if (active == this) {
            active = null;
        }
        this.awaitLastWrite();
        this.submitSnapshot();
        this.awaitLastWrite();
    }

    private void awaitLastWrite() {
        if (this.lastWrite != null) {
            this.lastWrite.join();
        }
    }

    private void submitSnapshot() {
        long terrainRevision = this.terrain.revision();
        if (this.writeFailed || (!this.dirty && this.submittedTerrainRevision == terrainRevision)) {
            return;
        }

        String snapshot = this.buildSnapshot();
        this.lastWrite = CompletableFuture.runAsync(() -> this.writeSnapshot(snapshot), REPORT_WRITER);
        this.submittedTerrainRevision = terrainRevision;
        this.dirty = false;
        this.lastSnapshot = System.nanoTime();
    }

    private String buildSnapshot() {
        StringBuilder snapshot = new StringBuilder("# world=")
                .append(this.world.getWorldInfo().getWorldName().replace('\t', ' ').replace('\r', ' ').replace('\n', ' '))
                .append(" dimension=").append(this.world.provider.getDimension())
                .append(" entities=").append(this.entities.size())
                .append(" blockEntities=").append(this.blockEntities.size())
                .append(' ').append(this.terrain.export()).append('\n');
        this.entityTypes.forEach((key, count) -> snapshot.append("ENTITY\t").append(count).append('\t').append(key).append('\n'));
        this.blockEntityTypes.forEach((key, count) -> snapshot.append("TESR\t").append(count).append('\t').append(key).append('\n'));
        return snapshot.toString();
    }

    private void writeSnapshot(String snapshot) {
        try {
            if (this.report == null) {
                Files.createDirectories(this.directory);
                this.report = Files.createTempFile(this.directory, "render-inventory-", ".tsv");
                LOGGER.info("Writing world render inventory to {}", this.report);
            }
            Path temporary = Files.createTempFile(this.directory, "render-inventory-", ".tmp");
            try {
                Files.writeString(temporary, snapshot);
                Files.move(temporary, this.report, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException | RuntimeException e) {
            this.writeFailed = true;
            LOGGER.warn("Could not write the world render inventory to {}", this.directory, e);
        }
    }
}
