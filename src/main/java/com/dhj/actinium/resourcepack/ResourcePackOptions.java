package com.dhj.actinium.resourcepack;

import com.dhj.actinium.runtime.ActiniumRuntime;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dhj.embeddedt.embeddium.impl.gui.SodiumGameOptions;
import dhj.embeddedt.embeddium.impl.gui.SodiumGameOptions.ResourcePackSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads feature settings and imports options from a previous standalone Continuity install. */
public final class ResourcePackOptions {
    private ResourcePackOptions() {
    }

    public static boolean enabled(String option) {
        ResourcePackSettings settings = ActiniumRuntime.options().resourcePacks;
        return switch (option) {
            case "terrain" -> settings.connectedTextures || settings.ctmModTextures;
            case "connectedTextures" -> settings.connectedTextures;
            case "ctmModTextures" -> settings.ctmModTextures;
            case "ctmConnectInside" -> settings.ctmConnectInside;
            case "emissive" -> settings.emissive;
            case "cit" -> settings.cit;
            case "customColors" -> settings.customColors;
            case "randomEntities" -> settings.randomEntities;
            case "customSky" -> settings.customSky;
            case "extendedTextures" -> settings.extendedTextures;
            default -> throw new IllegalArgumentException("Unknown resource pack option: " + option);
        };
    }

    public static void importContinuityOptions(Path configDir) {
        SodiumGameOptions options = ActiniumRuntime.options();
        ResourcePackSettings settings = options.resourcePacks;
        Path path = configDir.resolve("continuity.json");
        if (settings.continuityOptionsImported || options.isReadOnly() || !Files.isRegularFile(path)) {
            return;
        }

        try (var reader = Files.newBufferedReader(path)) {
            applyContinuityOptions(JsonParser.parseReader(reader).getAsJsonObject(), settings);
            settings.continuityOptionsImported = true;
            options.writeChanges();
        } catch (IOException | JsonParseException | IllegalStateException ex) {
            ActiniumRuntime.logger().warn("Could not import Continuity settings from {}", path, ex);
        }
    }

    static void applyContinuityOptions(JsonObject legacy, ResourcePackSettings settings) {
        settings.connectedTextures = readBoolean(legacy, "connected_textures", settings.connectedTextures);
        settings.ctmModTextures = readBoolean(legacy, "ctm_mod_textures", settings.ctmModTextures);
        settings.ctmConnectInside = readBoolean(legacy, "ctm_connect_inside", settings.ctmConnectInside);
        settings.emissive = readBoolean(legacy, "emissive_textures", settings.emissive);
    }

    private static boolean readBoolean(JsonObject object, String name, boolean current) {
        JsonElement value = object.get(name);
        if (value == null) {
            return current;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            ActiniumRuntime.logger().warn("Ignoring non-boolean Continuity setting {}", name);
            return current;
        }
        return value.getAsBoolean();
    }
}
