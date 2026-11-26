package com.dhj.actinium.render.dynamiclights;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightTrackerTest {
    private static final int SKY_LIGHT = 11 << 20;
    private static final int FULL_LIGHT = SKY_LIGHT | 15 << 4;

    private final LightTracker tracker = new LightTracker();
    private final Object source = new Object();

    @Test
    void mergesStrongestAttenuatedBlockLightAcrossSignedSectionBoundaries() {
        begin(0);
        tracker.visit(source, 0, -0.5, 24.5, 15.5, key -> 15);
        tracker.visit(new Object(), 0, -0.5, 24.5, 15.5, key -> 10);
        LightSnapshot snapshot = tracker.endTick();

        assertEquals(FULL_LIGHT, snapshot.merge(new BlockPos(-1, 24, 15), SKY_LIGHT));
        int acrossBoundary = snapshot.merge(0, 24, 16, SKY_LIGHT);
        int farther = snapshot.merge(4, 24, 16, SKY_LIGHT);
        assertEquals(SKY_LIGHT | 12 << 4, acrossBoundary);
        assertTrue(farther > SKY_LIGHT && farther < acrossBoundary);
        assertEquals(SKY_LIGHT, snapshot.merge(7, 24, 15, SKY_LIGHT));
        int brighterStatic = SKY_LIGHT | 13 << 4;
        assertEquals(brighterStatic, snapshot.merge(0, 24, 16, brighterStatic));
    }

    @Test
    void comparesWholeBlockLightCoordinateWithoutChangingOtherPackedBits() {
        begin(0);
        tracker.visit(source, 0, 8.5, 24.5, 8.5, key -> 12);
        DynamicLightSampler sampler = tracker.endTick();
        BlockPos pos = new BlockPos(8, 24, 8);
        int otherBits = SKY_LIGHT | 0x00070500 | 0x80000000;

        assertEquals(otherBits | 0xc0, sampler.merge(pos, otherBits | 0xb3));
        assertEquals(otherBits | 0xc3, sampler.merge(pos, otherBits | 0xc3));
    }

    @Test
    void rebuildsSectionsWhoseAoSamplesReachAnAdjacentLitBlock() {
        begin(0);
        tracker.visit(source, 0, 7.5, 24.5, 24.5, key -> 15);
        LightSnapshot snapshot = tracker.endTick();
        assertEquals(SKY_LIGHT, snapshot.merge(16, 24, 24, SKY_LIGHT));
        assertTrue(snapshot.merge(14, 24, 24, SKY_LIGHT) > SKY_LIGHT);

        Set<Section> rebuilt = drain(0, 100, (x, y, z) -> true);
        assertTrue(rebuilt.contains(new Section(1, 1, 1)));
    }

    @Test
    void movementRebuildsOldAndNewSectionsWithoutMutatingWorkerSnapshot() throws Exception {
        begin(0);
        tracker.visit(source, 0, 8.5, 24.5, 8.5, key -> 15);
        LightSnapshot old = tracker.endTick();
        drain(0, 100, (x, y, z) -> true);

        try (var worker = Executors.newSingleThreadExecutor()) {
            assertEquals(FULL_LIGHT, worker.submit(() -> old.merge(8, 24, 8, SKY_LIGHT)).get());
            begin(1);
            tracker.visit(source, 0, 72.5, 24.5, 8.5, key -> 15);
            LightSnapshot moved = tracker.endTick();
            Set<Section> rebuilt = drain(1, 100, (x, y, z) -> true);
            assertTrue(rebuilt.contains(new Section(0, 1, 0)));
            assertTrue(rebuilt.contains(new Section(4, 1, 0)));
            assertEquals(SKY_LIGHT, moved.merge(8, 24, 8, SKY_LIGHT));
            assertEquals(FULL_LIGHT, moved.merge(72, 24, 8, SKY_LIGHT));
            assertEquals(FULL_LIGHT, worker.submit(() -> old.merge(8, 24, 8, SKY_LIGHT)).get());
            assertEquals(SKY_LIGHT, worker.submit(() -> old.merge(72, 24, 8, SKY_LIGHT)).get());

            begin(2);
            tracker.visit(source, 0, 72.5, 24.5, 8.5, key -> 15);
            assertSame(moved, tracker.endTick());
            assertTrue(drain(2, 100, (x, y, z) -> true).isEmpty());
        }
    }

    @Test
    void smallMovementAccumulatesFromTheLastPublishedPosition() {
        begin(0);
        tracker.visit(source, 0, 8.5, 24.5, 8.5, key -> 15);
        LightSnapshot initial = tracker.endTick();
        drain(0, 100, (x, y, z) -> true);

        begin(1);
        tracker.visit(source, 0, 8.59, 24.59, 8.59, key -> 15);
        assertSame(initial, tracker.endTick());
        assertTrue(drain(1, 100, (x, y, z) -> true).isEmpty());

        begin(2);
        tracker.visit(source, 0, 8.625, 24.59, 8.59, key -> 15);
        LightSnapshot moved = tracker.endTick();
        assertTrue(moved.merge(8, 24, 8, SKY_LIGHT) < initial.merge(8, 24, 8, SKY_LIGHT));
        assertTrue(drain(2, 100, (x, y, z) -> true).contains(new Section(0, 1, 0)));
    }

    @Test
    void absentAndExtinguishedSourcesRemoveLightBeforeTheFrustumTimeout() {
        Object extinguished = new Object();
        begin(0);
        tracker.visit(source, 0, 8.5, 24.5, 8.5, key -> 15);
        tracker.visit(extinguished, 0, 72.5, 24.5, 8.5, key -> 11);
        tracker.endTick();
        assertTrue(drain(0, 100, (x, y, z) -> false).isEmpty());

        begin(1);
        tracker.visit(extinguished, 0, 72.5, 24.5, 8.5, key -> 0);
        assertSame(LightSnapshot.EMPTY, tracker.endTick());
        assertEquals(0, tracker.luminance(source));
        assertEquals(0, tracker.luminance(extinguished));
        Set<Section> rebuilt = drain(1, 100, (x, y, z) -> false);
        assertTrue(rebuilt.contains(new Section(0, 1, 0)));
        assertTrue(rebuilt.contains(new Section(4, 1, 0)));
        assertTrue(drain(1, 0, (x, y, z) -> true).isEmpty());
    }

    @Test
    void visibilityCanChangeWithinATickAndRepeatedEnqueueKeepsFirstTick() {
        begin(100);
        tracker.visit(source, 0, 8.5, 24.5, 8.5, key -> 15);
        tracker.endTick();
        for (int frame = 0; frame < 20; frame++) {
            assertTrue(drain(100, 3, (x, y, z) -> false).isEmpty());
        }
        assertEquals(Set.of(new Section(-1, 1, 0)), drain(100, 3, (x, y, z) -> x == -1 && y == 1 && z == 0));

        begin(102);
        tracker.visit(source, 0, 8.5, 24.5, 8.5, key -> 10);
        tracker.endTick();
        assertTrue(drain(102, 3, (x, y, z) -> false).isEmpty());
        Set<Section> expired = drain(103, 3, (x, y, z) -> false);
        assertTrue(expired.contains(new Section(0, 1, 0)));
        assertFalse(expired.contains(new Section(-1, 1, 0)));
        assertEquals(Set.of(new Section(-1, 1, 0)), drain(105, 3, (x, y, z) -> false));
    }

    @Test
    void adaptiveSamplingStaggersSourcesByDistanceAndCameraDirection() {
        List<Emitter> emitters = List.of(new Emitter(2, 1), new Emitter(40, 1), new Emitter(40, 2),
                new Emitter(80, 1), new Emitter(140, 1), new Emitter(-2, 1));
        ToIntFunction<Emitter> sample = emitter -> {
            emitter.samples++;
            return 15;
        };
        for (long tick = 1; tick <= 20; tick++) {
            tracker.beginTick(tick, 0, 0, 0, 1, 0, 1, true, 32, 64, 128, false);
            for (Emitter emitter : emitters) {
                tracker.visit(emitter, emitter.salt, emitter.x, 0, 0, sample);
            }
            tracker.endTick();
            if (tick == 1) {
                assertEquals(15, tracker.luminance(emitters.get(1)));
                assertEquals(0, tracker.luminance(emitters.get(2)));
            } else if (tick == 2) {
                assertEquals(15, tracker.luminance(emitters.get(1)));
                assertEquals(15, tracker.luminance(emitters.get(2)));
            }
        }
        assertEquals(List.of(20, 4, 4, 2, 1, 1), emitters.stream().map(emitter -> emitter.samples).toList());

        Emitter behind = emitters.getLast();
        tracker.beginTick(22, 0, 0, 0, -1, 0, 1, true, 32, 64, 128, false);
        tracker.visit(behind, behind.salt, behind.x, 0, 0, sample);
        tracker.endTick();
        assertEquals(2, behind.samples);
    }

    @Test
    void minimumIntervalAndForceControlSamplingAndClearDiscardsWorldWork() {
        Emitter emitter = new Emitter(0, -1);
        ToIntFunction<Emitter> sample = key -> {
            key.samples++;
            return 15;
        };

        tracker.beginTick(0L, 0, 0, 0, 1, 0, 4, true, 32, 64, 128, false);
        tracker.visit(emitter, emitter.salt, 0, 0, 0, sample);
        assertSame(LightSnapshot.EMPTY, tracker.endTick());
        assertEquals(0, emitter.samples);

        tracker.beginTick(1L, 0, 0, 0, 1, 0, 4, true, 32, 64, 128, true);
        tracker.visit(emitter, emitter.salt, 0, 0, 0, sample);
        LightSnapshot old = tracker.endTick();
        assertEquals(1, emitter.samples);

        tracker.clear();
        begin(0);
        assertSame(LightSnapshot.EMPTY, tracker.endTick());
        assertTrue(drain(100, 0, (x, y, z) -> true).isEmpty());
        assertEquals(0, tracker.luminance(emitter));
        assertTrue(old.merge(0, 0, 0, SKY_LIGHT) > SKY_LIGHT);
    }

    private void begin(long tick) {
        tracker.beginTick(tick, 0, 0, 0, 1, 0, 1, false, 32, 64, 128, false);
    }

    @Test
    void retainsASectionUntilItsCurrentBuildFinishesEvenWhenTheLightIsRemoved() {
        begin(0);
        tracker.visit(source, 0, 8.5, 24.5, 8.5, key -> 14);
        tracker.endTick();
        tracker.drain(0, 100, (x, y, z) -> true, (x, y, z) -> false);
        assertTrue(tracker.hasPendingRebuilds());

        begin(1);
        assertSame(LightSnapshot.EMPTY, tracker.endTick());
        tracker.drain(1, 100, (x, y, z) -> false, (x, y, z) -> false);
        assertTrue(tracker.hasPendingRebuilds());

        assertTrue(drain(1, 100, (x, y, z) -> false).contains(new Section(0, 1, 0)));
        assertFalse(tracker.hasPendingRebuilds());
    }

    private Set<Section> drain(long tick, int wait, LightTracker.SectionVisibility visible) {
        List<Section> calls = new ArrayList<>();
        tracker.drain(tick, wait, visible, (x, y, z) -> calls.add(new Section(x, y, z)));
        Set<Section> sections = new HashSet<>(calls);
        assertEquals(calls.size(), sections.size());
        return sections;
    }

    private record Section(int x, int y, int z) {}

    private static final class Emitter {
        private final double x;
        private final int salt;
        private int samples;

        private Emitter(double x, int salt) {
            this.x = x;
            this.salt = salt;
        }
    }
}
