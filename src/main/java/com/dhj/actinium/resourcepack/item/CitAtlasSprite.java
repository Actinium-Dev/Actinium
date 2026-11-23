package com.dhj.actinium.resourcepack.item;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Function;
import net.minecraft.client.renderer.texture.PngSizeInfo;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

final class CitAtlasSprite extends TextureAtlasSprite {
    private static final String PREFIX = "actinium_cit/";
    private final ResourceLocation file;
    private final TextureMap atlas;

    CitAtlasSprite(ResourceLocation sprite, ResourceLocation file, TextureMap atlas) {
        super(sprite.toString());
        this.file = file;
        this.atlas = atlas;
    }

    static ResourceLocation id(ResourceLocation file) {
        return new ResourceLocation(file.getNamespace(), PREFIX + file.getPath().substring(0, file.getPath().length() - 4));
    }

    static ResourceLocation source(ResourceLocation sprite) {
        String path = sprite.getPath();
        return path.startsWith(PREFIX) ? new ResourceLocation(sprite.getNamespace(), path.substring(PREFIX.length()) + ".png") : null;
    }

    @Override
    public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
        return true;
    }

    @Override
    public boolean load(IResourceManager manager, ResourceLocation location, Function<ResourceLocation, TextureAtlasSprite> sprites) {
        try (IResource metadata = manager.getResource(file); IResource frames = manager.getResource(file)) {
            loadSprite(PngSizeInfo.makeFromResource(manager.getResource(file)), metadata.getMetadata("animation") != null);
            loadSpriteFrames(frames, atlas.getMipmapLevels() + 1);
            return false;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load atlas texture " + file, e);
        }
    }
}
