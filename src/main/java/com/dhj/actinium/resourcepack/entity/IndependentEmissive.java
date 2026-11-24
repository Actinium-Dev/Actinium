package com.dhj.actinium.resourcepack.entity;

import com.dhj.actinium.resourcepack.ResourcePackIndex;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

final class IndependentEmissive {
    private static final Logger LOGGER = LogManager.getLogger("Actinium/EntityEmissive");
    private static final ResourceLocation CONFIG = new ResourceLocation("optifine/emissive.properties");

    record Draw(TextureManager manager, int baseId, ResourceLocation glow) {
        int glowId() {
            var texture = manager.getTexture(glow);
            if (texture == null) {
                texture = new SimpleTexture(glow);
                if (!manager.loadTexture(glow, texture)) return 0;
            }
            return texture == TextureUtil.MISSING_TEXTURE ? 0 : texture.getGlTextureId();
        }
    }

    private final Map<ResourceLocation, ResourceLocation> pairs;

    private IndependentEmissive(Map<ResourceLocation, ResourceLocation> pairs) {
        this.pairs = Map.copyOf(pairs);
    }

    static IndependentEmissive load(ResourcePackIndex index) {
        if (!index.exists(CONFIG)) return new IndependentEmissive(Map.of());
        String suffix;
        try {
            suffix = index.readProperties(CONFIG).getProperty("suffix.emissive", "").trim();
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.warn("Cannot read {} in {}", CONFIG, index.packName(CONFIG), e);
            return new IndependentEmissive(Map.of());
        }
        if (suffix.isEmpty()) return new IndependentEmissive(Map.of());
        if (!suffix.matches("[a-z0-9_.-]+")) {
            LOGGER.warn("Invalid suffix.emissive in {}", index.packName(CONFIG));
            return new IndependentEmissive(Map.of());
        }
        String ending = suffix + ".png";
        Map<ResourceLocation, ResourceLocation> pairs = new HashMap<>();
        for (ResourceLocation glow : index.find(id -> id.getPath().endsWith(ending))) {
            String path = glow.getPath();
            ResourceLocation base = new ResourceLocation(glow.getNamespace(), path.substring(0, path.length() - ending.length()) + ".png");
            if (index.exists(base)) pairs.put(base, glow);
        }
        return new IndependentEmissive(pairs);
    }

    Draw bound(TextureManager manager, ResourceLocation selected) {
        ResourceLocation glow = pairs.get(selected);
        return glow == null || manager.getTexture(selected) == TextureUtil.MISSING_TEXTURE
            ? null : new Draw(manager, GLSMTextures.getBoundTextureForServerState(0), glow);
    }
}
