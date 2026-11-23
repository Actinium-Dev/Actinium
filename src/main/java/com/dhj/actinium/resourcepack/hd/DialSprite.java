package com.dhj.actinium.resourcepack.hd;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.util.Collections;
import java.util.function.Function;

final class DialSprite extends TextureAtlasSprite {
    private final HdDial dial;
    private final double angle;

    DialSprite(String id, HdDial dial, double angle) {
        super(id);
        this.dial = dial;
        this.angle = angle;
    }

    @Override
    public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
        return true;
    }

    @Override
    public boolean load(IResourceManager manager, ResourceLocation location, Function<ResourceLocation, TextureAtlasSprite> getter) {
        int w = dial.layers().getFirst().image().getWidth();
        int h = dial.layers().getFirst().image().getHeight();
        setIconWidth(w);
        setIconHeight(h);
        setFramesTextureData(Collections.singletonList(new int[][]{dial.render(w, h, angle)}));
        return false;
    }
}
