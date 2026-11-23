package com.dhj.actinium.resourcepack.sky;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkyFadeTest {
    @Test
    void fadesAcrossMidnightAndRepeatsOnTheFollowingDay() {
        Properties p = new Properties();
        p.setProperty("startFadeIn", "23:00");
        p.setProperty("endFadeIn", "01:00");
        p.setProperty("endFadeOut", "05:00");
        SkyFade fade = SkyFade.parse(p);
        assertEquals(0.5F, fade.brightness(18000, 0));
        assertEquals(1F, fade.brightness(20000, 0));
        assertEquals(0.5F, fade.brightness(22000 + SkyFade.DAY, 0));
        assertEquals(0F, fade.brightness(0, 0));
    }

    @Test
    void rejectsOverlappingIntervals() {
        Properties p = new Properties();
        p.setProperty("startFadeIn", "06:00");
        p.setProperty("endFadeIn", "08:00");
        p.setProperty("startFadeOut", "07:00");
        p.setProperty("endFadeOut", "09:00");
        assertThrows(IllegalArgumentException.class, () -> SkyFade.parse(p));
    }
}
