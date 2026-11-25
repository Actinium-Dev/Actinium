package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.GLSMLighting;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.ffp.VertexKey;
import com.gtnewhorizons.angelica.glsm.stacks.BooleanStateStack;
import com.gtnewhorizons.angelica.glsm.states.LightModelState;
import com.gtnewhorizons.angelica.glsm.states.LightState;
import com.gtnewhorizons.angelica.glsm.states.MaterialState;
import net.coderbot.iris.shadows.ShadowRenderingState;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import org.joml.Matrix3f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

/** Preserves directional item lighting when a TESR flattens its geometry. */
public final class FlattenedItemLighting {
    private static final float FLATNESS_CUTOFF = 0.05f;

    private FlattenedItemLighting() {
    }

    /** Describes how the upload must consume the modified vertex colors. */
    public enum BakeResult {
        UNCHANGED,
        SHADER_PACK,
        FIXED_FUNCTION
    }

    /** Identifies draws whose colors depend on the current view and must be rebuilt. */
    public static boolean applies() {
        return GLStateManager.isMainThread() && TesrBatchRenderer.INSTANCE.currentScope() != 0
            && GLSMLighting.getLightingState().isEnabled() && GLSMLighting.getColorMaterial().isEnabled()
            && GLSMLighting.getColorMaterialParameter().getValue() == GL11.GL_AMBIENT_AND_DIFFUSE
            && !GLStateManager.isRecordingDisplayList() && !ShadowRenderingState.areShadowsCurrentlyBeingRendered()
            && (IrisApi.getInstance().isShaderPackInUse() ? TesrShaderPrograms.packActive() : GLSMPrograms.getActiveProgram() == 0)
            && isFlattened(GLSMMatrices.getModelViewMatrix());
    }

    static boolean isFlattened(Matrix4fc matrix) {
        // Compare the linear transform with its adjugate so the test is independent of overall scale.
        float ax = matrix.m00(), ay = matrix.m01(), az = matrix.m02();
        float bx = matrix.m10(), by = matrix.m11(), bz = matrix.m12();
        float cx = matrix.m20(), cy = matrix.m21(), cz = matrix.m22();
        float bcx = by * cz - bz * cy, bcy = bz * cx - bx * cz, bcz = bx * cy - by * cx;
        float cax = cy * az - cz * ay, cay = cz * ax - cx * az, caz = cx * ay - cy * ax;
        float abx = ay * bz - az * by, aby = az * bx - ax * bz, abz = ax * by - ay * bx;
        float determinant = ax * bcx + ay * bcy + az * bcz;
        float normSquared = ax * ax + ay * ay + az * az + bx * bx + by * by + bz * bz + cx * cx + cy * cy + cz * cz;
        float adjugateSquared = bcx * bcx + bcy * bcy + bcz * bcz + cax * cax + cay * cay + caz * caz
            + abx * abx + aby * aby + abz * abz;
        return determinant != 0.0f && Math.abs(determinant) * (float) Math.sqrt(normSquared) < FLATNESS_CUTOFF * adjugateSquared;
    }

    /** Updates the live vertex buffer before it is drawn or copied into a transient TESR batch. */
    public static BakeResult bake(BufferBuilder buffer) {
        if (buffer.getVertexCount() == 0 || !applies()) {
            return BakeResult.UNCHANGED;
        }
        VertexFormat format = buffer.getVertexFormat();
        if (!hasBakedLightingLayout(format)) {
            return BakeResult.UNCHANGED;
        }
        BooleanStateStack[] enabledLights = GLSMLighting.getLightStates();
        LightState[] lights = GLSMLighting.getLightDataStates();
        MaterialState material = GLSMLighting.getFrontMaterial();
        if (hasRgb(material.emission.x, material.emission.y, material.emission.z)) {
            return BakeResult.UNCHANGED;
        }
        boolean specular = material.shininess > 0.0f
            && hasRgb(material.specular.x, material.specular.y, material.specular.z);
        for (int i = 0; i < VertexKey.FFP_LIGHT_COUNT; i++) {
            LightState light = lights[i];
            if (enabledLights[i].isEnabled() && (light.position.w != 0.0f
                || specular && hasRgb(light.specular.x, light.specular.y, light.specular.z))) {
                return BakeResult.UNCHANGED;
            }
        }
        shade(buffer.getByteBuffer(), 0, buffer.getVertexCount(), format, GLSMMatrices.getModelViewMatrix(),
            GLSMLighting.getLightModel(), enabledLights, lights);
        return TesrShaderPrograms.packActive() ? BakeResult.SHADER_PACK : BakeResult.FIXED_FUNCTION;
    }

