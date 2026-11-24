package com.gtnewhorizons.angelica.glsm.ffp;

import com.google.common.collect.ImmutableSet;
import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMEnable;
import com.gtnewhorizons.angelica.glsm.GLSMLighting;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.GLStateManagerIntegrationFixture;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.backend.WindowBackends;
import net.coderbot.iris.gbuffer_overrides.matching.InputAvailability;
import net.coderbot.iris.gl.program.Program;
import net.coderbot.iris.gl.program.ProgramBuilder;
import net.coderbot.iris.pipeline.transform.PatchShaderType;
import net.coderbot.iris.pipeline.transform.TransformPatcher;
import net.coderbot.iris.uniforms.IrisInternalUniforms;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;

import static net.coderbot.iris.gl.uniform.UniformUpdateFrequency.ONCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the weather instance layout and FFP output against a real GL context. */
class WeatherInstancingGLTest extends GLStateManagerIntegrationFixture {

    private static final int SIZE = 64;
    private static final int BACKGROUND = 0xFF000000;

    /** Rain alpha of the column at the sampled distance: (1 - 0.25) * 0.5 + 0.5. */
    private static final int RAIN_ALPHA = 223;

    private static final int STRIDE = WeatherInstancedAttribs.STRIDE;

    /** Raw lightmap coordinate of sky light 4 and block light 12, in the units vanilla packs. */
    private static final float RAW_SKY = 4 * 16.0F;
    private static final float RAW_BLOCK = 12 * 16.0F;

    /** Texel the lightmap texture matrix maps the sky coordinate to. */
    private static final int LIGHTMAP_SKY_TEXEL = 4;
    private static final int LIGHTMAP_BLOCK_TEXEL = 12;

    /** Every texel step encodes 17 levels, so a read back channel names the sampled texel. */
    private static final int TEXEL_STEP = 17;

    private int instanceBuffer;
    private int rainTexture;
    private int lightmapTexture;

    @BeforeEach
    void setUp() {
        installDefaultGlsmConfig();
        WindowBackends.useSdl();
        GLStateManager.bindDisplayContext(new Object(), context);
        GLStateManager.recreateDefaultVertexArray();

        ShaderManager.enable();
        ShaderManager.getInstance().activate();
        GLSMRasterizer.glViewport(0, 0, SIZE, SIZE);
        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glOrtho(-1.0, 1.0, -1.0, 1.0, -1.0, 1.0);
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glLoadIdentity();
        GLSMEnable.glDisable(GL11.GL_DEPTH_TEST);
        GLSMEnable.glDisable(GL11.GL_BLEND);
        GLSMEnable.glDisable(GL11.GL_CULL_FACE);
        GLSMEnable.glDisable(GL11.GL_LIGHTING);

        rainTexture = whiteTexture();
        lightmapTexture = lightmapTexture();
        bindRainTexture(rainTexture);

        instanceBuffer = GLSMBuffers.glGenBuffers();
    }

