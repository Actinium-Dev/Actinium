package com.gtnewhorizons.angelica.rendering.tesr;

import com.dhj.actinium.render.RenderWorldRecursionGuard;
import com.dhj.actinium.render.translucency.TranslucencyLayers;
import com.dhj.actinium.runtime.ActiniumRuntime;
import com.gtnewhorizons.angelica.compat.mojang.RenderLayer;
import com.gtnewhorizons.angelica.glsm.DepthEcho;
import com.gtnewhorizons.angelica.glsm.DepthPeel;
import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMQueries;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.StateSet;
import com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import net.coderbot.batchedentityrendering.impl.TransparencyType;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import net.coderbot.iris.gl.texture.DepthBufferFormat;
import net.coderbot.iris.rendertarget.IRenderTargetExt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import net.minecraftforge.client.MinecraftForgeClient;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

import java.nio.ByteBuffer;


/// Depth peels the translucent geometry the block entity batchers have queued ([DepthPeel]), since batches group it by
/// layer and material rather than by depth. A flush whose translucent geometry comes from one block entity in one
/// batch that keeps submission order is not peeled.
///
/// Each of the first [#SLOTS_PER_PASS] peeled flushes of a pass keeps its own peel count, and later ones peel
/// [#MAX_PEELS]. Occlusion queries, read back frames later without stalling, lower a count one peel at a time while
/// its last peel draws nothing and reset it when the rest pass draws.
public final class TranslucentPeel {

    private static final int MAX_PEELS = 4;
    private static final int SLOTS_PER_PASS = 32;

    private static int width;
    private static int height;
    private static int depthVersion = -1;
    private static int depthTexture;
    private static Target[] peels;
    private static Target sceneDepth;
    private static StateSet clearState;
    private static final Slot[] slots = new Slot[2 * SLOTS_PER_PASS];
    private static int passFlushes;
    private static boolean collecting;
    private static Object firstSource;
    private static Object firstBatch;
    private static int firstBlockEntity;
    private static int blockEntity;
    private static boolean mixed;

    private TranslucentPeel() {}

    private static boolean enabled() {
        return ActiniumRuntime.options().performance.translucencyMode.composites()
            && TesrBatchRenderer.deferredPipeline() == null && DepthPeel.supported();
    }

    public static void beginPass() {
        passFlushes = 0;
        collecting = enabled();
        resetBatches();
        if (!collecting && peels != null) destroy();
    }

    public static void endPass() {
        collecting = false;
        resetBatches();
    }

    private static boolean collecting() {
        return collecting && !RenderWorldRecursionGuard.isNested() && !DepthEcho.replaying();
    }

    public static void beforeBlockEntity() {
        if (collecting()) blockEntity++;
    }

    /// `source` and `batch` identify one draw: an `AngelicaBufferSource` and a layer, or [RetainedTesrGroups] and a
    /// group.
    public static void queued(RenderLayer layer, Object source, Object batch) {
        if (mixed || !collecting() || layer.getTransparencyType() == TransparencyType.OPAQUE) return;
        if (firstBatch == null) {
            firstSource = source;
            firstBatch = batch;
            firstBlockEntity = blockEntity;
        } else if (batch != firstBatch || source != firstSource || blockEntity != firstBlockEntity) {
            mixed = true;
        }
    }

    public static void queuedUnordered(RenderLayer layer) {
        if (collecting() && layer.getTransparencyType() != TransparencyType.OPAQUE) mixed = true;
    }

    private static void resetBatches() {
        firstSource = null;
        firstBatch = null;
        mixed = false;
    }

    public static void flush() {
        if (!collecting()) return;
        final ModelPartBatcher models = ModelPartBatcher.INSTANCE;
        final TesrBatchRenderer tesrs = TesrBatchRenderer.INSTANCE;
        BatchStateGuard.suspend();
        try {
            models.drawOpaque();
            tesrs.drawOpaque();
        } finally {
            BatchStateGuard.resume();
        }
        final boolean peeling = mixed;
        resetBatches();
        if (!peeling) return;
        BatchStateGuard.suspend();
        try {
            final boolean modelsQueued = models.beginPeel();
            final boolean tesrsQueued = tesrs.beginPeel();
            if (modelsQueued || tesrsQueued) peel(modelsQueued, tesrsQueued);
        } finally {
            try {
                tesrs.endPeel();
            } finally {
                try {
                    models.endPeel();
                } finally {
                    BatchStateGuard.resume();
                }
            }
        }
    }

