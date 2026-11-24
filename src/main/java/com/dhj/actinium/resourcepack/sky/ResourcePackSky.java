package com.dhj.actinium.resourcepack.sky;

import com.dhj.actinium.resourcepack.ResourcePackIndex;
import com.dhj.actinium.resourcepack.ResourcePackReload;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/** Loads resource-pack skies independently of the vanilla sky geometry. */
public final class ResourcePackSky {
    private static final Logger LOGGER = LogManager.getLogger("Actinium/Sky");
    private static final Pattern FILE = Pattern.compile("(?:optifine|mcpatcher)/sky/world(-?\\d+)/(sky(?:\\d+)?|sun|moon_phases)\\.properties");
    private static final ResourceLocation OLD_PARTICLES = new ResourceLocation("mcpatcher/particle.properties");
    private static final ResourceLocation PARTICLES = new ResourceLocation("optifine/particle.properties");
    private static final int STATE = GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT
        | GL11.GL_TEXTURE_BIT | GL11.GL_DEPTH_BUFFER_BIT;
    private static ResourcePackReload.Handle<Snapshot> handle;
    private static final Map<Layer, Visibility> visibility = new IdentityHashMap<>();
    private static WorldClient lastWorld;
    private static Snapshot lastSnapshot;

    private ResourcePackSky() {}

    public static void register() {
        handle = ResourcePackReload.register(new Module());
    }

