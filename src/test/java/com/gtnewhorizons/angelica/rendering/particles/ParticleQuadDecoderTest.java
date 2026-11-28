package com.gtnewhorizons.angelica.rendering.particles;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Decodes the vertices 1.12.2's own {@code Particle.renderParticle} writes, so the five rotation
 * arguments and the per-vertex colour and lightmap checks are pinned against vanilla arithmetic
 * instead of against a copy of it.
 */
class ParticleQuadDecoderTest {

    private static final int STRIDE = DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP.getSize();
    private static final float EPSILON = 1.0e-6F;
    private static final int BRIGHTNESS = 0x00F00123;

    /** Rotation arguments in the order {@code ParticleManager.renderParticles} passes them. */
    private record Rotation(float x, float xz, float z, float yz, float xy) {}

    private static final Rotation ROTATION = new Rotation(0.36F, 0.21F, 0.48F, 0.13F, 0.27F);
    private static final Rotation OTHER_ROTATION = new Rotation(0.5F, 0.1F, 0.2F, 0.9F, 0.7F);

    private final ParticleParams params = new ParticleParams();

    @BeforeEach
    void resetCamera() {
        Particle.interpPosX = 0.0;
        Particle.interpPosY = 0.0;
        Particle.interpPosZ = 0.0;
        Particle.cameraViewDir = new Vec3d(0.0, 0.0, 1.0);
    }

    @AfterEach
    void clearCamera() {
        Particle.cameraViewDir = null;
    }

    @Test
    void decodesTheQuadVanillaWritesForTheFiveRotationArguments() {
        final Probe probe = new Probe();
        probe.setScale(1.6F);
        final ByteBuffer vertices = probe.capture();

        assertTrue(decode(vertices, 0, ROTATION));

        assertEquals(1.5D, params.centerX, EPSILON);
        assertEquals(2.5D, params.centerY, EPSILON);
        assertEquals(3.5D, params.centerZ, EPSILON);
        assertEquals(0.1F * 1.6F, params.half, EPSILON);
        assertEquals(ParticleQuads.packColor(0.25F, 0.5F, 0.75F, 1.0F), params.colorABGR);
        assertEquals(BRIGHTNESS, params.brightness);
    }

    @Test
    void rejectsAQuadBuiltFromADifferentRotationBasis() {
        assertFalse(decode(new Probe().capture(), 0, OTHER_ROTATION));
    }

    @Test
    void rejectsAQuadWhoseCornersDoNotFollowTheUvPattern() {
        final ByteBuffer vertices = new Probe().capture();
        vertices.putFloat(2 * STRIDE + DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP.getUvOffsetById(0), 0.75F);

        assertFalse(decode(vertices, 0, ROTATION));
    }

    @Test
    void rejectsAQuadWhoseVertexColorsDisagree() {
        final ByteBuffer vertices = new Probe().capture();
        vertices.putInt(3 * STRIDE + DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP.getColorOffset(), 0xFF00FF00);

        assertFalse(decode(vertices, 0, ROTATION));
    }

    @Test
    void rejectsAQuadWhoseVertexLightmapsDisagree() {
        final ByteBuffer vertices = new Probe().capture();
        vertices.putInt(STRIDE + DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP.getUvOffsetById(1), 0x00F00000);

        assertFalse(decode(vertices, 0, ROTATION));
    }

    @Test
    void rejectsTheQuaternionRotatedCornersOfASpinningParticle() {
        final Probe probe = new Probe();
        probe.setParticleAngle(0.8F);
        assertFalse(decode(probe.capture(), 0, ROTATION));
    }

    @Test
    void decodesTheAppendedQuadWithoutMovingTheSharedBufferOnSuccessOrFailure() {
        final BufferBuilder run = new BufferBuilder(64);
        run.begin(GL11.GL_QUADS, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP);
        run.putBulkData(ByteBuffer.allocate(3 * STRIDE));
        final int firstVertex = run.getVertexCount();
        new Probe().writeTo(run);
        assertEquals(4, run.getVertexCount() - firstVertex);

        final int end = run.getVertexCount() * STRIDE;
        final ByteBuffer vertices = run.getByteBuffer();
        vertices.limit(end).mark().position(end);

        assertTrue(decode(vertices, firstVertex, ROTATION));
        assertEquals(1.5F, params.centerX, EPSILON);
        assertEquals(BRIGHTNESS, params.brightness);
        assertCursorUnchanged(vertices, end, firstVertex * STRIDE);
        assertFalse(decode(vertices, firstVertex, OTHER_ROTATION));
        assertCursorUnchanged(vertices, end, firstVertex * STRIDE);
    }

    private static void assertCursorUnchanged(ByteBuffer vertices, int end, int mark) {
        assertEquals(end, vertices.position());
        assertEquals(end, vertices.limit());
        assertEquals(ByteOrder.nativeOrder(), vertices.order());
        assertEquals(mark, vertices.reset().position());
        vertices.position(end);
    }

    private boolean decode(ByteBuffer vertices, int firstVertex, Rotation rotation) {
        return ParticleQuadDecoder.decode(vertices, firstVertex, rotation.x(), rotation.xz(), rotation.z(),
            rotation.yz(), rotation.xy(), params);
    }

    /** Writes vertices through the vanilla {@code Particle.renderParticle} body. */
    private static final class Probe extends Particle {

        Probe() {
            super(null, 1.5D, 2.5D, 3.5D);
            this.setRBGColorF(0.25F, 0.5F, 0.75F);
            this.particleAge = 4;
            this.particleMaxAge = 20;
        }

        void setScale(float scale) {
            this.particleScale = scale;
        }

        void setParticleAngle(float angle) {
            this.particleAngle = angle;
            this.prevParticleAngle = angle;
        }

        ByteBuffer capture() {
            final BufferBuilder buffer = new BufferBuilder(64);
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP);
            writeTo(buffer);
            assertEquals(4, buffer.getVertexCount());
            return buffer.getByteBuffer();
        }

        void writeTo(BufferBuilder buffer) {
            renderParticle(buffer, null, 0.5F, ROTATION.x(), ROTATION.xz(), ROTATION.z(), ROTATION.yz(), ROTATION.xy());
        }

        @Override
        public int getBrightnessForRender(float partialTick) {
            return BRIGHTNESS;
        }
    }
}