    @FunctionalInterface
    interface BatchDraw {
        void draw(boolean replay);
    }

    private static void peel(boolean models, boolean tesrs) {
        final BatchDraw batch = replay -> {
            if (models) ModelPartBatcher.INSTANCE.drawPeeled(replay);
            if (tesrs) TesrBatchRenderer.INSTANCE.drawPeeled(replay);
        };
        final Framebuffer main = Minecraft.getMinecraft().getFramebuffer();
        final int target = GLSMFramebuffer.getDrawFramebuffer();
        if (!(main instanceof IRenderTargetExt ext) || target == 0
            || target != main.framebufferObject && target != TranslucencyLayers.INSTANCE.openFramebuffer()
            || ext.iris$getDepthTextureId() <= 0 || main.framebufferWidth <= 0 || main.framebufferHeight <= 0) {
            batch.draw(false);
            return;
        }
        peel(ext.iris$getDepthTextureId(), main.framebufferWidth, main.framebufferHeight,
            ext.iris$getDepthBufferVersion(), batch);
    }

    static void peel(int depth, int width, int height, int version, BatchDraw batch) {
        final int target = GLSMFramebuffer.getDrawFramebuffer();
        final int read = GLSMFramebuffer.getReadFramebuffer();
        final int unit = GLSMTextures.getActiveTextureUnit();
        final int previousPeelTexture = GLSMTextures.getTextures().getTextureUnitBindings(DepthPeel.TEXTURE_UNIT).getBinding();
        final int previousSceneTexture = GLSMTextures.getTextures().getTextureUnitBindings(DepthPeel.SCENE_TEXTURE_UNIT).getBinding();
        final Slot slot = slot(passFlushes++);
        final int count = slot != null ? slot.update() : MAX_PEELS;
        boolean completed = false;
        try {
            syncSize(depth, width, height, version);
            final boolean query = slot != null && slot.beginQueries(count);
            blitDepth(target, sceneDepth.framebuffer.getId());
            bindTexture(DepthPeel.SCENE_TEXTURE_UNIT, sceneDepth.depth);
            Target bound = sceneDepth;
            for (int i = 0; i < count; i++) {
                final Target current = peels[i % 2];
                clearDepth(current);
                current.framebuffer.bind();
                bindTexture(DepthPeel.TEXTURE_UNIT, bound.depth);
                GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                DepthPeel.setPass(DepthPeel.Pass.DEPTH, i == 0);
                if (query) slot.begin(i);
                batch.draw(i != 0);
                if (query) slot.end();
                GLSMFramebuffer.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target);
                bindTexture(DepthPeel.TEXTURE_UNIT, current.depth);
                GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                DepthPeel.setPass(DepthPeel.Pass.COLOR, false);
                batch.draw(true);
                bound = current;
            }
            DepthPeel.setPass(DepthPeel.Pass.REST, false);
            if (query) slot.begin(count);
            batch.draw(true);
            if (query) slot.end();
            if (DepthPeel.skippedApplicationDraws()) {
                DepthPeel.setPass(DepthPeel.Pass.APPLICATION, false);
                batch.draw(true);
            }
            completed = true;
        } finally {
            if (!completed && slot != null) slot.abandon();
            DepthPeel.setPass(DepthPeel.Pass.OFF, false);
            bindTexture(DepthPeel.TEXTURE_UNIT, previousPeelTexture);
            bindTexture(DepthPeel.SCENE_TEXTURE_UNIT, previousSceneTexture);
            GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target);
            GLSMFramebuffer.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        }
    }

    private static Slot slot(int flush) {
        if (flush >= SLOTS_PER_PASS) return null;
        final int index = (MinecraftForgeClient.getRenderPass() == 1 ? SLOTS_PER_PASS : 0) + flush;
        if (slots[index] == null) slots[index] = new Slot();
        return slots[index];
    }

    private static void clearDepth(Target target) {
        if (clearState == null) clearState = StateSet.forMask(GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_SCISSOR_BIT);
        final int saved = GLSMAttribStack.pushState(clearState);
        try {
            GLSMRasterizer.disableScissorTest();
            GLSMDepthStencilAlpha.glDepthMask(true);
            GLSMDepthStencilAlpha.glClearDepth(0.0);
            target.framebuffer.bind();
            GLSMFramebuffer.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        } finally {
            GLSMAttribStack.popStateTo(saved);
        }
    }

    private static void blitDepth(int from, int to) {
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from);
        GLSMFramebuffer.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to);
        GLSMFramebuffer.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
    }

    private static void bindTexture(int unit, int texture) {
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    private static void syncSize(int depth, int newWidth, int newHeight, int version) {
        if (peels != null && depthTexture == depth && width == newWidth && height == newHeight && depthVersion == version) return;
        destroyTargets();
        final var info = TextureInfoCache.INSTANCE.getInfo(depth);
        final DepthBufferFormat format = info == null ? null : DepthBufferFormat.fromGlEnum(info.getInternalFormat());
        if (format == null) throw new IllegalStateException("Unsupported depth peel scene texture " + depth);
        width = newWidth;
        height = newHeight;
        depthVersion = version;
        depthTexture = depth;
        sceneDepth = new Target(format.getGlInternalFormat(), format.getGlType(), format.getGlFormat());
        peels = new Target[2];
        boolean allocated = false;
        try {
            peels[0] = Target.float32();
            peels[1] = Target.float32();
            allocated = true;
        } finally {
            if (!allocated) destroyTargets();
        }
    }

    public static void destroy() {
        endPass();
        destroyTargets();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null) slots[i].delete();
            slots[i] = null;
        }
    }

    private static void destroyTargets() {
        if (peels != null) {
            for (Target target : peels) {
                if (target != null) target.destroy();
            }
        }
        if (sceneDepth != null) sceneDepth.destroy();
        peels = null;
        sceneDepth = null;
    }

    private static final class Slot {
        private int count = MAX_PEELS;
        private final int[] queries = new int[MAX_PEELS + 1];
        private int issued = -1;
        private boolean querying;

        int update() {
            if (issued < 0) return count;
            for (int i = 0; i <= issued; i++) {
                if (GLSMQueries.glGetQueryObjecti(queries[i], GL15.GL_QUERY_RESULT_AVAILABLE) == 0) return count;
            }
            int drawn = issued;
            for (int i = 0; i < issued; i++) {
                if (GLSMQueries.glGetQueryObjecti(queries[i], GL15.GL_QUERY_RESULT) == 0) {
                    drawn = i;
                    break;
                }
            }
            if (drawn == issued && GLSMQueries.glGetQueryObjecti(queries[issued], GL15.GL_QUERY_RESULT) != 0) {
                count = MAX_PEELS;
            } else if (drawn < count) {
                count = Math.max(1, count - 1);
            }
            issued = -1;
            return count;
        }

        boolean beginQueries(int peels) {
            if (issued >= 0) return false;
            for (int i = 0; i < queries.length; i++) {
                if (queries[i] == 0) queries[i] = GLSMQueries.glGenQueries();
            }
            issued = peels;
            return true;
        }

        void begin(int peel) {
            GLSMQueries.glBeginQuery(GL33.GL_ANY_SAMPLES_PASSED, queries[peel]);
            querying = true;
        }

        void end() {
            querying = false;
            GLSMQueries.glEndQuery(GL33.GL_ANY_SAMPLES_PASSED);
        }

        void abandon() {
            if (querying) end();
            issued = -1;
        }

        void delete() {
            for (int query : queries) {
                if (query != 0) GLSMQueries.glDeleteQueries(query);
            }
        }
    }

    private static final class Target {
        final int depth;
        final GlFramebuffer framebuffer = new GlFramebuffer();

        Target(int internalFormat, int format, int type) {
            depth = texture(internalFormat, format, type);
            boolean complete = false;
            try {
                framebuffer.addDepthAttachment(depth);
                framebuffer.noDrawBuffers();
                RenderSystem.readBuffer(framebuffer.getId(), GL11.GL_NONE);
                final int status = framebuffer.checkStatus();
                if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                    throw new IllegalStateException("Incomplete depth peel framebuffer: " + status);
                }
                complete = true;
            } finally {
                if (!complete) destroy();
            }
        }

        static Target float32() {
            return new Target(GL30.GL_DEPTH_COMPONENT32F, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT);
        }

        private static int texture(int internalFormat, int format, int type) {
            final int previous = GLSMQueries.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            final int texture = GLSMTextures.glGenTextures();
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GLSMTextures.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
            GLSMTextures.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, (ByteBuffer) null);
            GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, previous);
            return texture;
        }

        void destroy() {
            framebuffer.destroy();
            GLSMTextures.glDeleteTextures(depth);
        }
    }
}
