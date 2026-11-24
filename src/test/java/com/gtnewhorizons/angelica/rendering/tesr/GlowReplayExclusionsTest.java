package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.api.tesr.TesrGlowDepth;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlowReplayExclusionsTest {
    @Test
    void configuredSuperclassNameMatchesWithoutClassLoading() {
        assertTrue(GlowReplayExclusions.matches(Child.class, List.of(Parent.class.getName())));
        assertFalse(GlowReplayExclusions.matches(Unrelated.class, List.of(Parent.class.getName())));
    }

    @Test
    void apiRegistrationAlsoMatchesSubclassesAndTakesEffectImmediately() {
        assertFalse(GlowReplayExclusions.matches(RegisteredChild.class, List.of()));
        TesrGlowDepth.excludeFromReplay(RegisteredParent.class);
        assertTrue(GlowReplayExclusions.matches(RegisteredChild.class, List.of()));
        assertFalse(GlowReplayExclusions.matches(Unrelated.class, List.of()));
    }

    private static class Parent {}
    private static final class Child extends Parent {}
    private static final class Unrelated {}
    private static class RegisteredParent {}
    private static final class RegisteredChild extends RegisteredParent {}
}
