package com.dhj.actinium.render;

import com.dhj.actinium.mixin.vintage.core.terrain.AccessorEntityRenderer;
import com.dhj.actinium.render.WeatherRenderDiagnostics.Path;
import com.dhj.actinium.runtime.ActiniumRuntime;
import com.gtnewhorizon.gtnhlib.compat.Mods;
import com.gtnewhorizons.angelica.api.weather.WeatherColumnProvider;
import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMQueries;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.ffp.Instancing;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.ffp.VAOManager;
import com.gtnewhorizons.angelica.glsm.ffp.WeatherInstancedAttribs;
import com.gtnewhorizons.angelica.glsm.ffp.WeatherParams;
import com.gtnewhorizons.angelica.glsm.ffp.WeatherQuadMesh;
import net.coderbot.iris.Iris;
import net.coderbot.iris.apiimpl.IrisApiV0Impl;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.IRenderHandler;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Draws vanilla precipitation and Forge provider layers inside the existing world weather pass. */
public final class WeatherRenderer {
    private static final ResourceLocation RAIN = new ResourceLocation("textures/environment/rain.png");
    private static final ResourceLocation SNOW = new ResourceLocation("textures/environment/snow.png");
    private static final int REBUILD_TICKS = 20;
    private static final int STRIDE = WeatherInstancedAttribs.STRIDE;
    private static final WeatherColumnBatch CUSTOM_COLUMNS = new WeatherColumnBatch();
    private static final InstanceBuffer VANILLA = new InstanceBuffer();
    private static final InstanceBuffer CUSTOM = new InstanceBuffer();

    private static WeatherColumns columns = new WeatherColumns();
    private static WeakReference<WorldClient> sampledWorld = new WeakReference<>(null);
    private static WeatherSampler sampler;
    private static BuildKey vanillaKey;
    private static ProviderFrame providerFrame;
    private static int rainCount;
    private static int vanillaCount;

    private WeatherRenderer() {}

    /** Starts a world frame before Minecraft renders either eye. */
    public static void beginFrame() {
        if (!RenderWorldRecursionGuard.isNested()) {
            providerFrame = null;
        }
    }

    /** Returns whether Actinium handled this pass. Forge runs its original handler when this returns false. */
    public static boolean render(float partialTicks) {
        if (!ActiniumRuntime.options().advanced.useInstancedWeather) {
            return useOriginalRenderer(Path.OPTION_DISABLED);
        }
        if (RenderWorldRecursionGuard.isNested()) {
            return useOriginalRenderer(Path.NESTED_WORLD);
        }
        if (Mods.isModPresent("dsurround")) {
            return useOriginalRenderer(Path.DYNAMIC_SURROUNDINGS);
        }
        if (!BackendManager.RENDER_BACKEND.supportsInstancedDrawing()) {
            return useOriginalRenderer(Path.UNSUPPORTED_BACKEND);
        }

        Minecraft mc = Minecraft.getMinecraft();
        WorldClient world = mc.world;
        IRenderHandler handler = world.provider.getWeatherRenderer();
        WeatherColumnProvider provider = handler instanceof WeatherColumnProvider weather ? weather : null;
        Entity view = mc.getRenderViewEntity();
        if (handler != null && (provider == null || !(view instanceof EntityLivingBase))) {
            return useOriginalRenderer(Path.FORGE_HANDLER);
        }

        DeferredWorldRenderingPipeline pipeline = null;
        if (Iris.enabled && IrisApiV0Impl.INSTANCE.isShaderPackInUse()) {
            if (!(Iris.getPipelineManager().getPipelineNullable() instanceof DeferredWorldRenderingPipeline deferred)
                    || !deferred.supportsInstancing(Instancing.WEATHER)) {
                return useOriginalRenderer(Path.IRIS_VARIANT_UNAVAILABLE);
            }
            pipeline = deferred;
        } else if (!ShaderManager.isEnabled() || GLSMPrograms.getActiveProgram() != 0) {
            return useOriginalRenderer(Path.FFP_UNAVAILABLE);
        }

        float strength = provider == null || provider.rendersVanillaWeather() ? world.getRainStrength(partialTicks) : 0.0F;
        if (strength <= 0.0F && provider == null) {
            WeatherRenderDiagnostics.recordPass(Path.DRY);
            return true;
        }

        int vao = GLSMVertexInput.getBoundVAO();
        int arrayBuffer = GLSMQueries.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        VAOManager vaos = GLStateManager.getVaoManager();
        int flags = vaos.getVertexFlags();
        GLSMAttribStack.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        try {
            mc.entityRenderer.enableLightmap();
            if (pipeline != null) {
                pipeline.rebindCurrentPass();
                if (!pipeline.hasInstancedVariant(Instancing.WEATHER)) {
                    return useOriginalRenderer(Path.IRIS_VARIANT_UNAVAILABLE);
                }
            }

            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.disableCull();
            GlStateManager.glNormal3f(0.0F, 1.0F, 0.0F);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

            draw(mc, world, view, provider, pipeline, partialTicks, strength);
            WeatherRenderDiagnostics.recordPass(pipeline == null ? Path.FFP : Path.IRIS);
            return true;
        } finally {
            try {
                if (pipeline == null) {
                    GLSMPrograms.setFfpInstancing(Instancing.NONE);
                }
                GLSMVertexInput.glBindVertexArray(vao);
                vaos.setVertexFlags(flags);
                GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
                mc.entityRenderer.disableLightmap();
            } finally {
                GLSMAttribStack.glPopAttrib();
                if (pipeline != null) {
                    pipeline.rebindCurrentPass();
                }
            }
        }
    }

