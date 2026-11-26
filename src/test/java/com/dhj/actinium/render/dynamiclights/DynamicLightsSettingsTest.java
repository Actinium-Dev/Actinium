package com.dhj.actinium.render.dynamiclights;

import com.dhj.actinium.render.dynamiclights.DynamicLightsSettings.ExplosiveMode;
import com.dhj.actinium.render.dynamiclights.DynamicLightsSettings.Mode;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import org.junit.jupiter.api.Test;

import static net.minecraftforge.common.config.Configuration.CATEGORY_GENERAL;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DynamicLightsSettingsTest {
    @Test
    void loadsStandalonePropertiesAndWritesChangesToTheSameKeys() {
        Configuration config = new Configuration();
        config.get(CATEGORY_GENERAL, "dynamicLightsMode", "SLOW");
        config.get(CATEGORY_GENERAL, "entitiesLightSource", false);
        config.get(CATEGORY_GENERAL, "selfLightSource", false);
        config.get(CATEGORY_GENERAL, "blockEntitiesLightSource", false);
        config.get(CATEGORY_GENERAL, "waterSensitiveCheck", false);
        config.get(CATEGORY_GENERAL, "creeperLightingMode", "SIMPLE");
        config.get(CATEGORY_GENERAL, "tntLightingMode", "OFF");

        DynamicLightsSettings settings = new DynamicLightsSettings(config);
        assertEquals(Mode.SLOW, settings.mode);
        assertFalse(settings.entitiesLightSource);
        assertFalse(settings.selfLightSource);
        assertFalse(settings.blockEntitiesLightSource);
        assertFalse(settings.waterSensitiveCheck);
        assertEquals(ExplosiveMode.SIMPLE, settings.creeperLightingMode);
        assertEquals(ExplosiveMode.OFF, settings.tntLightingMode);

        settings.mode = Mode.FAST;
        settings.selfLightSource = true;
        settings.tntLightingMode = ExplosiveMode.FANCY;
        settings.writeProperties();

        assertEquals("FAST", config.getCategory(CATEGORY_GENERAL).get("dynamicLightsMode").getString());
        assertEquals("true", config.getCategory(CATEGORY_GENERAL).get("selfLightSource").getString());
        assertEquals("FANCY", config.getCategory(CATEGORY_GENERAL).get("tntLightingMode").getString());
    }

    @Test
    void roundTripsSchedulingAndRetainsOtherForgeProperties() {
        Configuration config = new Configuration();
        config.get("general.entities_settings", "minecraft:creeper", false);
        String[] elements = {"minecraft:torch", "minecraft:glowstone"};
        config.get(CATEGORY_GENERAL, "futureSources", elements);
        config.get("other", "dynamicLightsMode", "OFF");

        DynamicLightsSettings settings = new DynamicLightsSettings(config);
        settings.adaptiveTicking = false;
        settings.frustumCulling = false;
        settings.slowDistance = 40;
        settings.slowerDistance = 80;
        settings.backgroundDistance = 160;
        settings.maxWaitTicks = 120;
        settings.writeProperties();

        DynamicLightsSettings reloaded = new DynamicLightsSettings(config);
        assertEquals(settings.adaptiveTicking, reloaded.adaptiveTicking);
        assertEquals(settings.frustumCulling, reloaded.frustumCulling);
        assertEquals(settings.slowDistance, reloaded.slowDistance);
        assertEquals(settings.slowerDistance, reloaded.slowerDistance);
        assertEquals(settings.backgroundDistance, reloaded.backgroundDistance);
        assertEquals(settings.maxWaitTicks, reloaded.maxWaitTicks);
        assertEquals(Property.Type.INTEGER, config.getCategory(CATEGORY_GENERAL).get("maxWaitTicks").getType());
        assertFalse(config.getCategory("general.entities_settings").get("minecraft:creeper").getBoolean());
        assertArrayEquals(elements, config.getCategory(CATEGORY_GENERAL).get("futureSources").getStringList());
        assertEquals("OFF", config.getCategory("other").get("dynamicLightsMode").getString());
    }
}
