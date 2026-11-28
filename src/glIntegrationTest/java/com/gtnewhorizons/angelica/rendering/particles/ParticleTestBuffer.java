package com.gtnewhorizons.angelica.rendering.particles;

import com.dhj.actinium.mixinterface.features.iris.ParticleVertexRange;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import org.lwjgl.opengl.GL11;

/** Supplies the particle mixin's rewind operation in the plain JUnit VM. */
final class ParticleTestBuffer extends BufferBuilder implements ParticleVertexRange {

    private boolean drawing;
    private boolean hasTerrainMetadata;

    ParticleTestBuffer() {
        super(256);
    }

    @Override
    public void begin(int mode, VertexFormat format) {
        super.begin(mode, format);
        drawing = true;
        hasTerrainMetadata = false;
    }

    @Override
    public void finishDrawing() {
        super.finishDrawing();
        drawing = false;
    }

    void markTerrainMetadata() {
        hasTerrainMetadata = true;
    }

    @Override
    public boolean actinium$canRewindParticleVertices() {
        return drawing && getDrawMode() == GL11.GL_QUADS
            && getVertexFormat() == DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP && !hasTerrainMetadata;
    }

    @Override
    public void actinium$rewindParticleVertices(int firstVertex) {
        if (!actinium$canRewindParticleVertices()) throw new IllegalStateException("Particle range cannot be rewound");
        finishDrawing();
        int[] prefix = new int[firstVertex * getVertexFormat().getIntegerSize()];
        getByteBuffer().asIntBuffer().get(prefix);
        begin(getDrawMode(), getVertexFormat());
        addVertexData(prefix);
        getByteBuffer().position(prefix.length * Integer.BYTES);
    }
}