    private static boolean useOriginalRenderer(Path path) {
        WeatherRenderDiagnostics.recordPass(path);
        return false;
    }

    private static void draw(Minecraft mc, WorldClient world, Entity view, WeatherColumnProvider provider,
                             DeferredWorldRenderingPipeline pipeline, float partialTicks, float strength) {
        int x = MathHelper.floor(view.posX);
        int y = MathHelper.floor(view.posY);
        int z = MathHelper.floor(view.posZ);
        double cameraX = view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks;
        double cameraY = view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks;
        double cameraZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks;
        int tick = ((AccessorEntityRenderer) mc.entityRenderer).celeritas$getRendererUpdateCount();
        int radius = ActiniumRuntime.options().quality.weatherQuality.isFancy(mc.gameSettings.fancyGraphics) ? 10 : 5;

        if (sampledWorld.get() != world) {
            invalidateColumns();
            sampledWorld = new WeakReference<>(world);
            sampler = new WorldClientWeatherSamplerImpl(world);
        }
        if (strength > 0.0F) {
            updateVanilla(world, x, y, z, MathHelper.floor(cameraY), radius, tick);
        }

        int providerTick = 0;
        float providerInvRadius = 0.0F;
        if (provider != null) {
            if (providerFrame == null || providerFrame.provider() != provider || providerFrame.view() != view) {
                providerFrame = new ProviderFrame(provider, view, provider.beginFrame(world, (EntityLivingBase) view, partialTicks));
            }
            providerTick = providerFrame.tick();
            float fadeRadius = provider.fadeRadius();
            if (!Float.isFinite(fadeRadius) || fadeRadius <= 0.0F) {
                throw new IllegalArgumentException("Weather fade radius must be finite and positive");
            }
            providerInvRadius = 1.0F / fadeRadius;
            if (CUSTOM_COLUMNS.update(provider, provider.columnRevision(), world.provider.getDimension(), x, z)) {
                CUSTOM.upload(CUSTOM_COLUMNS.bytes());
            }
        } else {
            CUSTOM_COLUMNS.clear();
        }

        WeatherParams.set((float) (x - cameraX), (float) -cameraY, (float) (z - cameraZ), 1.0F / radius,
            (float) (view.posX - x), (float) (view.posZ - z), partialTicks, tick + partialTicks, tick, strength,
            providerTick, providerTick + partialTicks, providerInvRadius);

        if (strength > 0.0F) {
            drawRange(mc, pipeline, VANILLA, RAIN, 0, rainCount);
            drawRange(mc, pipeline, VANILLA, SNOW, rainCount, vanillaCount - rainCount);
        }
        var layers = CUSTOM_COLUMNS.layers();
        for (int i = 0; i < layers.size(); i++) {
            var layer = layers.get(i);
            drawRange(mc, pipeline, CUSTOM, layer.texture(), layer.first(), CUSTOM_COLUMNS.endOfLayer(i) - layer.first());
        }
    }

