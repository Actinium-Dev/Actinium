package com.dhj.actinium.gui.svg;

import com.dhj.actinium.runtime.ActiniumRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.io.IOException;

final class SvgIconImpl extends AbstractTexture implements SvgIcon {
    private final ResourceLocation resource;
    private SvgImage image;
    private int pixelWidth;
    private int pixelHeight;

    SvgIconImpl(ResourceLocation resource) {
        this.resource = resource;
    }

    @Override
    public @Nullable ResourceLocation texture(int pixelWidth, int pixelHeight) {
        if (pixelWidth <= 0 || pixelHeight <= 0) {
            throw new IllegalArgumentException("SVG texture dimensions must be positive");
        }
        boolean resized = this.pixelWidth != pixelWidth || this.pixelHeight != pixelHeight;
        this.pixelWidth = pixelWidth;
        this.pixelHeight = pixelHeight;

        TextureManager textures = Minecraft.getMinecraft().getTextureManager();
        if (textures.getTexture(this.resource) != this) {
            textures.loadTexture(this.resource, this);
        } else if (resized && this.image != null) {
            this.upload();
        }
        return this.image == null ? null : this.resource;
    }

    @Override
    public void loadTexture(IResourceManager resources) {
        this.deleteGlTexture();
        this.image = null;
        SvgImage loadedImage;
        try (IResource input = resources.getResource(this.resource)) {
            loadedImage = SvgImage.load(input.getInputStream());
        } catch (IOException exception) {
            // Retain this registered texture so the next resource reload can retry it.
            ActiniumRuntime.logger().error("Failed to load SVG icon {}", this.resource, exception);
            return;
        }
        this.image = loadedImage;
        this.upload();
    }

    private void upload() {
        BufferedImage raster = this.image.rasterize(this.pixelWidth, this.pixelHeight);
        this.deleteGlTexture();
        TextureUtil.uploadTextureImageAllocate(this.getGlTextureId(), raster, false, true);
    }
}