    @AfterEach
    void tearDown() {
        WeatherQuadMesh.delete();
        if (instanceBuffer != 0) { GLSMBuffers.glDeleteBuffers(instanceBuffer); instanceBuffer = 0; }
        GLSMEnable.glDisable(GL11.GL_LIGHTING);
        disableLightmapUnit();
        bindRainTexture(0);
        GLSMEnable.glDisable(GL11.GL_TEXTURE_2D);
        if (rainTexture != 0) { GLSMTextures.glDeleteTextures(rainTexture); rainTexture = 0; }
        if (lightmapTexture != 0) { GLSMTextures.glDeleteTextures(lightmapTexture); lightmapTexture = 0; }
        GLSMPrograms.setFfpInstancing(Instancing.NONE);
        final ShaderManager sm = ShaderManager.getInstance();
        if (sm.isActive()) sm.deactivate();
        GLSMVertexInput.glBindVertexArray(0);
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glLoadIdentity();
        ShaderManager.disable();
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    @Test
    void theActiveBackendDrawsInstances() {
        assertTrue(BackendManager.RENDER_BACKEND.supportsInstancedDrawing(),
            "the instanced weather path needs a backend that draws instances: "
                + BackendManager.RENDER_BACKEND.getName());
    }

    @Test
    void rainColumnCoversItsQuadWithTheVanillaTransparency() {
        final int[] pixels = drawColumn(rainInstance(0.0F, -0.5F, -0.5F, 0.5F), 1.0F, 0);

        assertEquals(RAIN_ALPHA, alphaAt(pixels, 48, 32), 2, "centre of the column");
        assertEquals(255, redAt(pixels, 40, 24), "the bottom-left of the strip is drawn");
        assertEquals(255, redAt(pixels, 56, 40), "the top-right of the strip is drawn");
        assertEquals(BACKGROUND, pixels[8 * SIZE + 8], "outside the column");
    }

    @Test
    void transparencyFollowsTheRainStrengthUniform() {
        final int full = alphaAt(drawColumn(rainInstance(0.0F, -0.5F, -0.5F, 0.5F), 1.0F, 0), 48, 32);
        final int half = alphaAt(drawColumn(rainInstance(0.0F, -0.5F, -0.5F, 0.5F), 0.5F, 0), 48, 32);

        assertEquals(RAIN_ALPHA, full, 2);
        assertEquals(full / 2, half, 3, "the strength reaches the shader through WeatherParams");
    }

    @Test
    void negativeVanillaRainAlphaWrapsLikeTheByteVertexWriter() {
        WeatherParams.set(0, 0, 0, 3, 0.5F, 0.5F, 0, 0, 0, 1, 0, 0, 1);
        final int[] pixels = drawInstances(rainInstance(0, -0.5F, -0.5F, 0.5F), 0, 1);
        final float opacity = (1.0F - 1.5F * 1.5F) * 0.5F + 0.5F;
        assertEquals((int) (opacity * 255.0F) & 0xFF, alphaAt(pixels, 48, 32));
    }

    @Test
    void snowColumnUsesTheSnowTransparency() {
        final int rain = alphaAt(drawColumn(rainInstance(0.0F, -0.5F, -0.5F, 0.5F), 1.0F, 0), 48, 32);
        final int snow = alphaAt(drawColumn(snowInstance(0.0F, -0.5F, -0.5F, 0.5F), 1.0F, 0), 48, 32);

        assertEquals(RAIN_ALPHA, rain, 2);
        assertEquals(184, snow, 3, "(1 - 0.25) * 0.3 + 0.5");
        assertTrue(snow < rain, "snow is drawn less opaque than rain");
    }

    @Test
    void legacyLightingKeepsTheWeatherTransparency() {
        GLSMEnable.glEnable(GL11.GL_LIGHTING);
        try {
            final int[] pixels = drawColumn(rainInstance(0.0F, -0.5F, -0.5F, 0.5F), 1.0F, 0);

            assertEquals(RAIN_ALPHA, alphaAt(pixels, 48, 32), 2,
                "the weather colour does not depend on the lighting state");
            assertEquals(255, redAt(pixels, 48, 32));
        } finally {
            GLSMEnable.glDisable(GL11.GL_LIGHTING);
        }
    }

    @Test
    void separateSpecularDoesNotLeakIntoTheWeatherColour() {
        // Lighting plus GL_SEPARATE_SPECULAR_COLOR makes the fragment stage declare and add
        // v_SpecularColor. The weather variant never runs the lighting branch, so it has to write a
        // zero specular itself instead of leaving the varying undefined.
        GLSMEnable.glEnable(GL11.GL_LIGHTING);
        GLSMLighting.glLightModeli(GL12.GL_LIGHT_MODEL_COLOR_CONTROL, GL12.GL_SEPARATE_SPECULAR_COLOR);
        try {
            final int[] pixels = drawColumn(rainInstance(0.0F, -0.5F, -0.5F, 0.5F), 1.0F, 0);

            assertEquals(RAIN_ALPHA, alphaAt(pixels, 48, 32), 2, "transparency survives the lit pass");
            assertEquals(255, redAt(pixels, 48, 32), "the weather colour is not replaced by lighting");
        } finally {
            GLSMLighting.glLightModeli(GL12.GL_LIGHT_MODEL_COLOR_CONTROL, GL12.GL_SINGLE_COLOR);
            GLSMEnable.glDisable(GL11.GL_LIGHTING);
        }
    }

    @Test
    void lightmapIsSampledAsBlockThenSkyThroughTheTextureMatrix() {
        enableLightmapUnit();

        final int[] pixels = drawColumn(rainInstance(0.0F, -0.5F, -0.5F, 0.5F, RAW_BLOCK, RAW_SKY), 1.0F, 0);

        assertEquals(LIGHTMAP_BLOCK_TEXEL * TEXEL_STEP, redAt(pixels, 48, 32),
            "the low block-light word selects the texture column");
        assertEquals(LIGHTMAP_SKY_TEXEL * TEXEL_STEP, greenAt(pixels, 48, 32),
            "the high sky-light word selects the texture row");
        assertEquals(RAIN_ALPHA, alphaAt(pixels, 48, 32), 2, "the lightmap does not change the transparency");
    }

    @Test
    void rainScrollFollowsTheVanillaSign() {
        GLSMTextures.glDeleteTextures(rainTexture);
        rainTexture = gradientTexture();
        bindRainTexture(rainTexture);

        final int scrolled = redAt(drawColumn(rainInstance(0.0F, -0.5F, -0.5F, 0.5F), 1.0F, 2), 48, 32);
        final int unscrolled = redAt(drawColumn(rainInstance(0.0F, -0.5F, -0.5F, 0.5F), 1.0F, 0), 48, 32);

        // The upper vertices sample the lower column bound's V in vanilla.
        // V = -0.00390625 - (mod(2 + 0, 32) + 0) / 32 * (3 + 0.5) = -0.22265625.
        // The repeated coordinate is 0.77734375, which selects row 12.
        assertEquals(12 * TEXEL_STEP, scrolled, "the scroll moves the texture the vanilla way");
        assertEquals(15 * TEXEL_STEP, unscrolled, "vanilla reverses V between the top and bottom vertices");
    }

    @Test
    void rainTextureVMatchesVanillaVertexHeights() {
        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glOrtho(-0.5, 0.5, 0.0, 1.0, -1.0, 1.0);
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMTextures.glDeleteTextures(rainTexture);
        rainTexture = gradientTexture();
        bindRainTexture(rainTexture);

        final int[] pixels = drawColumn(rainInstance(0.0F, -0.5F, 0.0F, 1.0F), 1.0F, 0);

        for (int y : new int[]{16, 48}) {
            final float worldY = (y + 0.5F) / SIZE;
            final float vanillaV = (1.0F - worldY) * 0.25F;
            assertEquals((int) (vanillaV * 16.0F) * TEXEL_STEP, redAt(pixels, 32, y),
                "the vertex at world height " + worldY + " uses the vanilla texture V");
        }
    }

    @Test
    void cameraColumnCollapsesWithoutProducingNaN() {
        final int[] pixels = drawColumn(rainInstance(0.0F, 0.0F, -0.5F, 0.5F), 1.0F, 0);

        for (int pixel : pixels) {
            assertEquals(BACKGROUND, pixel, "a zero length column must not paint anything");
        }
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    @Test
    void deleteReleasesTheQuadMesh() {
        final int vao = WeatherQuadMesh.vao();
        assertTrue(GLSMVertexInput.glIsVertexArray(vao), "the mesh exists before the release");

        WeatherQuadMesh.delete();

        assertFalse(GLSMVertexInput.glIsVertexArray(vao), "the VAO is released");
        assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
    }

    @Test
    void providerUsesItsOwnRadiusAndTintWithoutVanillaRain() {
        final ByteBuffer data = BufferUtils.createByteBuffer(2 * STRIDE);
        WeatherInstancedAttribs.putColumn(data, 0, 0.0F, 0.0F, -0.5F, 0.5F,
            0, 0, 0, 0, 0, 0, 0, WeatherInstancedAttribs.SOURCE_VANILLA,
            1, 1, 1, 1, WeatherInstancedAttribs.RAIN_PERIOD, 0, 0, 0);
        WeatherInstancedAttribs.putColumn(data, 1, 0.0F, -0.5F, -0.5F, 0.5F,
            0, 0, 0, 0, 0, 0, 0, WeatherInstancedAttribs.SOURCE_PROVIDER,
            0.25F, 0.5F, 0.75F, 0.5F, WeatherInstancedAttribs.SNOW_PERIOD, 0, 0, 0.5F);

        WeatherParams.set(0, 0, 0, 1, 0.5F, 0.5F, 0, 0, 0, 0, 0, 0, 0.5F);
        final int[] pixels = drawInstances(data, STRIDE, 1);
        assertEquals(63, redAt(pixels, 48, 32), 1);
        assertEquals(127, greenAt(pixels, 48, 32), 1);
        assertEquals(123, alphaAt(pixels, 48, 32), 1, "provider fade uses its own radius and alpha");
    }

    @Test
    void irisVariantUploadsProviderParamsAndDrawsTheColumn() {
        RenderSystem.initializeGlslCapabilityForTesting(330);
        final var patched = TransformPatcher.patchAttributesInstanced("""
            #version 120
            varying vec4 vertexColor;
            varying vec2 weatherUv;
            void main() {
                gl_Position = ftransform();
                vertexColor = gl_Color;
                weatherUv = gl_MultiTexCoord0.xy;
            }
            """, null, null, null, """
            #version 120
            varying vec4 vertexColor;
            varying vec2 weatherUv;
            void main() {
                gl_FragData[0] = vec4(vertexColor.r, fract(weatherUv.y), vertexColor.b, vertexColor.a);
            }
            """, InputAvailability.of(true, true), Instancing.WEATHER);
        final ProgramBuilder builder = ProgramBuilder.begin("weatherTest", patched.get(PatchShaderType.VERTEX),
            null, patched.get(PatchShaderType.FRAGMENT), ImmutableSet.of());
        builder.uniformMatrix(ONCE, "iris_ModelViewMatrix", Matrix4f::new);
        builder.uniformMatrix(ONCE, "iris_ProjectionMatrix", Matrix4f::new);
        builder.uniform4f(ONCE, "iris_ColorModulator", () -> new Vector4f(1.0F));
        IrisInternalUniforms.addWeatherUniforms(builder);

        final ByteBuffer data = BufferUtils.createByteBuffer(STRIDE);
        WeatherInstancedAttribs.putColumn(data, 0, 0, -0.5F, -0.5F, 0.5F,
            0, 0, 0, 1, 0, 2, 1, WeatherInstancedAttribs.SOURCE_PROVIDER,
            0.25F, 1, 1, 0.5F, 32, 0, 0.01F, 0.5F);
        WeatherParams.set(0, 0, 0, 1, 0.5F, 0.5F, 0.25F, 0, 0, 0, 8, 8.25F, 0.5F);
        final Program program = builder.build();
        try {
            program.use();
            final int[] pixels = drawInstances(data, 0, 1);
            assertEquals(63, redAt(pixels, 48, 32), 1);
            assertEquals(104, greenAt(pixels, 48, 32), 1, "provider scroll and drift use the provider clock");
            assertEquals(123, alphaAt(pixels, 48, 32), 1, "provider fade uses its own radius and alpha");
        } finally {
            Program.unbind();
            program.destroy();
        }
    }

    private int[] drawColumn(ByteBuffer instance, float rainStrength, int rainScroll) {
        WeatherParams.set(0, 0, 0, 1, 0.5F, 0.5F, 0, 0, rainScroll, rainStrength, 0, 0, 1);
        return drawInstances(instance, 0, 1);
    }

    private int[] drawInstances(ByteBuffer data, long offset, int count) {
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceBuffer);
        GLSMBuffers.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
        clear();
        GLSMVertexInput.glBindVertexArray(WeatherQuadMesh.vao());
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, instanceBuffer);
        WeatherInstancedAttribs.pointInstanceAttribs(offset);
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GLStateManager.getVaoManager().setVertexFlags(WeatherQuadMesh.VERTEX_FLAGS);
        GLSMPrograms.setFfpInstancing(Instancing.WEATHER);
        GLSMDraws.glDrawArraysInstanced(GL11.GL_TRIANGLE_STRIP, 0, WeatherQuadMesh.VERTEX_COUNT, count);
        GLSMPrograms.setFfpInstancing(Instancing.NONE);
        GLSMVertexInput.glBindVertexArray(0);
        return readRegion();
    }

