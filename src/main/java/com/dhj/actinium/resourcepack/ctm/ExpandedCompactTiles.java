package com.dhj.actinium.resourcepack.ctm;

import com.dhj.actinium.resourcepack.ResourcePackIndex;
import com.dhj.actinium.resourcepack.ctm.client.resource.ResourceRedirectHandler;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Composes the MCPatcher compact_expanded atlas entries before terrain workers use them. */
final class ExpandedCompactTiles {
    // MCPatcherForge's 47-tile CTM order; each entry describes the four source quadrants.
    // Derived from Angelica 996fb54c, CTMTextureGenerator (LGPL-3.0).
    private static final String[] QUADRANTS = {
            "0000", "0303", "3333", "3030", "0324", "3042", "2424", "3344", "4144", "4441", "1414", "1144",
            "0022", "0321", "3311", "3012", "2403", "4230", "4433", "4242", "1444", "4414", "4411", "4141",
            "2222", "2121", "1111", "1212", "2421", "3314", "2124", "3341", "1114", "1141", "4114", "1441",
            "2200", "2103", "1133", "1230", "4133", "1212", "1433", "4212", "1411", "4111", "4444"
    };

    private final ResourceLocation rule;
    private final BufferedImage[] inputs;
    private final AnimationMetadataSection animation;

    private ExpandedCompactTiles(ResourceLocation rule, BufferedImage[] inputs, @Nullable AnimationMetadataSection animation) {
        this.rule = rule;
        this.inputs = inputs;
        this.animation = animation;
    }

    static ExpandedCompactTiles read(ResourcePackIndex index, ResourceLocation rule, List<ResourceLocation> tiles) throws IOException {
        if (tiles.size() != 5) {
            throw new IllegalArgumentException("compact_expanded requires exactly 5 tiles");
        }
        var images = new BufferedImage[tiles.size()];
        AnimationMetadataSection animation = null;
        for (int i = 0; i < tiles.size(); i++) {
            ResourceLocation tile = tiles.get(i);
            ResourceLocation png = ResourceRedirectHandler.redirect(new ResourceLocation(tile.getNamespace(), "textures/" + tile.getPath() + ".png"));
            try (var resource = index.manager().getResource(png)) {
                if (i == 0) {
                    animation = resource.getMetadata("animation");
                }
                images[i] = ImageIO.read(resource.getInputStream());
            }
            if (images[i] == null || images[i].getWidth() < 2 || (images[i].getWidth() & 1) != 0
                    || images[i].getHeight() < images[i].getWidth() || images[i].getHeight() % images[i].getWidth() != 0
                    || images[i].getWidth() != images[0].getWidth() || images[i].getHeight() != images[0].getHeight()) {
                throw new IOException("Expanded CTM tiles must have identical dimensions and contain square frames: " + png);
            }
        }
        if (animation == null && images[0].getHeight() != images[0].getWidth()) {
            throw new IOException("Animated expanded CTM requires .png.mcmeta for the first tile");
        }
        return new ExpandedCompactTiles(rule, images, animation);
    }

    void register(TextureMap atlas) {
        int width = inputs[0].getWidth();
        for (int tile = 0; tile < QUADRANTS.length; tile++) {
            List<int[][]> frames = new ArrayList<>(inputs[0].getHeight() / width);
            for (int frame = 0; frame < inputs[0].getHeight() / width; frame++) {
                frames.add(new int[][]{compose(inputs, tile, frame)});
            }
            atlas.setTextureEntry(new Sprite(id(tile), width, frames, animation));
        }
    }

    static int[] compose(BufferedImage[] images, int tile, int frame) {
        int width = images[0].getWidth();
        int half = width / 2;
        int[] pixels = new int[width * width];
        for (int q = 0; q < 4; q++) {
            var image = images[QUADRANTS[tile].charAt(q) - '0'];
            int x = (q & 1) * half;
            int y = (q >>> 1) * half;
            image.getRGB(x, frame * width + y, half, half, pixels, y * width + x, width);
        }
        return pixels;
    }

    TextureAtlasSprite[] sprites(Function<ResourceLocation, TextureAtlasSprite> getSprite) {
        var sprites = new TextureAtlasSprite[QUADRANTS.length];
        for (int i = 0; i < sprites.length; i++) {
            sprites[i] = getSprite.apply(id(i));
        }
        return sprites;
    }

    private ResourceLocation id(int tile) {
        return new ResourceLocation(rule.getNamespace(), "actinium_ctm_generated/" + rule.getPath() + "/" + tile);
    }

    private static final class Sprite extends TextureAtlasSprite {
        private final int width;
        private final List<int[][]> frames;
        private final AnimationMetadataSection animation;

        private Sprite(ResourceLocation id, int width, List<int[][]> frames, @Nullable AnimationMetadataSection animation) {
            super(id.toString());
            this.width = width;
            this.frames = frames;
            this.animation = animation;
        }

        @Override
        public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
            return true;
        }

        @Override
        public boolean load(IResourceManager manager, ResourceLocation location, Function<ResourceLocation, TextureAtlasSprite> getter) {
            setIconWidth(width);
            setIconHeight(width);
            setFramesTextureData(frames);
            animationMetadata = animation;
            return false;
        }
    }
}
