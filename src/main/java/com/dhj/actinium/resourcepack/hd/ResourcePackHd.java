package com.dhj.actinium.resourcepack.hd;

import com.dhj.actinium.resourcepack.ResourcePackIndex;
import com.dhj.actinium.resourcepack.ResourcePackReload;
import com.dhj.actinium.resourcepack.hd.mixinterface.PackMipmappedTexture;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/** Loads independent texture animations, mipmap rules, HD fonts and generated item atlas frames. */
public final class ResourcePackHd {
    private static final Logger LOGGER = LogManager.getLogger("Actinium/ExtendedHD");
    private static ResourcePackReload.Handle<Snapshot> handle;
    private static Snapshot animationSnapshot;
    private static List<Playback> playback = List.of();
    private static IntBuffer upload = BufferUtils.createIntBuffer(0);

    private ResourcePackHd() {}

    public static void register() { handle = ResourcePackReload.register(new Module()); }

    private static Snapshot snapshot() { return handle == null ? null : handle.get(); }

    /** Resolves the mip chain size before a named PNG is uploaded. */
    public static int standaloneMipmapLevels(ResourceLocation path, int width, int height) {
        Snapshot prepared = handle == null ? null : handle.getDuringReload();
        if (prepared == null || prepared.mipmaps == null || !prepared.mipmaps.textureEnabled(path)) return 0;
        return HdMipmapRules.imageLevels(width, height, Minecraft.getMinecraft().gameSettings.mipmapLevels);
    }

    /** Tests whether an upcoming or active animation needs the named texture's CPU pixels. */
    public static boolean hasAnimationTarget(ResourceLocation path) {
        Snapshot prepared = handle == null ? null : handle.getDuringReload();
        return prepared != null && prepared.animationTargets.contains(path);
    }

    /** Tests whether the current pack disables mipmaps on the combined block and item atlas. */
    public static boolean disablesAtlasMipmaps(TextureMap atlas) {
        Snapshot prepared = handle == null ? null : handle.getDuringReload();
        return prepared != null && prepared.mipmaps != null && atlas == Minecraft.getMinecraft().getTextureMapBlocks()
            && !prepared.mipmaps.atlasEnabled();
    }

    public static void tick(TextureManager manager) {
        Snapshot current = snapshot();
        if (animationSnapshot != current) {
            animationSnapshot = current;
            playback = current == null ? List.of() : current.animations.stream().map(Playback::new).toList();
        }
        if (playback.isEmpty()) return;
        GLStateManager.pushState(GL11.GL_TEXTURE_BIT);
        try {
            for (Playback state : playback) state.tick(manager);
        } finally {
            GLStateManager.popState();
        }
    }

    public static ResourceLocation fontTexture(ResourceLocation original) {
        Font font = font(original);
        return font == null ? original : font.texture;
    }

    /** Selects the PNG used to compute font metrics inside the reload listener. */
    public static ResourceLocation fontTextureDuringReload(ResourceLocation original) {
        Snapshot prepared = handle == null ? null : handle.getDuringReload();
        Font font = font(prepared, original);
        return font == null ? original : font.texture;
    }

    public static float fontWidth(ResourceLocation original, int index, float vanilla) {
        Font font = font(original);
        return font == null ? vanilla : font.widths.getOrDefault(index, vanilla);
    }

    public static boolean fontBlend(ResourceLocation original) {
        Font font = font(original);
        return font != null && font.blend;
    }

    private static Font font(ResourceLocation original) {
        return font(snapshot(), original);
    }

    private static Font font(Snapshot snapshot, ResourceLocation original) {
        return snapshot == null ? null : snapshot.fonts.get(original);
    }

    private static BufferedImage image(ResourcePackIndex index, ResourceLocation id) throws IOException {
        try (var stream = index.open(id)) {
            BufferedImage image = ImageIO.read(stream);
            if (image == null) throw new IOException("Invalid PNG " + id);
            return image;
        }
    }