    private static ByteBuffer rainInstance(float columnX, float columnZ, float bottom, float top) {
        return instance(columnX, columnZ, bottom, top, 0.0F, 240.0F, 240.0F);
    }

    private static ByteBuffer rainInstance(float columnX, float columnZ, float bottom, float top,
                                          float blockLight, float skyLight) {
        return instance(columnX, columnZ, bottom, top, 0.0F, blockLight, skyLight);
    }

    private static ByteBuffer snowInstance(float columnX, float columnZ, float bottom, float top) {
        return instance(columnX, columnZ, bottom, top, 1.0F, 240.0F, 240.0F);
    }

    private static ByteBuffer instance(float columnX, float columnZ, float bottom, float top, float snow,
                                       float blockLight, float skyLight) {
        final ByteBuffer data = BufferUtils.createByteBuffer(STRIDE);
        final boolean snowColumn = snow != 0.0F;
        final int light = ((int) skyLight << 16) | (int) blockLight;
        WeatherInstancedAttribs.putColumn(data, 0, columnX, columnZ, bottom, top,
            0, 0, 0, 0, light, 0, snowColumn ? 1.0F : WeatherInstancedAttribs.RAIN_SCROLL_BASE + 0.5F,
            WeatherInstancedAttribs.SOURCE_VANILLA, 1, 1, 1, 1,
            snowColumn ? WeatherInstancedAttribs.SNOW_PERIOD : WeatherInstancedAttribs.RAIN_PERIOD,
            0, 0, snowColumn ? WeatherInstancedAttribs.SNOW_FADE : WeatherInstancedAttribs.RAIN_FADE);
        return data;
    }

