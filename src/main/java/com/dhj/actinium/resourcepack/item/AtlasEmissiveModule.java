package com.dhj.actinium.resourcepack.item;

import com.dhj.actinium.resourcepack.ResourcePackIndex;
import com.dhj.actinium.resourcepack.ResourcePackReload;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;

final class AtlasEmissiveModule implements ResourcePackReload.Module<String, AtlasEmissive> {
    private static final Pattern SUFFIX = Pattern.compile("[a-z0-9_.-]*");

    @Override public boolean requiresAtlas() { return true; }

    @Override
    public String option() {
        return "emissive";
    }

    @Override
    public String prepare(ResourcePackIndex index) {
        ResourceLocation modern = new ResourceLocation("optifine/emissive.properties");
        ResourceLocation legacy = new ResourceLocation("mcpatcher/emissive.properties");
        ResourceLocation[] configs = index.priority(legacy) > index.priority(modern)
            ? new ResourceLocation[]{legacy, modern} : new ResourceLocation[]{modern, legacy};
        for (ResourceLocation config : configs) {
            if (!index.exists(config)) continue;
            try {
                String suffix = index.readProperties(config).getProperty("suffix.emissive", "").trim();
                if (!SUFFIX.matcher(suffix).matches()) throw new IllegalArgumentException("Invalid suffix.emissive: " + suffix);
                return suffix;
            } catch (IOException | IllegalArgumentException e) {
                LogManager.getLogger("Actinium/Emissive").warn("Invalid {} in {}", config, index.packName(config), e);
            }
        }
        return "";
    }

    @Override
    public void registerSprites(String suffix, TextureMap atlas) {
        AtlasEmissiveStitch.begin(atlas, suffix);
    }

    @Override
    public AtlasEmissive finish(String suffix, ResourcePackIndex index, TextureMap atlas) {
        Map<TextureAtlasSprite, TextureAtlasSprite> pairs = new HashMap<>();
        if (!suffix.isEmpty()) {
            atlas.mapUploadedSprites.forEach((name, sprite) -> {
                if (name.endsWith(suffix) || sprite == atlas.getMissingSprite()) return;
                TextureAtlasSprite emissive = atlas.getAtlasSprite(name + suffix);
                if (emissive != atlas.getMissingSprite()) pairs.put(sprite, emissive);
            });
        }
        return pairs.isEmpty() ? null : new AtlasEmissive(pairs);
    }
}
