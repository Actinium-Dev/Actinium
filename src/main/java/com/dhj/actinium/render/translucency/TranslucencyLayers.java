package com.dhj.actinium.render.translucency;

import com.dhj.actinium.render.RenderWorldRecursionGuard;
import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMQueries;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.StateSet;
import com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard;
import com.gtnewhorizons.angelica.glsm.stacks.ColorMaskStack;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import dhj.embeddedt.embeddium.impl.gl.shader.GlProgram;
import dhj.embeddedt.embeddium.impl.gl.shader.GlShader;
import dhj.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import dhj.embeddedt.embeddium.impl.gl.shader.ShaderType;
import dhj.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BooleanSupplier;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import net.coderbot.iris.gl.texture.DepthBufferFormat;
import net.coderbot.iris.pipeline.FixedFunctionWorldRenderingPipeline;
import net.coderbot.iris.rendertarget.IRenderTargetExt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;
import static com.mitchej123.lwjgl.LWJGLServiceProvider.LWJGL;

/** Captures translucent passes against the scene depth and composites premultiplied color back into the scene. */
public final class TranslucencyLayers {

    private static final int LAYERS = TranslucentLayer.values().length;
    private static final int FIRST_LAYER_UNIT = 1;
    private static final int TERRAIN_BEHIND_UNIT = FIRST_LAYER_UNIT + 2 * LAYERS;
    private static final int SPLIT_DEPTH_UNIT = TERRAIN_BEHIND_UNIT + 1;
    private static final int[] SINGLE_BUFFER = { 0 };
    private static final int[] SPLIT_BUFFERS = { 0, 1 };
    private static final int[] BEHIND_BUFFER = { 1 };

    public static final TranslucencyLayers INSTANCE = new TranslucencyLayers(new MinecraftTarget(), TranslucencyLayers::supportsLayeredPipeline);

    interface MainTarget {
        int framebuffer();
        int depthTexture();
        int width();
        int height();
        int depthVersion();
    }

    private final MainTarget main;
    private final BooleanSupplier supportsPipeline;
    private final Target[] layers = new Target[LAYERS];
    private Target depthSnapshot;
    private int targetFramebuffer;
    private int targetDepthTexture;
    private int width;
    private int height;
    private int depthVersion = -1;
    private DepthBufferFormat depthFormat;

    private long frameId;
    private int drawnMask;
    private TranslucentLayer open;
    private int savedDraw;
    private int savedRead;
    private final int[] savedViewport = new int[4];
    private boolean savedAlphaMask;
    private boolean savedLayerBlending;
    private boolean captureSuspended;

    private final Deque<TerrainScope> terrainScopes = new ArrayDeque<>();
    private final TextureBindings splitBinding = new TextureBindings(1);
    private final TextureBindings compositeBindings = new TextureBindings(TERRAIN_BEHIND_UNIT + 1);
    private boolean behindDrawn;

    private CompositeProgram program;
    private CompositeProgram behindProgram;
    private int vao;
    private StateSet prepareState;

    TranslucencyLayers(MainTarget main, BooleanSupplier supportsPipeline) {
        this.main = main;
        this.supportsPipeline = supportsPipeline;
    }

    /** Registers an outer world pass before any capture. Iris eligibility is checked when a layer is first requested. */
    public void beginFrame() {
        long passId = RenderWorldRecursionGuard.outerPassId();
        if (passId == 0 || frameId != 0) {
            throw new IllegalStateException("Translucency frame must begin once inside an outer world pass");
        }
        frameId = passId;
    }

    /** Releases any open capture and unregisters the frame, including when rendering fails. */
    public void endFrame() {
        try {
            while (!terrainScopes.isEmpty()) endTerrainSplit();
        } finally {
            try {
                if (open != null) end();
            } finally {
                frameId = 0;
                drawnMask = 0;
                behindDrawn = false;
            }
        }
    }