    private void bindRainTexture(int texture) {
        GLStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        if (texture != 0) {
            GLSMEnable.glEnable(GL11.GL_TEXTURE_2D);
        } else {
            GLSMEnable.glDisable(GL11.GL_TEXTURE_2D);
        }
    }

    /**
     * Binds a 16 texel lightmap with the vanilla lightmap texture matrix: the packed coordinate is
     * scaled by {@code 1 / 256} and offset by half a texel, so raw light 4 reads texel 4.
     */
    private void enableLightmapUnit() {
        GLStateManager.setActiveTexture(GL13.GL_TEXTURE1);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, lightmapTexture);
        GLSMEnable.glEnable(GL11.GL_TEXTURE_2D);
        GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glScalef(1.0F / 256.0F, 1.0F / 256.0F, 1.0F / 256.0F);
        GLSMMatrices.glTranslatef(8.0F, 8.0F, 8.0F);
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.setActiveTexture(GL13.GL_TEXTURE0);
    }

    private void disableLightmapUnit() {
        GLStateManager.setActiveTexture(GL13.GL_TEXTURE1);
        GLSMEnable.glDisable(GL11.GL_TEXTURE_2D);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GLSMMatrices.glMatrixMode(GL11.GL_TEXTURE);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.setActiveTexture(GL13.GL_TEXTURE0);
    }

    private static int whiteTexture() {
        final ByteBuffer white = BufferUtils.createByteBuffer(16 * 16 * 4);
        for (int i = 0; i < 16 * 16; i++) {
            white.put((byte) 255).put((byte) 255).put((byte) 255).put((byte) 255);
        }
        white.flip();
        return upload(white);
    }

    /**
     * 16 by 16 texture whose red channel is the column and green channel the row, so a read back pixel
     * names the texel the lightmap coordinate selected.
     */
    private static int lightmapTexture() {
        final ByteBuffer data = BufferUtils.createByteBuffer(16 * 16 * 4);
        for (int row = 0; row < 16; row++) {
            for (int column = 0; column < 16; column++) {
                data.put((byte) (column * TEXEL_STEP)).put((byte) (row * TEXEL_STEP)).put((byte) 0).put((byte) 255);
            }
        }
        data.flip();
        return upload(data);
    }

    /**
     * 16 by 16 texture whose red channel is the row, used to read back the sampled texture V.
     */
    private static int gradientTexture() {
        final ByteBuffer data = BufferUtils.createByteBuffer(16 * 16 * 4);
        for (int row = 0; row < 16; row++) {
            for (int column = 0; column < 16; column++) {
                data.put((byte) (row * TEXEL_STEP)).put((byte) 0).put((byte) 0).put((byte) 255);
            }
        }
        data.flip();
        return upload(data);
    }

    /**
     * Creates a 16 by 16 texture. Binding goes through GLSM because the FFP fragment key treats an
     * enabled unit without a tracked binding as disabled, and a raw bind would leave the texture out
     * of the shader.
     */
    private static int upload(ByteBuffer data) {
        final int id = GL11.glGenTextures();
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 16, 16, 0, GL11.GL_RGBA,
            GL11.GL_UNSIGNED_BYTE, data);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        return id;
    }

    private static void clear() {
        GLStateManager.glClearColor(0.0F, 0.0F, 0.0F, 1.0F);
        GLSMFramebuffer.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    private static int[] readRegion() {
        final ByteBuffer pixels = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
        GLSMFramebuffer.glReadPixels(0, 0, SIZE, SIZE, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        final int[] out = new int[SIZE * SIZE];
        for (int i = 0; i < out.length; i++) {
            out[i] = pixels.getInt(i * 4);
        }
        return out;
    }

    private static int alphaAt(int[] pixels, int x, int y) {
        return pixels[y * SIZE + x] >>> 24 & 0xFF;
    }

    private static int redAt(int[] pixels, int x, int y) {
        return pixels[y * SIZE + x] & 0xFF;
    }

    private static int greenAt(int[] pixels, int x, int y) {
        return pixels[y * SIZE + x] >> 8 & 0xFF;
    }
}
