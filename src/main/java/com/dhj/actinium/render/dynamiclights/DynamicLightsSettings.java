package com.dhj.actinium.render.dynamiclights;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

/** Client settings stored in the standalone addon's Forge configuration file. */
public final class DynamicLightsSettings {
    private static final Logger LOGGER = LogManager.getLogger("Actinium Dynamic Lights");

    public enum Mode {
        OFF(0), SLOW(10), FAST(5), REALTIME(1);

        private final int intervalTicks;

        Mode(int intervalTicks) {
            this.intervalTicks = intervalTicks;
        }

        /** Returns the minimum interval between updates, or zero when lighting is off. */
        public int intervalTicks() {
            return intervalTicks;
        }
    }

    public enum ExplosiveMode {
        OFF, SIMPLE, FANCY
    }

    public Mode mode = Mode.REALTIME;
    public boolean entitiesLightSource = true;
    public boolean selfLightSource = true;
    public boolean blockEntitiesLightSource = true;
    public boolean waterSensitiveCheck = true;
    public ExplosiveMode creeperLightingMode = ExplosiveMode.FANCY;
    public ExplosiveMode tntLightingMode = ExplosiveMode.FANCY;
    public boolean adaptiveTicking = true;
    public boolean frustumCulling = true;
    public int slowDistance = 32;
    public int slowerDistance = 64;
    public int backgroundDistance = 128;
    public int maxWaitTicks = 100;

    private final Configuration config;

    DynamicLightsSettings(Configuration config) {
        this.config = config;
        mode = Mode.valueOf(property("dynamicLightsMode", mode.name(), Property.Type.STRING).getString());
        entitiesLightSource = readBoolean("entitiesLightSource", entitiesLightSource);
        selfLightSource = readBoolean("selfLightSource", selfLightSource);
        blockEntitiesLightSource = readBoolean("blockEntitiesLightSource", blockEntitiesLightSource);
        waterSensitiveCheck = readBoolean("waterSensitiveCheck", waterSensitiveCheck);
        creeperLightingMode = ExplosiveMode.valueOf(property("creeperLightingMode", creeperLightingMode.name(), Property.Type.STRING).getString());
        tntLightingMode = ExplosiveMode.valueOf(property("tntLightingMode", tntLightingMode.name(), Property.Type.STRING).getString());
        adaptiveTicking = readBoolean("adaptiveTicking", adaptiveTicking);
        frustumCulling = readBoolean("frustumCulling", frustumCulling);
        slowDistance = readPositiveInt("slowDistance", slowDistance);
        slowerDistance = readPositiveInt("slowerDistance", slowerDistance);
        backgroundDistance = readPositiveInt("backgroundDistance", backgroundDistance);
        maxWaitTicks = readPositiveInt("maxWaitTicks", maxWaitTicks);
    }

    public static DynamicLightsSettings load(File file) {
        try {
            return new DynamicLightsSettings(new Configuration(file));
        } catch (RuntimeException e) {
            LOGGER.error("Failed to load dynamic lights settings from {}", file, e);
            throw e;
        }
    }

    /** Saves the known settings while retaining unrecognized Forge properties and categories. */
    public void save() {
        writeProperties();
        config.save();
    }

    void writeProperties() {
        property("dynamicLightsMode", mode.name(), Property.Type.STRING).set(mode.name());
        writeBoolean("entitiesLightSource", entitiesLightSource);
        writeBoolean("selfLightSource", selfLightSource);
        writeBoolean("blockEntitiesLightSource", blockEntitiesLightSource);
        writeBoolean("waterSensitiveCheck", waterSensitiveCheck);
        property("creeperLightingMode", creeperLightingMode.name(), Property.Type.STRING).set(creeperLightingMode.name());
        property("tntLightingMode", tntLightingMode.name(), Property.Type.STRING).set(tntLightingMode.name());
        writeBoolean("adaptiveTicking", adaptiveTicking);
        writeBoolean("frustumCulling", frustumCulling);
        writePositiveInt("slowDistance", slowDistance);
        writePositiveInt("slowerDistance", slowerDistance);
        writePositiveInt("backgroundDistance", backgroundDistance);
        writePositiveInt("maxWaitTicks", maxWaitTicks);
    }

    private boolean readBoolean(String key, boolean defaultValue) {
        Property property = property(key, Boolean.toString(defaultValue), Property.Type.BOOLEAN);
        if (!property.isBooleanValue()) {
            throw new IllegalArgumentException("Invalid boolean for " + key + ": " + property.getString());
        }
        return property.getBoolean();
    }

    private int readPositiveInt(String key, int defaultValue) {
        Property property = property(key, Integer.toString(defaultValue), Property.Type.INTEGER);
        int value = Integer.parseInt(property.getString());
        requirePositive(key, value);
        return value;
    }

    private void writeBoolean(String key, boolean value) {
        property(key, Boolean.toString(value), Property.Type.BOOLEAN).set(value);
    }

    private void writePositiveInt(String key, int value) {
        requirePositive(key, value);
        property(key, Integer.toString(value), Property.Type.INTEGER).set(value);
    }

    private static void requirePositive(String key, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(key + " must be positive: " + value);
        }
    }

    private Property property(String key, String defaultValue, Property.Type type) {
        return config.get(Configuration.CATEGORY_GENERAL, key, defaultValue, null, type);
    }
}