    public boolean active() {
        if (!isCurrentFrame()) return false;
        return supportsPipeline.getAsBoolean() && main.framebuffer() > 0 && main.depthTexture() > 0;
    }

    private static boolean supportsLayeredPipeline() {
        return !RENDER_BACKEND.isSDLGPU() && RENDER_BACKEND.hasContextOnThread() && !RenderSystem.isGLES()
            && LWJGL.isOpenGLVersionSupported(3, 3)
            && ClientProxy.options().performance.translucencyMode.composites() && Iris.enabled
            && Iris.getPipelineManager().getPipelineNullable() instanceof FixedFunctionWorldRenderingPipeline;
    }

    private boolean isCurrentFrame() {
        return frameId != 0 && frameId == RenderWorldRecursionGuard.outerPassId();
    }

    public void begin(TranslucentLayer layer) {
        if (!active() || open != null) throw new IllegalStateException("Cannot begin translucency layer " + layer);
        BatchStateGuard.beforeChange();
        savedDraw = GLSMFramebuffer.getDrawFramebuffer();
        savedRead = GLSMFramebuffer.getReadFramebuffer();
        GLSMQueries.glGetInteger(GL11.GL_VIEWPORT, savedViewport);
        savedAlphaMask = GLSMBlend.getColorMask().alpha;
        savedLayerBlending = GLSMBlend.isLayerBlending();
        try {
            syncSize();
            final Target target = layer(layer);
            final int bit = 1 << layer.ordinal();
            if ((drawnMask & bit) == 0) prepare(target);
            target.framebuffer.bind();
            GLSMBlend.setLayerBlending(true);
            restoreAlphaMask(true);
            drawnMask |= bit;
            open = layer;
        } finally {
            if (open == null) restoreCaptureState();
        }
    }

    public void end() {
        if (open == null) throw new IllegalStateException("No translucency layer is open");
        try {
            while (!terrainScopes.isEmpty()) endTerrainSplit();
        } finally {
            try {
                restoreCaptureState();
            } finally {
                open = null;
            }
        }
    }

    private void restoreCaptureState() {
        BatchStateGuard.beforeChange();
        restoreAlphaMask(savedAlphaMask);
        GLSMBlend.setLayerBlending(savedLayerBlending);
        bindFramebuffers(savedDraw, savedRead);
        GLSMRasterizer.glViewport(savedViewport[0], savedViewport[1], savedViewport[2], savedViewport[3]);
    }

    private static void restoreAlphaMask(boolean alpha) {
        final ColorMaskStack mask = GLSMBlend.getColorMask();
        GLSMBlend.glColorMask(mask.red, mask.green, mask.blue, alpha);
    }

    public boolean redirectsMainBind() {
        return isCurrentFrame() && open != null;
    }

    public void bindOpenLayer() {
        if (!redirectsMainBind()) throw new IllegalStateException("No translucency layer is open for this view");
        layers[open.ordinal()].framebuffer.bind();
    }

    public boolean isOpenLayer(TranslucentLayer layer) {
        return redirectsMainBind() && open == layer;
    }

    /** Keeps recursive world renders outside the outer view's capture. */
    public void renderNested(Runnable draw) {
        if (open == null || captureSuspended) {
            draw.run();
            return;
        }
        BatchStateGuard.beforeChange();
        final int layerFramebuffer = layers[open.ordinal()].framebuffer.getId();
        final int previousDraw = GLSMFramebuffer.getDrawFramebuffer();
        final int previousRead = GLSMFramebuffer.getReadFramebuffer();
        final int[] viewport = new int[4];
        GLSMQueries.glGetInteger(GL11.GL_VIEWPORT, viewport);
        final boolean previousAlphaMask = GLSMBlend.getColorMask().alpha;
        final boolean previousLayerBlending = GLSMBlend.isLayerBlending();
        captureSuspended = true;
        try {
            bindFramebuffers(previousDraw == layerFramebuffer ? savedDraw : previousDraw,
                previousRead == layerFramebuffer ? savedRead : previousRead);
            restoreAlphaMask(savedAlphaMask);
            GLSMBlend.setLayerBlending(savedLayerBlending);
            draw.run();
        } finally {
            try {
                BatchStateGuard.beforeChange();
                bindFramebuffers(previousDraw, previousRead);
                GLSMRasterizer.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
                restoreAlphaMask(previousAlphaMask);
                GLSMBlend.setLayerBlending(previousLayerBlending);
            } finally {
                captureSuspended = false;
            }
        }
    }

