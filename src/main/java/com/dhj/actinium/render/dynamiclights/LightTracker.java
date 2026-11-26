/*
 * Adaptive updates and deferred rebuilds adapted from GT New Horizons' Angelica
 * (AdaptiveTickCalculator, AdaptiveTickMode and ChunkRebuildManager), LGPL-3.0.
 */

package com.dhj.actinium.render.dynamiclights;

import dhj.embeddedt.embeddium.impl.util.PositionUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.IdentityHashMap;
import java.util.function.ToIntFunction;

/** Collects client-thread light samples and schedules rebuilds of affected terrain sections. */
public final class LightTracker {
    // Terrain AO can read light two blocks outside the section being meshed (WorldSlice).
    private static final int AO_PADDING = 2;
    private static final double MOVEMENT_THRESHOLD = 0.1;

    private final IdentityHashMap<Object, TrackedLight> lights = new IdentityHashMap<>();
    private final Long2ObjectOpenHashMap<PendingSection> pending = new Long2ObjectOpenHashMap<>();

    private LightSnapshot snapshot = LightSnapshot.EMPTY;
    private long tick;
    private double cameraX;
    private double cameraY;
    private double cameraZ;
    private double lookX;
    private double lookZ;
    private int minimumIntervalTicks;
    private boolean adaptive;
    private double slowDistanceSquared;
    private double slowerDistanceSquared;
    private double backgroundDistanceSquared;
    private boolean force;
    private boolean changed;

    /** Starts a collection using the current camera and client tick. Force bypasses the sampling interval. */
    public void beginTick(long tick, double cameraX, double cameraY, double cameraZ, double lookX, double lookZ,
                          int minimumIntervalTicks, boolean adaptive, int slowDistance, int slowerDistance,
                          int backgroundDistance, boolean force) {
        this.tick = tick;
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        this.cameraZ = cameraZ;
        this.lookX = lookX;
        this.lookZ = lookZ;
        this.minimumIntervalTicks = Math.max(1, minimumIntervalTicks);
        this.adaptive = adaptive;
        this.slowDistanceSquared = (double) slowDistance * slowDistance;
        this.slowerDistanceSquared = (double) slowerDistance * slowerDistance;
        this.backgroundDistanceSquared = (double) backgroundDistance * backgroundDistance;
        this.force = force;
    }

    /** Records a live source by identity and evaluates its luminance on its assigned tick. */
    public <T> void visit(T key, int salt, double x, double y, double z, ToIntFunction<T> luminance) {
        TrackedLight tracked = lights.get(key);
        if (tracked != null) {
            tracked.seen = true;
        }

        if (!force) {
            int interval = Math.max(minimumIntervalTicks, adaptive ? adaptiveInterval(x, y, z) : 1);
            if (Math.floorMod(tick, interval) != Math.floorMod(salt, interval)) {
                return;
            }
        }

        int level = Math.clamp(luminance.applyAsInt(key), 0, 15);
        if (level == 0) {
            if (tracked != null) {
                lights.remove(key);
                invalidate(tracked.sample, true);
            }
            return;
        }

        if (tracked != null) {
            LightSample previous = tracked.sample;
            boolean moved = Math.abs(x - previous.x()) > MOVEMENT_THRESHOLD
                    || Math.abs(y - previous.y()) > MOVEMENT_THRESHOLD
                    || Math.abs(z - previous.z()) > MOVEMENT_THRESHOLD;
            if (!moved && previous.luminance() == level) {
                return;
            }
            invalidate(previous, false);
        }

        LightSample sample = new LightSample(x, y, z, level);
        if (tracked == null) {
            lights.put(key, new TrackedLight(sample));
        } else {
            tracked.sample = sample;
        }
        invalidate(sample, false);
    }

    /** Removes missing sources and returns the snapshot to publish to the chunk workers. */
    public LightSnapshot endTick() {
        var iterator = lights.values().iterator();
        while (iterator.hasNext()) {
            TrackedLight tracked = iterator.next();
            if (tracked.seen) {
                tracked.seen = false;
            } else {
                invalidate(tracked.sample, true);
                iterator.remove();
            }
        }

        if (changed) {
            snapshot = lights.isEmpty() ? LightSnapshot.EMPTY
                    : LightSnapshot.build(() -> lights.values().stream().map(light -> light.sample).iterator());
            changed = false;
        }
        return snapshot;
    }

    /** Returns the most recently sampled luminance of a source for its own rendering. */
    public int luminance(Object key) {
        TrackedLight tracked = lights.get(key);
        return tracked == null ? 0 : tracked.sample.luminance();
    }

    /** Schedules visible, expired and removal rebuilds. Repeated frames at one tick do not age the queue. */
    public void drain(long tick, int maxWaitTicks, SectionVisibility visible, SectionRebuildRequest rebuild) {
        var iterator = pending.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            PendingSection section = iterator.next().getValue();
            if ((section.urgent || tick - section.firstQueuedTick >= maxWaitTicks
                    || visible.isVisible(section.x, section.y, section.z)) && rebuild.request(section.x, section.y, section.z)) {
                iterator.remove();
            }
        }
    }

    /** Reports whether any section still needs its light mesh refreshed. */
    public boolean hasPendingRebuilds() {
        return !pending.isEmpty();
    }

    /** Discards all samples and pending work when unloading a world. */
    public void clear() {
        lights.clear();
        pending.clear();
        snapshot = LightSnapshot.EMPTY;
        changed = false;
    }

    private int adaptiveInterval(double x, double y, double z) {
        double dx = x - cameraX;
        double dy = y - cameraY;
        double dz = z - cameraZ;
        double squaredDistance = dx * dx + dy * dy + dz * dz;
        if (dx * lookX + dz * lookZ < 0 || squaredDistance > backgroundDistanceSquared) {
            return 20;
        }
        if (squaredDistance > slowerDistanceSquared) {
            return 10;
        }
        return squaredDistance > slowDistanceSquared ? 5 : 1;
    }

    private void invalidate(LightSample sample, boolean urgent) {
        changed = true;
        sample.forEachSection(AO_PADDING, (x, y, z) -> {
            long key = PositionUtil.packSection(x, y, z);
            PendingSection section = pending.get(key);
            if (section == null) {
                pending.put(key, new PendingSection(x, y, z, tick, urgent));
            } else if (urgent) {
                section.urgent = true;
            }
        });
    }

    /** Tests section coordinates against the current camera frustum. */
    @FunctionalInterface
    public interface SectionVisibility {
        boolean isVisible(int sx, int sy, int sz);
    }

    /** Schedules a mesh rebuild at the supplied section coordinates. */
    @FunctionalInterface
    public interface SectionRebuild {
        void rebuild(int sx, int sy, int sz);
    }

    /** Requests a rebuild, returning false while the section is still building an older mesh. */
    @FunctionalInterface
    public interface SectionRebuildRequest {
        boolean request(int sx, int sy, int sz);
    }

    private static final class TrackedLight {
        private LightSample sample;
        private boolean seen = true;

        private TrackedLight(LightSample sample) {
            this.sample = sample;
        }
    }

    private static final class PendingSection {
        private final int x;
        private final int y;
        private final int z;
        private final long firstQueuedTick;
        private boolean urgent;

        private PendingSection(int x, int y, int z, long firstQueuedTick, boolean urgent) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.firstQueuedTick = firstQueuedTick;
            this.urgent = urgent;
        }
    }
}
