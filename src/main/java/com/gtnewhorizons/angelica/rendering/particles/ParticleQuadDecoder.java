package com.gtnewhorizons.angelica.rendering.particles;

import com.gtnewhorizons.angelica.glsm.ffp.ParticleQuadMesh;

import java.nio.ByteBuffer;

/**
 * Recognises a particle quad among the vertices a particle wrote and turns it into one instance.
 *
 * <p>The decode only accepts the axis aligned billboard layout: the four corners have to match
 * {@code centre + half * corner(rotX, rotXZ, rotZ, rotYZ, rotXY)} and the texture coordinates have to
 * follow the vanilla diagonal. Everything else, including the quaternion rotated corners of a
 * particle with a non-zero {@code particleAngle}, stays in the original vertex buffer.</p>
 *
 * <p>The five rotation arguments are the ones {@code ParticleManager.renderParticles} passes to
 * {@code Particle.renderParticle} in 1.12.2, in call order. Vanilla moved the names around in the
 * signature but still passes {@code rotationX, rotationXZ, rotationZ, rotationYZ, rotationXY}
 * positionally, which is the order {@link ParticleQuadMesh} expects.</p>
 */
public final class ParticleQuadDecoder {

    /** Byte offsets in {@code DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP}. */
    private static final int X = 0, Y = 4, Z = 8, U = 12, V = 16, COLOR = 20, LIGHTMAP = 24;
    private static final int STRIDE = LIGHTMAP + Integer.BYTES;

    private ParticleQuadDecoder() {}

    /**
     * Reads four native-order particle vertices without changing the buffer or its cursor.
     * @param vertices shared buffer with native byte order and the standard particle vertex format
     * @param firstVertex index of the quad's first vertex, measured from byte index zero
     * @param rotationX camera billboard rotation
     * @param rotationXZ camera billboard rotation
     * @param rotationZ camera billboard rotation
     * @param rotationYZ camera billboard rotation
     * @param rotationXY camera billboard rotation
     * @param out receives the instance payload when the quad decodes
     * @return whether the vertices form one decodable particle quad
     */
    public static boolean decode(ByteBuffer vertices, int firstVertex, float rotationX, float rotationXZ,
                                 float rotationZ, float rotationYZ, float rotationXY, ParticleParams out) {
        final float sx = rotationX + rotationYZ;
        final float sy = rotationXZ;
        final float sz = rotationZ + rotationXY;
        final float denom = sx * sx + sy * sy + sz * sz;
        if (denom <= 1.0e-12f) return false;

        final int base0 = firstVertex * STRIDE;
        final int base1 = base0 + STRIDE;
        final int base2 = base1 + STRIDE;
        final int base3 = base2 + STRIDE;
        final float x0 = vertices.getFloat(base0 + X), x2 = vertices.getFloat(base2 + X);
        final float y0 = vertices.getFloat(base0 + Y), y2 = vertices.getFloat(base2 + Y);
        final float z0 = vertices.getFloat(base0 + Z), z2 = vertices.getFloat(base2 + Z);
        final float cx = (x0 + x2) * 0.5f;
        final float cy = (y0 + y2) * 0.5f;
        final float cz = (z0 + z2) * 0.5f;
        final float half = ((x2 - x0) * sx + (y2 - y0) * sy + (z2 - z0) * sz) / (2.0f * denom);

        final float scale = Math.max(Math.abs(cx), Math.max(Math.abs(cy), Math.abs(cz)));
        final float tol = 1.0e-5f * (1.0f + Math.abs(half) + scale);
        for (int corner = 0; corner < ParticleQuadMesh.VERTEX_COUNT; corner++) {
            final int base = base0 + corner * STRIDE;
            final float a = ParticleQuadMesh.cornerA(corner) * half;
            final float b = ParticleQuadMesh.cornerB(corner) * half;
            if (Math.abs(vertices.getFloat(base + X) - (cx + ParticleQuadMesh.offsetX(a, b, rotationX, rotationYZ))) > tol) return false;
            if (Math.abs(vertices.getFloat(base + Y) - (cy + ParticleQuadMesh.offsetY(b, rotationXZ))) > tol) return false;
            if (Math.abs(vertices.getFloat(base + Z) - (cz + ParticleQuadMesh.offsetZ(a, b, rotationZ, rotationXY))) > tol) return false;
        }

        final float u0 = vertices.getFloat(base0 + U), v0 = vertices.getFloat(base0 + V);
        final float u1 = vertices.getFloat(base2 + U), v1 = vertices.getFloat(base2 + V);
        if (u0 != vertices.getFloat(base1 + U) || v1 != vertices.getFloat(base1 + V)
            || u1 != vertices.getFloat(base3 + U) || v0 != vertices.getFloat(base3 + V)) {
            return false;
        }

        final int color = vertices.getInt(base0 + COLOR);
        final int lightmap = vertices.getInt(base0 + LIGHTMAP);
        for (int corner = 1; corner < ParticleQuadMesh.VERTEX_COUNT; corner++) {
            final int base = base0 + corner * STRIDE;
            if (vertices.getInt(base + COLOR) != color || vertices.getInt(base + LIGHTMAP) != lightmap) {
                return false;
            }
        }

        out.centerX = cx;
        out.centerY = cy;
        out.centerZ = cz;
        out.half = half;
        out.u0 = u0;
        out.v0 = v0;
        out.u1 = u1;
        out.v1 = v1;
        out.colorABGR = color;
        out.brightness = lightmap;
        return true;
    }
}
