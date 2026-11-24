package com.dhj.actinium.resourcepack.item;

import com.dhj.actinium.resourcepack.ctm.client.resource.ResourceRedirectHandler;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;

/** Associates a suffix with a pending block-atlas stitch. */
public final class AtlasEmissiveStitch {
    private static TextureMap pendingAtlas;
    private static String pendingSuffix;

    private AtlasEmissiveStitch() {}

    static void begin(TextureMap atlas, String suffix) {
        pendingAtlas = atlas;
        pendingSuffix = suffix;
    }

    /** Registers companions of vanilla, CTM and CIT entries once all modules have registered sprites. */
    public static void register(TextureMap atlas, IResourceManager manager, Map<String, TextureAtlasSprite> entries) {
        if (pendingAtlas != atlas) return;
        String suffix = pendingSuffix;
        pendingAtlas = null;
        pendingSuffix = null;
        if (suffix.isEmpty()) return;
        for (String name : List.copyOf(entries.keySet())) {
            if (name.endsWith(suffix) || entries.containsKey(name + suffix)) continue;
            ResourceLocation base = new ResourceLocation(name);
            ResourceLocation id = new ResourceLocation(base.getNamespace(), base.getPath() + suffix);
            ResourceLocation file = CitAtlasSprite.source(id);
            if (file == null) {
                file = ResourceRedirectHandler.redirect(new ResourceLocation(id.getNamespace(), atlas.getBasePath() + "/" + id.getPath() + ".png"));
            }
            try (IResource ignored = manager.getResource(file)) {
                atlas.setTextureEntry(new CitAtlasSprite(id, file, atlas));
            } catch (FileNotFoundException ignored) {
                // Emissive companions are optional.
            } catch (IOException e) {
                LogManager.getLogger("Actinium/Emissive").warn("Cannot read {}", file, e);
            }
        }
    }
}