    /** Sets the uniforms on the bound terrain program, even when this terrain draw uses the main framebuffer. */
    public void beginTerrainSplit(int program) {
        final boolean layered = isOpenLayer(TranslucentLayer.TERRAIN);
        final boolean split = layered && (drawnMask & (1 << TranslucentLayer.BLOCK_ENTITIES.ordinal())) != 0;
        final TerrainScope scope = new TerrainScope(program, layered, split);
        if (split && splitBinding.isSaved()) throw new IllegalStateException("Terrain split is already open");
        terrainScopes.push(scope);
        try {
            if (split) prepareTerrainSplit(scope);
            scope.apply(layered, split);
        } catch (RuntimeException | Error failure) {
            try {
                endTerrainSplit();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private void prepareTerrainSplit(TerrainScope scope) {
        BatchStateGuard.beforeChange();
        final Target terrain = layers[TranslucentLayer.TERRAIN.ordinal()];
        splitBinding.save(0, SPLIT_DEPTH_UNIT);
        scope.splitBound = true;
        try {
            bindTexture(SPLIT_DEPTH_UNIT, layers[TranslucentLayer.BLOCK_ENTITIES.ordinal()].depth);
        } finally {
            splitBinding.restoreActiveUnit();
        }
        if (!behindDrawn) {
            terrain.attachBehind(width, height);
            terrain.framebuffer.drawBuffers(BEHIND_BUFFER);
            clearBehind(terrain);
            behindDrawn = true;
        }
        terrain.framebuffer.drawBuffers(SPLIT_BUFFERS);
        setUniform(scope.program, "u_SplitDepth", SPLIT_DEPTH_UNIT);
    }

    public void endTerrainSplit() {
        if (terrainScopes.isEmpty()) return;
        final TerrainScope scope = terrainScopes.pop();
        final int previousProgram = GLSMPrograms.getActiveProgram();
        try {
            GLSMPrograms.glUseProgram(scope.program);
            scope.apply(false, false);
            final TerrainScope parent = terrainScopes.peek();
            if (parent != null && parent.program == scope.program) parent.apply(parent.layered, parent.split);
        } finally {
            GLSMPrograms.glUseProgram(previousProgram);
            if (scope.splitBound) {
                try {
                    layers[TranslucentLayer.TERRAIN.ordinal()].framebuffer.drawBuffers(SINGLE_BUFFER);
                } finally {
                    splitBinding.restore(GLSMTextures.getActiveTextureUnit());
                }
            }
        }
    }

    private static final class TerrainScope {
        final int program;
        final boolean layered;
        final boolean split;
        boolean splitBound;

        TerrainScope(int program, boolean layered, boolean split) {
            this.program = program;
            this.layered = layered;
            this.split = split;
        }

        void apply(boolean layered, boolean split) {
            setUniform(program, "u_Layered", layered ? 1 : 0);
            setUniform(program, "u_SplitEnabled", split ? 1 : 0);
        }
    }

    public void composite() {
        if (!active() || drawnMask == 0) return;
        if (open != null) throw new IllegalStateException("Cannot composite an open translucency layer");
        BatchStateGuard.beforeChange();
        final int draw = GLSMFramebuffer.getDrawFramebuffer();
        final int read = GLSMFramebuffer.getReadFramebuffer();
        final int activeProgram = GLSMPrograms.getActiveProgram();
        final int activeVao = GLSMQueries.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        final boolean layerBlending = GLSMBlend.isLayerBlending();
        final int state = GLSMAttribStack.pushState(StateSet.forMask(GL11.GL_ALL_ATTRIB_BITS));
        try {
            syncSize();
            if (drawnMask == 0) return;
            final Target snapshot = depthSnapshot();
            final CompositeProgram composite = compositeProgram(behindDrawn);
            GLSMRasterizer.disableScissorTest();
            blit(targetFramebuffer, snapshot.framebuffer.getId(), GL11.GL_DEPTH_BUFFER_BIT);
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, targetFramebuffer);
            GLSMRasterizer.glViewport(0, 0, width, height);
            GLSMBlend.setLayerBlending(false);
            GLSMBlend.enableBlend();
            GLSMBlend.glBlendEquation(GL14.GL_FUNC_ADD);
            GLSMBlend.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
            GLSMBlend.glColorMask(true, true, true, false);
            GLSMRasterizer.disableCull();
            GLSMRasterizer.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
            GLSMEnable.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            GLSMEnable.glDisable(GL11.GL_COLOR_LOGIC_OP);
            GLSMEnable.glDisable(GL11.GL_STENCIL_TEST);
            GLSMDepthStencilAlpha.disableAlphaTest();
            GLSMDepthStencilAlpha.enableDepthTest();
            GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_ALWAYS);
            GLSMDepthStencilAlpha.glDepthMask(true);
            compositeBindings.save(0, 0);
            bindTexture(0, snapshot.depth);
            for (int i = 0; i < LAYERS; i++) {
                if ((drawnMask & (1 << i)) == 0) continue;
                final int colorUnit = FIRST_LAYER_UNIT + i;
                final int depthUnit = colorUnit + LAYERS;
                compositeBindings.save(colorUnit, colorUnit);
                compositeBindings.save(depthUnit, depthUnit);
                bindTexture(colorUnit, layers[i].color);
                bindTexture(depthUnit, layers[i].depth);
            }
            if (behindDrawn) {
                compositeBindings.save(TERRAIN_BEHIND_UNIT, TERRAIN_BEHIND_UNIT);
                bindTexture(TERRAIN_BEHIND_UNIT, layers[TranslucentLayer.TERRAIN.ordinal()].behind);
            }
            GLSMPrograms.glUseProgram(composite.program.handle());
            GLSMPrograms.glUniform1i(composite.maskUniform, drawnMask);
            GLSMVertexInput.glBindVertexArray(vao);
            GLSMDraws.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        } finally {
            compositeBindings.restore();
            GLSMVertexInput.glBindVertexArray(activeVao);
            GLSMPrograms.glUseProgram(activeProgram);
            bindFramebuffers(draw, read);
            GLSMBlend.setLayerBlending(layerBlending);
            GLSMAttribStack.popStateTo(state);
            drawnMask = 0;
            behindDrawn = false;
        }
    }

    public void destroy() {
        endFrame();
        destroyTargets();
        if (program != null) program.program.delete();
        if (behindProgram != null) behindProgram.program.delete();
        if (vao != 0) GLSMVertexInput.glDeleteVertexArrays(vao);
        program = null;
        behindProgram = null;
        vao = 0;
    }

    private CompositeProgram compositeProgram(boolean behind) {
        if (vao == 0) vao = GLSMVertexInput.glGenVertexArrays();
        if (behind) {
            if (behindProgram == null) {
                behindProgram = new CompositeProgram("angelica:translucency_composite_behind",
                    ShaderConstants.builder().add("TERRAIN_BEHIND").build());
            }
            return behindProgram;
        }
        if (program == null) program = new CompositeProgram("angelica:translucency_composite", ShaderConstants.EMPTY);
        return program;
    }

    private void prepare(Target target) {
        final int draw = GLSMFramebuffer.getDrawFramebuffer();
        final int read = GLSMFramebuffer.getReadFramebuffer();
        final int state = pushClearState();
        try {
            clear(target);
            final int mask = GL11.GL_DEPTH_BUFFER_BIT | (depthFormat.isCombinedStencil() ? GL11.GL_STENCIL_BUFFER_BIT : 0);
            blit(targetFramebuffer, target.framebuffer.getId(), mask);
        } finally {
            bindFramebuffers(draw, read);
            GLSMAttribStack.popStateTo(state);
        }
    }

    private void clearBehind(Target terrain) {
        final int state = pushClearState();
        try {
            clear(terrain);
        } finally {
            GLSMAttribStack.popStateTo(state);
        }
    }

    private int pushClearState() {
        if (prepareState == null) prepareState = StateSet.forMask(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_SCISSOR_BIT);
        return GLSMAttribStack.pushState(prepareState);
    }

    private static void clear(Target target) {
        GLSMRasterizer.disableScissorTest();
        GLSMBlend.glColorMask(true, true, true, true);
        target.framebuffer.bind();
        GLSMBlend.glClearColor(0f, 0f, 0f, 0f);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }

    private static void bindFramebuffers(int draw, int read) {
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
    }

    private void blit(int from, int to, int mask) {
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from);
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to);
        GLSMFramebuffer.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, mask, GL11.GL_NEAREST);
    }

    private static void bindTexture(int unit, int texture) {
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    private void syncSize() {
        final int framebuffer = main.framebuffer();
        final int depth = main.depthTexture();
        final int version = main.depthVersion();
        final int newWidth = main.width();
        final int newHeight = main.height();
        if (framebuffer == targetFramebuffer && depth == targetDepthTexture && version == depthVersion
            && newWidth == width && newHeight == height) return;
        destroyTargets();
        final var info = TextureInfoCache.INSTANCE.getInfo(depth);
        final DepthBufferFormat format = info == null ? null : DepthBufferFormat.fromGlEnum(info.getInternalFormat());
        if (format == null || newWidth <= 0 || newHeight <= 0) {
            throw new IllegalStateException("Unsupported main framebuffer depth texture " + depth);
        }
        targetFramebuffer = framebuffer;
        targetDepthTexture = depth;
        depthVersion = version;
        width = newWidth;
        height = newHeight;
        depthFormat = format;
    }

    private Target layer(TranslucentLayer layer) {
        final int i = layer.ordinal();
        if (layers[i] == null) layers[i] = new Target(width, height, depthFormat, true);
        return layers[i];
    }

    private Target depthSnapshot() {
        if (depthSnapshot == null) depthSnapshot = new Target(width, height, depthFormat, false);
        return depthSnapshot;
    }

    private void destroyTargets() {
        for (int i = 0; i < LAYERS; i++) {
            if (layers[i] != null) layers[i].destroy();
            layers[i] = null;
        }
        if (depthSnapshot != null) depthSnapshot.destroy();
        depthSnapshot = null;
        drawnMask = 0;
        behindDrawn = false;
    }

    private static void setUniform(int program, String name, int value) {
        GLSMPrograms.glUniform1i(GLSMPrograms.glGetUniformLocation(program, name), value);
    }

    private static final class TextureBindings {
        private final int[] units;
        private final int[] textures;
        private final int[] samplers;
        private int savedMask;
        private int activeUnit;

        TextureBindings(int count) {
            units = new int[count];
            textures = new int[count];
            samplers = new int[count];
        }

        void save(int slot, int unit) {
            if (savedMask == 0) activeUnit = GLSMTextures.getActiveTextureUnit();
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            units[slot] = unit;
            textures[slot] = GLSMQueries.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            samplers[slot] = GLSMQueries.glGetInteger(GL33.GL_SAMPLER_BINDING);
            savedMask |= 1 << slot;
            GLSMTextures.glBindSampler(unit, 0);
        }

        boolean isSaved() {
            return savedMask != 0;
        }

        void restoreActiveUnit() {
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + activeUnit);
        }

        void restore() {
            restore(activeUnit);
        }

        void restore(int restoreUnit) {
            if (savedMask == 0) return;
            for (int slot = 0; slot < units.length; slot++) {
                if ((savedMask & (1 << slot)) == 0) continue;
                bindTexture(units[slot], textures[slot]);
                GLSMTextures.glBindSampler(units[slot], samplers[slot]);
            }
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + restoreUnit);
            savedMask = 0;
        }
    }

    private static final class CompositeProgram {
        final GlProgram<Object> program;
        final int maskUniform;

        CompositeProgram(String name, ShaderConstants constants) {
            final GlShader vertex = ShaderLoader.loadShader(ShaderType.VERTEX, "angelica:panorama_blur.vert", ShaderConstants.EMPTY);
            try {
                final GlShader fragment = ShaderLoader.loadShader(ShaderType.FRAGMENT, "angelica:translucency_composite.frag", constants);
                try {
                    program = GlProgram.builder(name).attachShader(vertex).attachShader(fragment).link(context -> context);
                } finally {
                    fragment.delete();
                }
            } finally {
                vertex.delete();
            }
            GLSMPrograms.glUseProgram(program.handle());
            setUniform(program.handle(), "u_Depth", 0);
            for (int i = 0; i < LAYERS; i++) {
                setUniform(program.handle(), "u_Layer" + i, FIRST_LAYER_UNIT + i);
                setUniform(program.handle(), "u_LayerDepth" + i, FIRST_LAYER_UNIT + LAYERS + i);
            }
            setUniform(program.handle(), "u_TerrainBehind", TERRAIN_BEHIND_UNIT);
            maskUniform = GLSMPrograms.glGetUniformLocation(program.handle(), "u_LayerMask");
        }
    }

    private static final class Target {
        final int color;
        final int depth;
        final GlFramebuffer framebuffer;
        int behind;

        Target(int width, int height, DepthBufferFormat depthFormat, boolean withColor) {
            // Match the main framebuffer's upload type for unsized depth storage.
            final int depthType = depthFormat == DepthBufferFormat.DEPTH ? GL11.GL_FLOAT : depthFormat.getGlFormat();
            depth = texture(width, height, depthFormat.getGlInternalFormat(), depthFormat.getGlType(), depthType);
            framebuffer = new GlFramebuffer();
            framebuffer.addDepthAttachment(depth);
            if (withColor) {
                color = texture(width, height, GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE);
                framebuffer.addColorAttachment(0, color);
                framebuffer.drawBuffers(SINGLE_BUFFER);
                framebuffer.readBuffer(0);
            } else {
                color = 0;
                framebuffer.noDrawBuffers();
                RenderSystem.readBuffer(framebuffer.getId(), GL11.GL_NONE);
            }
        }

        void attachBehind(int width, int height) {
            if (behind != 0) return;
            behind = texture(width, height, GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE);
            framebuffer.addColorAttachment(1, behind);
        }

        private static int texture(int width, int height, int internalFormat, int format, int type) {
            final int previous = GLSMQueries.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            final int texture = GLSMTextures.glGenTextures();
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GLSMTextures.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, (ByteBuffer) null);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, previous);
            return texture;
        }

        void destroy() {
            framebuffer.destroy();
            if (color != 0) GLSMTextures.glDeleteTextures(color);
            if (behind != 0) GLSMTextures.glDeleteTextures(behind);
            GLSMTextures.glDeleteTextures(depth);
        }
    }

    private static final class MinecraftTarget implements MainTarget {
        private static Framebuffer fb() {
            return Minecraft.getMinecraft().getFramebuffer();
        }

        @Override public int framebuffer() { return fb() == null ? 0 : fb().framebufferObject; }
        @Override public int depthTexture() { return fb() instanceof IRenderTargetExt target ? target.iris$getDepthTextureId() : 0; }
        @Override public int width() { return fb().framebufferWidth; }
        @Override public int height() { return fb().framebufferHeight; }
        @Override public int depthVersion() { return ((IRenderTargetExt) fb()).iris$getDepthBufferVersion(); }
    }
}