    private static ResourceLocation path(ResourceLocation config, String path) {
        if (path == null || path.isBlank()) throw new IllegalArgumentException("Missing texture path");
        String value = path.trim().replaceFirst("^/", "");
        if (value.startsWith("./")) value = config.getPath().substring(0, config.getPath().lastIndexOf('/') + 1) + value.substring(2);
        if (value.startsWith("~/")) value = config.getPath().substring(0, config.getPath().indexOf('/') + 1) + value.substring(2);
        return value.contains(":") ? new ResourceLocation(value) : new ResourceLocation(config.getNamespace(), value);
    }

    private record Animation(ResourceLocation target, HdAnimation frames) {}
    private record Font(ResourceLocation texture, Map<Integer, Float> widths, boolean blend) {}
    private record Snapshot(List<Animation> animations, Set<ResourceLocation> animationTargets,
                            Map<ResourceLocation, Font> fonts, Map<String, HdDial> dials, HdMipmapRules mipmaps) {}

    private static final class Playback {
        private final Animation animation;
        private final HdAnimation.Cursor cursor;
        private ITextureObject texture;
        private int textureId;
        private Object uploadIdentity;

        private Playback(Animation animation) {
            this.animation = animation;
            this.cursor = animation.frames.cursor();
        }

        private void tick(TextureManager manager) {
            ITextureObject current = manager.getTexture(animation.target);
            if (current == null) {
                texture = null;
                uploadIdentity = null;
                return;
            }
            int id = current.getGlTextureId();
            var animatedImage = current instanceof PackMipmappedTexture mipped ? mipped.actinium$getAnimatedImage() : null;
            Object identity = animatedImage == null ? null : animatedImage.uploadIdentity;
            if (current != texture || id != textureId || identity != uploadIdentity) {
                texture = current;
                textureId = id;
                uploadIdentity = identity;
                cursor.reset();
            }
            int[] pixels = cursor.tick();
            if (pixels == null) return;
            if (upload.capacity() < pixels.length) upload = BufferUtils.createIntBuffer(pixels.length);
            manager.bindTexture(animation.target);
            HdMipmapTextures.uploadAnimation(animation.frames, pixels, upload, animatedImage);
        }
    }

    private static final class Module implements ResourcePackReload.Module<Snapshot, Snapshot> {
        @Override public String option() { return "extendedTextures"; }