    public static void render(float partialTicks) {
        Dimension sky = currentDimension();
        if (sky == null || sky.layers.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        Entity camera = mc.getRenderViewEntity();
        if (camera == null) return;
        Snapshot snapshot = handle.get();
        if (lastWorld != mc.world || lastSnapshot != snapshot) {
            visibility.clear();
            lastWorld = mc.world;
            lastSnapshot = snapshot;
        }
        BlockPos pos = new BlockPos(camera.getPositionEyes(partialTicks));
        WorldRenderingPipeline pipeline = Iris.enabled ? Iris.getPipelineManager().getPipelineNullable() : null;
        WorldRenderingPhase prior = pipeline == null ? null : pipeline.getPhase();
        GLStateManager.pushState(STATE);
        try {
            if (pipeline != null) pipeline.setPhase(WorldRenderingPhase.CUSTOM_SKY);
            GlStateManager.enableTexture2D();
            GlStateManager.disableFog();
            GlStateManager.disableCull();
            GlStateManager.depthMask(false);
            GLStateManager.disableDepthTest();
            for (Layer layer : sky.layers) {
                float brightness = layer.brightness(mc.world, pos, partialTicks,
                    visibility.computeIfAbsent(layer, ignored -> new Visibility()));
                if (brightness <= 0) continue;
                mc.getTextureManager().bindTexture(layer.texture);
                layer.blend.apply(brightness);
                drawLayer(layer, mc.world, partialTicks);
            }
        } finally {
            try {
                GLStateManager.popState();
            } finally {
                if (pipeline != null) pipeline.setPhase(prior);
            }
        }
    }

    public static ResourceLocation celestialTexture(ResourceLocation original, boolean moon) {
        Layer layer = celestial(moon);
        return layer == null ? original : layer.texture;
    }

    public static void drawCelestial(boolean moon, Runnable draw) {
        Layer layer = celestial(moon);
        if (layer == null) {
            draw.run();
            return;
        }
        GLStateManager.pushState(STATE);
        try {
            float partialTicks = Minecraft.getMinecraft().getRenderPartialTicks();
            layer.blend.apply(1 - Minecraft.getMinecraft().world.getRainStrength(partialTicks));
            draw.run();
        } finally {
            GLStateManager.popState();
        }
    }

    public static boolean hasFireworkBlend() {
        Snapshot snapshot = handle == null ? null : handle.get();
        return snapshot != null && snapshot.fireworks != null;
    }

    public static void applyFireworkBlend() {
        handle.get().fireworks.apply(1);
    }

    private static Layer celestial(boolean moon) {
        Dimension dimension = currentDimension();
        return dimension == null ? null : moon ? dimension.moon : dimension.sun;
    }

    private static Dimension currentDimension() {
        Snapshot snapshot = handle == null ? null : handle.get();
        WorldClient world = Minecraft.getMinecraft().world;
        return snapshot == null || world == null ? null : snapshot.dimensions.get(world.provider.getDimension());
    }

    private static void drawLayer(Layer layer, WorldClient world, float partialTicks) {
        GlStateManager.pushMatrix();
        try {
            if (layer.rotate) {
                double phase = world.getCelestialAngle(partialTicks) + Math.floorDiv(world.getWorldTime(), SkyFade.DAY);
                GlStateManager.rotate((float) ((phase * layer.speed % 1) * 360), layer.axisX, layer.axisY, layer.axisZ);
            }
            GlStateManager.rotate(90, 1, 0, 0);
            GlStateManager.rotate(-90, 0, 0, 1);
            tile(4);
            cap(90, 1);
            cap(-90, 0);
            for (int face : new int[]{5, 2, 3}) {
                GlStateManager.rotate(90, 0, 0, 1);
                tile(face);
            }
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private static void cap(float angle, int face) {
        GlStateManager.pushMatrix();
        try {
            GlStateManager.rotate(angle, 1, 0, 0);
            tile(face);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private static void tile(int face) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        double u = face % 3 / 3.0;
        double v = face / 3 / 2.0;
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(-100, -100, -100).tex(u, v).endVertex();
        buffer.pos(-100, -100, 100).tex(u, v + 0.5).endVertex();
        buffer.pos(100, -100, 100).tex(u + 1.0 / 3, v + 0.5).endVertex();
        buffer.pos(100, -100, -100).tex(u + 1.0 / 3, v).endVertex();
        tessellator.draw();
    }

    private static ResourceLocation resolveTexture(ResourceLocation file, String source) {
        if (source == null) return new ResourceLocation(file.getNamespace(), file.getPath().replace(".properties", ".png"));
        String value = source.trim();
        if (value.startsWith("./")) value = file.getPath().substring(0, file.getPath().lastIndexOf('/') + 1) + value.substring(2);
        if (value.startsWith("~/")) value = file.getPath().substring(0, file.getPath().indexOf('/') + 1) + value.substring(2);
        return value.contains(":") ? new ResourceLocation(value) : new ResourceLocation(file.getNamespace(), value.replaceFirst("^/", ""));
    }

    private record Snapshot(Map<Integer, Dimension> dimensions, SkyBlend fireworks) {}
    private record Dimension(List<Layer> layers, Layer sun, Layer moon) {}
    private record Slot(int dimension, String name) {}

    private record Layer(ResourceLocation texture, SkyBlend blend, SkyFade fade, boolean rotate, float speed,
                         float axisX, float axisY, float axisZ, Set<String> weather, List<String> biomes,
                         List<Range> heights, List<Range> days, int dayLoop, float transition) {
        static Layer parse(ResourcePackIndex index, ResourceLocation file, boolean celestial) throws IOException {
            Properties p = index.readProperties(file);
            ResourceLocation texture = resolveTexture(file, p.getProperty("source"));
            if (!index.exists(texture)) throw new IOException("Missing sky texture " + texture);
            String[] axis = p.getProperty("axis", "0 0 1").trim().split("\\s+");
            if (axis.length != 3) throw new IllegalArgumentException("Expected three rotation axis coordinates");
            float x = number(axis[2]), y = number(axis[1]), z = -number(axis[0]);
            if (x == 0 && y == 0 && z == 0) throw new IllegalArgumentException("Empty rotation axis");
            Set<String> weather = Set.copyOf(words(p.getProperty("weather", "clear")));
            if (weather.isEmpty() || !Set.of("clear", "rain", "thunder").containsAll(weather)) {
                throw new IllegalArgumentException("Invalid sky weather: " + weather);
            }
            int loop = Integer.parseInt(p.getProperty("daysLoop", "8"));
            if (loop <= 0) throw new IllegalArgumentException("daysLoop must be positive");
            float transition = number(p.getProperty("transition", "1"));
            if (transition < 0) throw new IllegalArgumentException("Negative sky transition time");
            return new Layer(texture, SkyBlend.parse(p.getProperty("blend", "add")), celestial ? null : SkyFade.parse(p),
                Boolean.parseBoolean(p.getProperty("rotate", "true")), number(p.getProperty("speed", "1")), x, y, z, weather,
                words(p.getProperty("biomes", "")), Range.parse(p.getProperty("heights", "")), Range.parse(p.getProperty("days", "")), loop, transition);
        }

        float brightness(WorldClient world, BlockPos pos, float partialTicks, Visibility visibility) {
            boolean allowed = Range.matches(heights, pos.getY());
            if (!biomes.isEmpty()) {
                ResourceLocation biome = world.getBiome(pos).getRegistryName();
                String path = biome == null ? "" : biome.getPath().replace("_", "");
                allowed &= biomes.stream().anyMatch(b -> b.equalsIgnoreCase(String.valueOf(biome)) || b.replace("_", "").equalsIgnoreCase(path));
            }
            float spatial = visibility.update(world.getTotalWorldTime() + (double) partialTicks, allowed, transition);
            long time = world.getWorldTime();
            long day = Math.floorDiv(time - fade.startIn(), SkyFade.DAY);
            if (!Range.matches(days, Math.floorMod(day, dayLoop))) return 0;
            float rain = world.getRainStrength(partialTicks);
            float thunder = MathHelper.clamp(world.getThunderStrength(partialTicks), 0, rain);
            float weight = (weather.contains("clear") ? 1 - rain : 0) + (weather.contains("rain") ? rain - thunder : 0)
                + (weather.contains("thunder") ? thunder : 0);
            return fade.brightness(time, partialTicks) * weight * spatial;
        }
    }

    private static final class Visibility {
        private double lastTick = Double.NaN;
        private float level;

        float update(double tick, boolean visible, float seconds) {
            float target = visible ? 1 : 0;
            if (Double.isNaN(lastTick) || seconds == 0 || tick < lastTick) {
                level = target;
            } else {
                float delta = (float) ((tick - lastTick) / (seconds * 20));
                level = visible ? Math.min(target, level + delta) : Math.max(target, level - delta);
            }
            lastTick = tick;
            return level;
        }
    }

    private static List<String> words(String value) {
        return value.isBlank() ? List.of() : List.of(value.trim().split("\\s+"));
    }

    private static float number(String value) {
        float number = Float.parseFloat(value);
        if (!Float.isFinite(number)) throw new IllegalArgumentException("Non-finite sky coordinate or speed");
        return number;
    }

    private record Range(int first, int last) {
        private static final Pattern SYNTAX = Pattern.compile("(-?\\d+)(?:-(-?\\d+))?");

        static List<Range> parse(String input) {
            return words(input).stream().map(word -> {
                var matcher = SYNTAX.matcher(word);
                if (!matcher.matches()) throw new IllegalArgumentException("Invalid sky range " + word);
                int first = Integer.parseInt(matcher.group(1));
                int last = matcher.group(2) == null ? first : Integer.parseInt(matcher.group(2));
                return new Range(Math.min(first, last), Math.max(first, last));
            }).toList();
        }

        static boolean matches(List<Range> ranges, int value) {
            return ranges.isEmpty() || ranges.stream().anyMatch(r -> value >= r.first && value <= r.last);
        }
    }

    private static final class Module implements ResourcePackReload.Module<Snapshot, Snapshot> {
        @Override public String option() { return "customSky"; }

        @Override
        public Snapshot prepare(ResourcePackIndex index) {
            Map<Slot, ResourceLocation> selected = new HashMap<>();
            if (!Loader.isModLoaded("neoforgeskyboxes")) {
                for (ResourceLocation file : index.find(id -> FILE.matcher(id.getPath()).matches())) {
                    var match = FILE.matcher(file.getPath());
                    if (!match.matches()) continue;
                    try {
                        selected.merge(new Slot(Integer.parseInt(match.group(1)), match.group(2)), file,
                            (left, right) -> precedence(index).compare(left, right) >= 0 ? left : right);
                    } catch (IllegalArgumentException e) {
                        warn(index, file, e);
                    }
                }
            }
            Map<Integer, List<Layer>> layers = new HashMap<>();
            Map<Integer, Layer> suns = new HashMap<>(), moons = new HashMap<>();
            Map<Integer, Dimension> dimensions = new HashMap<>();
            selected.entrySet().stream().sorted(Comparator.comparingInt(e -> order(e.getKey().name))).forEach(entry -> {
                Slot slot = entry.getKey();
                ResourceLocation file = entry.getValue();
                try {
                    Layer layer = Layer.parse(index, file, !slot.name.startsWith("sky"));
                    switch (slot.name) {
                        case "sun" -> suns.put(slot.dimension, layer);
                        case "moon_phases" -> moons.put(slot.dimension, layer);
                        default -> layers.computeIfAbsent(slot.dimension, ignored -> new ArrayList<>()).add(layer);
                    }
                    dimensions.put(slot.dimension, null);
                } catch (IOException | IllegalArgumentException e) {
                    warn(index, file, e);
                }
            });
            dimensions.replaceAll((id, unused) -> new Dimension(List.copyOf(layers.getOrDefault(id, List.of())), suns.get(id), moons.get(id)));
            ResourceLocation fireworks = !index.exists(OLD_PARTICLES) || index.exists(PARTICLES)
                && precedence(index).compare(PARTICLES, OLD_PARTICLES) >= 0 ? PARTICLES : OLD_PARTICLES;
            SkyBlend fireworkBlend = null;
            if (index.exists(fireworks)) {
                try {
                    fireworkBlend = SkyBlend.parse(index.readProperties(fireworks).getProperty("blend.4", "add"));
                } catch (IOException | IllegalArgumentException e) {
                    warn(index, fireworks, e);
                }
            }
            return new Snapshot(Map.copyOf(dimensions), fireworkBlend);
        }

        @Override public Snapshot finish(Snapshot prepared, ResourcePackIndex index, TextureMap atlas) { return prepared; }

        private static int order(String name) {
            if (name.length() <= 3 || !name.startsWith("sky")) return -1;
            String suffix = name.substring(3);
            return suffix.length() > 9 ? Integer.MAX_VALUE : Integer.parseInt(suffix);
        }

        private static Comparator<ResourceLocation> precedence(ResourcePackIndex index) {
            return Comparator.<ResourceLocation>comparingInt(index::priority)
                .thenComparing(id -> id.getPath().startsWith("optifine/"))
                .thenComparing(ResourceLocation::toString);
        }

        private static void warn(ResourcePackIndex index, ResourceLocation file, Exception e) {
            LOGGER.warn("Skipping {} from {}: {}", file, index.packName(file), e.getMessage());
        }
    }
}