    private static boolean hasRgb(float red, float green, float blue) {
        return red != 0.0f || green != 0.0f || blue != 0.0f;
    }

    private static boolean hasBakedLightingLayout(VertexFormat format) {
        boolean packedColor = false;
        boolean packedNormal = false;
        for (VertexFormatElement element : format.getElements()) {
            if (element.getUsage() == VertexFormatElement.EnumUsage.COLOR) {
                packedColor = element.getType() == VertexFormatElement.EnumType.UBYTE && element.getElementCount() == 4;
            } else if (element.getUsage() == VertexFormatElement.EnumUsage.NORMAL) {
                packedNormal = element.getType() == VertexFormatElement.EnumType.BYTE && element.getElementCount() == 3;
            }
        }
        return packedColor && packedNormal;
    }

    static void shade(ByteBuffer vertices, int firstVertex, int vertexCount, VertexFormat format, Matrix4fc modelView,
                      LightModelState lightModel, BooleanStateStack[] enabledLights, LightState[] lights) {
        Matrix3f normalMatrix = itemOrientation(modelView);
        Vector3f normal = new Vector3f();
        Vector3f lightDirection = new Vector3f();
        Vector3f factor = new Vector3f();
        for (int index = firstVertex; index < vertexCount; index++) {
            int vertexOffset = index * format.getSize();
            int normalOffset = vertexOffset + format.getNormalOffset();
            normal.set(vertices.get(normalOffset), vertices.get(normalOffset + 1), vertices.get(normalOffset + 2))
                .div(127.0f);
            normalMatrix.transform(normal);

            factor.set(lightModel.ambient.x, lightModel.ambient.y, lightModel.ambient.z);
            for (int lightIndex = 0; lightIndex < VertexKey.FFP_LIGHT_COUNT; lightIndex++) {
                if (!enabledLights[lightIndex].isEnabled()) {
                    continue;
                }
                LightState light = lights[lightIndex];
                lightDirection.set(light.position.x, light.position.y, light.position.z).normalize();
                float diffuse = Math.max(0.0f, normal.dot(lightDirection));
                factor.add(light.ambient.x + light.diffuse.x * diffuse,
                    light.ambient.y + light.diffuse.y * diffuse, light.ambient.z + light.diffuse.z * diffuse);
            }

            int colorOffset = vertexOffset + format.getColorOffset();
            multiplyChannel(vertices, colorOffset, factor.x);
            multiplyChannel(vertices, colorOffset + 1, factor.y);
            multiplyChannel(vertices, colorOffset + 2, factor.z);
        }
    }

    private static Matrix3f itemOrientation(Matrix4fc modelView) {
        // The polar factor keeps rotations and reflections on both sides of the flattening scale.
        Matrix3f orientation = modelView.get3x3(new Matrix3f());
        Matrix3f inverseTranspose = new Matrix3f();
        for (int i = 0; i < 6; i++) {
            orientation.invert(inverseTranspose).transpose();
            float scale = (float) Math.sqrt(frobeniusNorm(inverseTranspose) / frobeniusNorm(orientation));
            orientation.scale(0.5f * scale).add(inverseTranspose.scale(0.5f / scale));
        }
        return orientation;
    }

    private static double frobeniusNorm(Matrix3f matrix) {
        return Math.sqrt(matrix.m00 * matrix.m00 + matrix.m01 * matrix.m01 + matrix.m02 * matrix.m02
            + matrix.m10 * matrix.m10 + matrix.m11 * matrix.m11 + matrix.m12 * matrix.m12
            + matrix.m20 * matrix.m20 + matrix.m21 * matrix.m21 + matrix.m22 * matrix.m22);
    }

    private static void multiplyChannel(ByteBuffer vertices, int index, float factor) {
        int color = Byte.toUnsignedInt(vertices.get(index));
        vertices.put(index, (byte) Math.round(Math.clamp(color * factor, 0.0f, 255.0f)));
    }
}
