package com.dhj.actinium.test.shadow;

import com.dhj.actinium.test.launch.ShadowFastTesrTweaker;
import net.minecraft.client.renderer.BufferBuilder;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.spongepowered.asm.service.clean.ICleanMixinService;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ShadowFastTesrIntegrationTest {
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"dispatch-scope", "main-batch", "ordinary-shadow", "already-building",
        "mixed-model", "empty-batch", "callback-failure", "upload-failure", "nested-fast", "nested-ordinary",
        "nested-callback-failure", "nested-upload-failure", "existing-batch", "nbt-ids", "provider",
        "buffer-reuse", "pending-id"})
    void runsTransformedDispatcherWithoutAClient(String scenario, @TempDir Path directory) throws Exception {
        Path cleanMix = Path.of(ICleanMixinService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path host = Path.of(BufferBuilder.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String dependencies = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .map(entry -> Path.of(entry).toAbsolutePath().normalize().toString()).collect(Collectors.joining(File.pathSeparator));
        String classpath = cleanMix + File.pathSeparator + host + File.pathSeparator + dependencies;
        Path arguments = directory.resolve("java.args");
        Files.write(arguments, List.of("-Dfoundation.loadsall=true", "-Doutlands.install=false", "-Djava.awt.headless=true",
            "-Dactinium.shadowScenario=" + scenario,
            "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
            "-cp", "\"" + classpath.replace('\\', '/') + "\"", "top.outlands.foundation.boot.Foundation",
            "--tweakClass", ShadowFastTesrTweaker.class.getName(), "--gameDir", ".", "--mixin", "ShadowFastTesr.production.json",
            "--mixin", "ShadowFastTesr.fixture.json"));
        Path output = directory.resolve("output.log");
        Process process = new ProcessBuilder(ProcessHandle.current().info().command().orElseThrow(), "@" + arguments)
            .directory(directory.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            boolean finished = process.waitFor(90, TimeUnit.SECONDS);
            if (!finished) process.destroyForcibly().waitFor();
            String diagnostics = Files.readString(output);
            assertTrue(finished, () -> "Dispatcher probe timed out:\n" + diagnostics);
            assertEquals(0, process.exitValue(), diagnostics);
            assertEquals(scenario, Files.readString(directory.resolve("shadow-fast-tesr.ok")), diagnostics);
        } finally {
            if (process.isAlive()) process.destroyForcibly().waitFor();
        }
    }
}