        @Override
        public Snapshot prepare(ResourcePackIndex index) {
            Comparator<ResourceLocation> precedence = Comparator.<ResourceLocation>comparingInt(index::priority)
                .thenComparing(id -> id.getPath().startsWith("optifine/"))
                .thenComparing(ResourceLocation::toString);
            List<ResourceLocation> configurations = new ArrayList<>();
            for (ResourceLocation id : index.find(id -> id.getPath().matches("(?:optifine|mcpatcher)/anim/.+\\.properties"))) configurations.add(id);
            configurations.sort(precedence.reversed());
            List<Animation> animations = new ArrayList<>();
            for (ResourceLocation file : configurations) {
                try {
                    Properties p = index.readProperties(file);
                    ResourceLocation target = path(file, p.getProperty("to"));
                    if (index.priority(target) > index.priority(file)) continue;
                    HdAnimation animation = HdAnimation.parse(p, image(index, path(file, p.getProperty("from"))), image(index, target));
                    if (animations.stream().noneMatch(a -> a.target.equals(target) && a.frames.overlaps(animation))) {
                        animations.add(new Animation(target, animation));
                    }
                } catch (IOException | IllegalArgumentException e) {
                    warn(index, file, e);
                }
            }

            Map<ResourceLocation, Font> fonts = new HashMap<>();
            for (ResourceLocation file : index.find(id -> id.getPath().matches("(?:optifine|mcpatcher|textures)/font/.+\\.(?:png|properties)"))) {
                String name = file.getPath().substring(file.getPath().indexOf('/') + 1).replaceFirst("\\.[^.]+$", "");
                ResourceLocation vanilla = new ResourceLocation(file.getNamespace(), "textures/" + name + ".png");
                if (fonts.containsKey(vanilla)) continue;
                ResourceLocation texture = select(index, precedence, vanilla, "optifine/" + name + ".png", "mcpatcher/" + name + ".png");
                if (texture == null) continue;
                ResourceLocation config = select(index, precedence, null, "optifine/" + name + ".properties", "mcpatcher/" + name + ".properties",
                    "textures/" + name + ".properties");
                boolean hasConfig = config != null && index.priority(config) >= index.priority(texture);
                Map<Integer, Float> widths = new HashMap<>();
                boolean blend = false;
                if (!name.startsWith("font/unicode_page_")) {
                    try {
                        BufferedImage bitmap = image(index, texture);
                        if (texture.equals(vanilla) && !hasConfig && bitmap.getWidth() <= 128 && bitmap.getHeight() <= 128) continue;
                        widths.putAll(HdFontMetrics.measure(bitmap));
                    } catch (IOException | IllegalArgumentException e) {
                        warn(index, texture, e);
                        continue;
                    }
                } else if (texture.equals(vanilla) && !hasConfig) {
                    continue;
                }
                if (hasConfig) {
                    try {
                        Properties p = index.readProperties(config);
                        for (String key : p.stringPropertyNames()) {
                            if (!key.startsWith("width.")) continue;
                            try {
                                int glyph = Integer.parseInt(key.substring(6));
                                float width = Float.parseFloat(p.getProperty(key));
                                if (glyph < 0 || glyph >= 256 || !Float.isFinite(width) || width < 0 || width > 8) {
                                    throw new IllegalArgumentException("Invalid font width " + key);
                                }
                                widths.put(glyph, width);
                            } catch (IllegalArgumentException e) {
                                warn(index, config, e);
                            }
                        }
                        blend = Boolean.parseBoolean(p.getProperty("blend"));
                    } catch (IOException | IllegalArgumentException e) {
                        warn(index, config, e);
                    }
                }
                fonts.put(vanilla, new Font(texture, Map.copyOf(widths), blend));
            }

            Map<String, HdDial> dials = new HashMap<>();
            for (String dial : List.of("clock", "compass")) {
                ResourceLocation file = select(index, precedence, null, "optifine/dial/" + dial + ".properties", "mcpatcher/dial/" + dial + ".properties");
                if (file == null) continue;
                try {
                    dials.put(dial, HdDial.parse(index.readProperties(file), value -> image(index, path(file, value))));
                } catch (IOException | IllegalArgumentException e) {
                    warn(index, file, e);
                }
            }
            Set<ResourceLocation> animationTargets = animations.stream().map(Animation::target).collect(Collectors.toUnmodifiableSet());
            return new Snapshot(List.copyOf(animations), animationTargets, Map.copyOf(fonts), Map.copyOf(dials), HdMipmapRules.load(index, LOGGER));
        }

        @Override
        public void registerSprites(Snapshot prepared, TextureMap atlas) {
            prepared.dials.forEach((name, dial) -> {
                int count = name.equals("clock") ? 64 : 32;
                for (int i = 0; i < count; i++) {
                    String id = "minecraft:items/" + name + "_" + (i < 10 ? "0" : "") + i;
                    if (!atlas.setTextureEntry(new DialSprite(id, dial, 360.0 * i / count))) {
                        LOGGER.warn("Cannot replace already registered dial sprite {}", id);
                    }
                }
            });
        }

        @Override public Snapshot finish(Snapshot prepared, ResourcePackIndex index, TextureMap atlas) { return prepared; }

        private static ResourceLocation select(ResourcePackIndex index, Comparator<ResourceLocation> precedence,
                                               ResourceLocation vanilla, String... alternatives) {
            String namespace = vanilla == null ? "minecraft" : vanilla.getNamespace();
            ResourceLocation chosen = vanilla != null && index.exists(vanilla) ? vanilla : null;
            for (String value : alternatives) {
                ResourceLocation id = new ResourceLocation(namespace, value);
                if (index.exists(id) && (chosen == null || precedence.compare(id, chosen) >= 0)) chosen = id;
            }
            return chosen;
        }
    }

    private static void warn(ResourcePackIndex index, ResourceLocation file, Exception e) {
        LOGGER.warn("Skipping data in {} from {}: {}", file, index.packName(file), e.getMessage());
    }
}