    private static void updateVanilla(WorldClient world, int x, int y, int z, int cameraY, int radius, int tick) {
        columns.update(sampler, x, z, radius, tick);
        BuildKey key = new BuildKey(columns.revision(), x, y, z, cameraY, radius, tick);
        if (vanillaKey != null && vanillaKey.matches(key)) {
            return;
        }

        int diameter = radius * 2 + 1;
        ByteBuffer data = VANILLA.staging(diameter * diameter * STRIDE);
        WeatherInstanceWriter writer = new WeatherInstanceWriter(columns, world.getBiomeProvider()::getTemperatureAtHeight);
        rainCount = writer.write(data, sampler, x, y, z, cameraY, radius, true, 0);
        vanillaCount = writer.write(data, sampler, x, y, z, cameraY, radius, false, rainCount);
        VANILLA.upload(data.limit(vanillaCount * STRIDE));
        vanillaKey = key;
    }

    private static void drawRange(Minecraft mc, DeferredWorldRenderingPipeline pipeline, InstanceBuffer instances,
                                  ResourceLocation texture, int first, int count) {
        if (count == 0) {
            return;
        }
        mc.getTextureManager().bindTexture(texture);
        if (pipeline != null) {
            pipeline.bindInstancedVariant(Instancing.WEATHER);
        } else {
            GLSMPrograms.setFfpInstancing(Instancing.WEATHER);
        }

        GLSMVertexInput.glBindVertexArray(WeatherQuadMesh.vao());
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, instances.id);
        WeatherInstancedAttribs.pointInstanceAttribs((long) first * STRIDE);
        GLStateManager.getVaoManager().setVertexFlags(WeatherQuadMesh.VERTEX_FLAGS);
        GLSMDraws.glDrawArraysInstanced(GL11.GL_TRIANGLE_STRIP, 0, WeatherQuadMesh.VERTEX_COUNT, count);
        WeatherRenderDiagnostics.recordDraw(instances == CUSTOM, count);
    }

    /** Releases render resources and invalidates the column caches when the client world changes. */
    public static void clear() {
        WeatherRenderDiagnostics.endWorld();
        WeatherQuadMesh.delete();
        VANILLA.release();
        CUSTOM.release();
        invalidateColumns();
    }

    private static void invalidateColumns() {
        CUSTOM_COLUMNS.clear();
        columns = new WeatherColumns();
        sampledWorld.clear();
        sampler = null;
        vanillaKey = null;
        providerFrame = null;
    }

    private record ProviderFrame(WeatherColumnProvider provider, Entity view, int tick) {}

    private record BuildKey(int revision, int x, int y, int z, int cameraY, int radius, int tick) {
        boolean matches(BuildKey current) {
            return revision == current.revision && x == current.x && y == current.y && z == current.z
                && cameraY == current.cameraY && radius == current.radius && current.tick - tick < REBUILD_TICKS;
        }
    }

    private static final class InstanceBuffer {
        private int id;
        private int capacity;
        private ByteBuffer staging;

        ByteBuffer staging(int bytes) {
            if (staging == null || staging.capacity() < bytes) {
                staging = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
            }
            return staging.clear();
        }

        void upload(ByteBuffer data) {
            if (!data.hasRemaining()) {
                return;
            }
            if (id == 0) {
                id = GLSMBuffers.glGenBuffers();
            }
            GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, id);
            if (capacity < data.remaining()) {
                capacity = data.capacity();
                GLSMBuffers.glBufferData(GL15.GL_ARRAY_BUFFER, (long) capacity, GL15.GL_DYNAMIC_DRAW);
            }
            GLSMBuffers.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, data);
        }

        void release() {
            if (id != 0) {
                GLSMBuffers.glDeleteBuffers(id);
                id = 0;
            }
            capacity = 0;
            staging = null;
        }
    }
}
