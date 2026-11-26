package com.gtnewhorizons.angelica.sdlgpu.device;

import com.gtnewhorizons.angelica.glsm.ffp.ClipDistanceGlsl;
import com.gtnewhorizons.angelica.sdlgpu.SDLGPUGate;
import com.gtnewhorizons.angelica.sdlgpu.SDLGPURenderBackend;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.PipelineApplier;
import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import com.gtnewhorizons.angelica.sdlgpu.resource.TextureTransfers;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.sdl.SDL_GPUColorTargetDescription;
import org.lwjgl.sdl.SDL_GPUColorTargetInfo;
import org.lwjgl.sdl.SDL_GPUGraphicsPipelineCreateInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.sdl.SDLError;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.sdl.SDLGPU.SDL_BeginGPURenderPass;
import static org.lwjgl.sdl.SDLGPU.SDL_BindGPUGraphicsPipeline;
import static org.lwjgl.sdl.SDLGPU.SDL_CreateGPUGraphicsPipeline;
import static org.lwjgl.sdl.SDLGPU.SDL_DrawGPUPrimitives;
import static org.lwjgl.sdl.SDLGPU.SDL_EndGPURenderPass;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_LOADOP_CLEAR;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_PRIMITIVETYPE_TRIANGLELIST;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_SHADERSTAGE_FRAGMENT;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_SHADERSTAGE_VERTEX;
import static org.lwjgl.sdl.SDLGPU.SDL_GPU_STOREOP_STORE;
import static org.lwjgl.sdl.SDLGPU.SDL_ReleaseGPUGraphicsPipeline;
import static org.lwjgl.sdl.SDLGPU.SDL_WaitForGPUIdle;
import static org.lwjgl.sdl.SDLInit.SDL_INIT_VIDEO;
import static org.lwjgl.sdl.SDLInit.SDL_InitSubSystem;
import static org.lwjgl.sdl.SDLInit.SDL_QuitSubSystem;

@Tag("sdl-gpu-device")
class DeviceClipDistanceTest {
    private Device device;

    @BeforeEach
    void openDeviceWithoutNativeClipping() {
        assertTrue(SDL_InitSubSystem(SDL_INIT_VIDEO), SDLError::SDL_GetError);
        device = SDLGPUGate.device();
        assertTrue(device.createDevice(false), SDLError::SDL_GetError);
    }

    @AfterEach
    void closeDevice() {
        if (device != null) device.shutdown();
        SDL_QuitSubSystem(SDL_INIT_VIDEO);
    }

    @Test
    void deviceCreatedWithoutNativeClipDistancesReportsThemUnavailable() {
        assertFalse(device.hasClipDistance());
        assertEquals(0, new SDLGPURenderBackend().getInteger(GL30.GL_MAX_CLIP_DISTANCES));
    }

