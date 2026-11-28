package com.dhj.actinium.mixinterface.features.iris;

/** Gives the particle renderer access to the writable tail of its shared quad buffer. */
public interface ParticleVertexRange {

    /** Check after particle writes and before emitting an instance: terrain quad metadata requires keeping the vertices. */
    boolean actinium$canRewindParticleVertices();

    /** Discards vertices from {@code firstVertex} onward in an eligible run and leaves it open for appends. */
    void actinium$rewindParticleVertices(int firstVertex);
}
