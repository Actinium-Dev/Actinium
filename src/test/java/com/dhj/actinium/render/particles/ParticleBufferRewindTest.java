package com.dhj.actinium.render.particles;

import com.dhj.actinium.mixinterface.features.iris.ParticleVertexRange;
import dhj.embeddedt.embeddium.api.shader.buffer.BufferBuilderExtension;
import dhj.embeddedt.embeddium.api.shader.buffer.VanillaQuadContext;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.launch.MixinTweaker;
import org.spongepowered.asm.service.clean.ICleanMixinService;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParticleBufferRewindTest {
    @Test
    void rewindsAnOutlandsBufferWithTheProductionMixins(@TempDir Path directory) throws Exception {
        Path arguments = directory.resolve("java.args");
        Path cleanMix = Path.of(ICleanMixinService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path host = Path.of(BufferBuilder.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        // Foundation must discover the host's CleanMix service before any copy bundled with a test mod.
        String testClasspath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .map(entry -> Path.of(entry).toAbsolutePath().normalize().toString()).collect(Collectors.joining(File.pathSeparator));
        String classpath = cleanMix + File.pathSeparator + host + File.pathSeparator + testClasspath;
        Files.write(arguments, List.of("-Dfoundation.loadsall=true", "-Doutlands.install=false", "-Djava.awt.headless=true",
            "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
            "-cp", "\"" + classpath.replace('\\', '/') + "\"",
            "top.outlands.foundation.boot.Foundation", "--tweakClass", Tweaker.class.getName(),
            "--gameDir", ".", "--mixin", "particle-buffer-rewind.mixins.json"));
        Path output = directory.resolve("output.log");
        Process process = new ProcessBuilder(ProcessHandle.current().info().command().orElseThrow(), "@" + arguments)
            .directory(directory.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) process.destroyForcibly().waitFor();
            String diagnostics = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
            assertTrue(finished, "Particle buffer test VM timed out:\n" + diagnostics);
            assertEquals(0, process.exitValue(), diagnostics);
            assertTrue(Files.exists(directory.resolve("rewind.ok")), "Probe did not finish:\n" + diagnostics);
        } finally {
            if (process.isAlive()) process.destroyForcibly().waitFor();
        }
    }

    /** Selects a buffer-only entry point after the loader applies the test's two production mixins. */
    public static class Tweaker extends MixinTweaker {
        static {
            FMLLaunchHandler.setSide(Side.CLIENT);
            Launch.blackboard.put("forgeLaunchArgs", Map.of());
        }

        @Override
        public String getLaunchTarget() {
            return Probe.class.getName();
        }
    }

    public static class Probe {
        public static void main(String[] args) throws IOException {
            int stride = DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP.getSize();
            // Forge reserves one extra vertex on a bulk write. Three quads and that vertex fit exactly.
            BufferBuilder run = new BufferBuilder((3 * 4 + 1) * stride / Integer.BYTES);
            ParticleVertexRange range = (ParticleVertexRange) run;
            run.begin(GL11.GL_QUADS, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP);
            writeQuad(run, 10);
            int first = run.getVertexCount();
            int[] prefix = run.getVertexState().getRawBuffer();
            run.addVertexData(prefix);
            run.putBulkData(ByteBuffer.allocate(4 * stride));
            assertTrue(range.actinium$canRewindParticleVertices());
            range.actinium$rewindParticleVertices(first);
            assertEquals(first, run.getVertexCount());
            ByteBuffer storage = run.getByteBuffer();
            assertEquals(first * stride, storage.position());
            run.addVertexData(new int[2 * prefix.length]);
            assertSame(storage, run.getByteBuffer(), "The rewound int view has room for two more quads");
            range.actinium$rewindParticleVertices(first);
            assertArrayEquals(prefix, run.getVertexState().getRawBuffer());
            writeQuad(run, 20);
            run.finishDrawing();
            assertEquals(2 * first * stride, run.getByteBuffer().remaining());
            assertEquals(20f, run.getByteBuffer().getFloat(first * stride));
            assertFalse(range.actinium$canRewindParticleVertices());

            run.begin(GL11.GL_QUADS, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP);
            BufferBuilderExtension extension = (BufferBuilderExtension) run;
            VanillaQuadContext context = new VanillaQuadContext(1, 2, 3, 4, (short) 0, (byte) 0);
            extension.actinium$setActiveQuadContext(context);
            assertFalse(range.actinium$canRewindParticleVertices());
            run.addVertexData(prefix);
            extension.actinium$setActiveQuadContext(null);
            assertFalse(range.actinium$canRewindParticleVertices());
            assertThrows(IllegalStateException.class, () -> range.actinium$rewindParticleVertices(0));
            assertEquals(List.of(context), extension.actinium$copyQuadContexts());
            assertArrayEquals(prefix, run.getVertexState().getRawBuffer());
            run.finishDrawing();

            run.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
            assertFalse(range.actinium$canRewindParticleVertices());
            assertThrows(IllegalStateException.class, () -> range.actinium$rewindParticleVertices(0));
            Files.createFile(Path.of("rewind.ok"));
        }

        private static void writeQuad(BufferBuilder run, int x) {
            for (int i = 0; i < 4; i++) {
                run.pos(x, i, 0).tex(i & 1, i >> 1).color(11, 12, 13, 255).lightmap(240, 16).endVertex();
            }
        }
    }
}