    @Test
    void clipEquationUploadedToAnArrayElementSelectsPixelsOnTheDevice() {
        final ShaderManager shaders = new ShaderManager(device);
        final FrameManager frames = new FrameManager(device);
        final ResourceManager resources = new ResourceManager(device, frames);
        frames.setResourceManager(resources);
        final PipelineApplier applier = new PipelineApplier(frames, resources, shaders, null, null, null, null, null, null, null);
        final ContextState state = new ContextState();
        final ByteBuffer pixels = MemoryUtil.memAlloc(8 * 8 * 4);
        long pipeline = 0;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final int program = shaders.createProgram();
            final int vertex = shaders.createShader(GL20.GL_VERTEX_SHADER);
            final int fragment = shaders.createShader(GL20.GL_FRAGMENT_SHADER);
            final StringBuilder source = new StringBuilder("#version 460 core\nuniform vec4 planes[8];\n")
                .append(ClipDistanceGlsl.VS_OUT_DIST0).append(ClipDistanceGlsl.VS_OUT_DIST1)
                .append("void main() { vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);")
                .append("vec4 eye = vec4(p * 2.0 - 1.0, 0.0, 1.0); gl_Position = eye;");
            final String[] distances = new String[8];
            for (int i = 0; i < distances.length; i++) distances[i] = "dot(eye, planes[" + i + "])";
            ClipDistanceGlsl.appendVsAssign(source, distances);
            shaders.shaderSource(vertex, source.append('}'));
            shaders.shaderSource(fragment, "#version 460 core\n" + ClipDistanceGlsl.FS_IN_DIST0 + ClipDistanceGlsl.FS_IN_DIST1
                + "layout(location = 0) out vec4 color; void main() {" + ClipDistanceGlsl.FS_DISCARD + "color = vec4(1.0); }");
            shaders.compileShader(vertex);
            shaders.compileShader(fragment);
            shaders.attachShader(program, vertex);
            shaders.attachShader(program, fragment);
            shaders.linkProgram(program);
            assertTrue(shaders.isLinked(program), () -> shaders.getProgramInfoLog(program));
            final ShaderManager.ProgramObject linked = shaders.getProgram(program);
            state.boundProgramObj = linked;
            state.boundProgram = program;
            final int plane = shaders.getUniformLocation(program, "planes[7]");
            assertTrue(plane >= 0);
            linked.sdlVertexShader = shaders.createSDLShader(linked.vertexSpirv, SDL_GPU_SHADERSTAGE_VERTEX, 0, linked.vertexResources.numUBOs(), 0, 0);
            linked.sdlFragmentShader = shaders.createSDLShader(linked.fragmentSpirv, SDL_GPU_SHADERSTAGE_FRAGMENT, 0, linked.fragmentResources.numUBOs(), 0, 0);
            assertTrue(linked.sdlVertexShader != 0 && linked.sdlFragmentShader != 0, SDLError::SDL_GetError);
            final long texture = resources.createTexture(1, GL11.GL_TEXTURE_2D, GL11.GL_RGBA8, 8, 8, 1, 1);
            assertTrue(texture != 0, SDLError::SDL_GetError);
            final SDL_GPUColorTargetDescription.Buffer targets = SDL_GPUColorTargetDescription.calloc(1, stack);
            targets.get(0).format(resources.getTextureMeta(1).sdlFormat());
            final SDL_GPUGraphicsPipelineCreateInfo pipelineInfo = SDL_GPUGraphicsPipelineCreateInfo.calloc(stack)
                .vertex_shader(linked.sdlVertexShader).fragment_shader(linked.sdlFragmentShader)
                .primitive_type(SDL_GPU_PRIMITIVETYPE_TRIANGLELIST);
            pipelineInfo.target_info().num_color_targets(1).color_target_descriptions(targets);
            pipeline = SDL_CreateGPUGraphicsPipeline(device.getDevice(), pipelineInfo);
            assertTrue(pipeline != 0, SDLError::SDL_GetError);
            final SDL_GPUColorTargetInfo.Buffer attachments = SDL_GPUColorTargetInfo.calloc(1, stack);
            attachments.get(0).texture(texture).load_op(SDL_GPU_LOADOP_CLEAR).store_op(SDL_GPU_STOREOP_STORE);

            for (float normal : new float[]{1, -1, 0}) {
                frames.beginFrame();
                frames.frame().swapchainUnavailable = true;
                final long render = SDL_BeginGPURenderPass(frames.getCommandBuffer(), attachments, null);
                assertTrue(render != 0, SDLError::SDL_GetError);
                SDL_BindGPUGraphicsPipeline(render, pipeline);
                applier.putUniformFv(state, plane, FloatBuffer.wrap(new float[]{normal, 0, 0, 0}));
                applier.pushUniforms(render, state);
                SDL_DrawGPUPrimitives(render, 3, 1, 0, 0);
                SDL_EndGPURenderPass(render);
                frames.endFrame();

                TextureTransfers.download(device, resources, 1, 0, 0, 8, 8, 1, pixels);
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        boolean visible = normal == 0 || (normal > 0 ? x >= 4 : x < 4);
                        assertEquals(visible ? 255 : 0, Byte.toUnsignedInt(pixels.get((y * 8 + x) * 4)),
                            "plane normal " + normal + " at " + x + "," + y);
                    }
                }
            }
        } finally {
            SDL_WaitForGPUIdle(device.getDevice());
            if (frames.isFrameActive()) frames.endFrame();
            frames.releaseAllRegisteredFrames();
            if (pipeline != 0) SDL_ReleaseGPUGraphicsPipeline(device.getDevice(), pipeline);
            if (state.boundProgramObj != null) state.releaseUniformStaging(state.boundProgramObj);
            shaders.shutdown();
            resources.shutdown();
            MemoryUtil.nmemFree(state.vboBindingsAddr);
            state.eboBinding.free();
            state.cachedViewport.free();
            state.cachedScissor.free();
            state.cachedBlendColor.free();
            state.fragSamplerBindings.free();
            state.vertSamplerBindings.free();
            MemoryUtil.memFree(pixels);
        }
    }

    @Test
    void relinkingWithoutPlanesDropsTheirUniformLocations() {
        final ShaderManager shaders = new ShaderManager(device);
        try {
            final int program = shaders.createProgram();
            final int vertex = shaders.createShader(GL20.GL_VERTEX_SHADER);
            final int fragment = shaders.createShader(GL20.GL_FRAGMENT_SHADER);
            shaders.shaderSource(vertex, """
                #version 330 core
                uniform vec4 planes[2];
                void main() { gl_Position = planes[gl_VertexID % 2]; }
                """);
            shaders.shaderSource(fragment, """
                #version 330 core
                out vec4 color;
                void main() { color = vec4(1.0); }
                """);
            shaders.compileShader(vertex);
            shaders.compileShader(fragment);
            shaders.attachShader(program, vertex);
            shaders.attachShader(program, fragment);
            shaders.linkProgram(program);
            assertTrue(shaders.isLinked(program), () -> shaders.getProgramInfoLog(program));
            assertTrue(shaders.getUniformLocation(program, "planes[1]") >= 0);

            shaders.shaderSource(vertex, """
                #version 330 core
                void main() { gl_Position = vec4(0.0, 0.0, 0.0, 1.0); }
                """);
            shaders.compileShader(vertex);
            shaders.linkProgram(program);
            assertTrue(shaders.isLinked(program), () -> shaders.getProgramInfoLog(program));
            assertEquals(-1, shaders.getUniformLocation(program, "planes"));
            assertEquals(-1, shaders.getUniformLocation(program, "planes[1]"));
        } finally {
            shaders.shutdown();
        }
    }
}
