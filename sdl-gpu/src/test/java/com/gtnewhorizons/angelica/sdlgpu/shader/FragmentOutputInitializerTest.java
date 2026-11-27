package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.shader.ShaderCacheIO;
import com.gtnewhorizons.angelica.glsm.shader.SpirvCompiler;
import com.gtnewhorizons.angelica.sdlgpu.shader.msl.MslCrossCompile;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.util.spvc.Spv;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.lwjgl.system.MemoryUtil.memFree;

class FragmentOutputInitializerTest {
    @Test
    void initializesImplicitlySizedArrayAndIntegerTargetsBeforeOriginalCode() {
        final ByteBuffer source = compile("""
            #version 460 core
            layout(location = 0) out vec4 colors[];
            layout(location = 2) out uvec2 payload;
            void main() {
                vec4 value = vec4(gl_FragCoord.xy, 0.5, 1.0);
                if (value.x < 1.0) return;
                colors[1] = value;
                payload = uvec2(1u, 2u);
                gl_FragDepth = value.z;
                gl_SampleMask[0] = 1;
            }
            """);
        ByteBuffer patched = source;
        try {
            final byte[] originalBytes = ShaderCacheIO.toHeap(source);
            final Module original = inspect(source);
            patched = FragmentOutputInitializer.initialize(source);
            assertNotSame(source, patched);
            assertArrayEquals(originalBytes, ShaderCacheIO.toHeap(source));

            final Module actual = inspect(patched);
            assertEquals(2, actual.colors().size());
            final List<Store> newStores = actual.stores().subList(0, actual.colors().size());
            assertEquals(actual.colors(), newStores.stream().map(Store::target).collect(Collectors.toSet()));
            assertEquals(original.stores(), actual.stores().subList(newStores.size(), actual.stores().size()));
            for (Store store : newStores) {
                assertEquals(actual.entryBlock(), store.block());
                assertEquals(actual.outputTypes().get(store.target()), actual.nullTypes().get(store.value()));
            }

            final var msl = MslCrossCompile.compileAtVersion(patched, GL20.GL_FRAGMENT_SHADER, 20200);
            memFree(msl.code());
        } finally {
            if (patched != source) memFree(patched);
            memFree(source);
        }
    }

    @Test
    void leavesDepthAndSampleMaskOnlyShaderUntouched() {
        final ByteBuffer source = compile("""
            #version 460 core
            void main() {
                gl_FragDepth = 0.5;
                gl_SampleMask[0] = 1;
            }
            """);
        ByteBuffer patched = source;
        try {
            patched = FragmentOutputInitializer.initialize(source);
            assertSame(source, patched);
        } finally {
            if (patched != source) memFree(patched);
            memFree(source);
        }
    }

    private static ByteBuffer compile(String source) {
        final var result = SpirvCompiler.compile(source, Shaderc.shaderc_fragment_shader, "color-outputs.frag",
            SpirvCompiler.Options.vulkanForced460Core(), false);
        assertNotNull(result.spirv(), result::error);
        return result.spirv();
    }

    private record Store(int block, int target, int value) {}

    private record Module(Set<Integer> colors, Map<Integer, Integer> outputTypes, Map<Integer, Integer> nullTypes,
                          List<Store> stores, int entryBlock) {}

    private static Module inspect(ByteBuffer spirv) {
        final IntBuffer words = spirv.asIntBuffer();
        final Set<Integer> locations = new HashSet<>();
        final Map<Integer, Integer> pointeeTypes = new HashMap<>();
        final Map<Integer, Integer> outputTypes = new HashMap<>();
        final Map<Integer, Integer> nullTypes = new HashMap<>();
        final List<Store> stores = new ArrayList<>();
        final int headerWords = 5;
        int entryBlock = 0;
        int block = 0;
        for (int offset = headerWords; offset < words.limit();) {
            final int header = words.get(offset);
            switch (header & 0xffff) {
                case Spv.SpvOpDecorate -> {
                    if (words.get(offset + 2) == Spv.SpvDecorationLocation) locations.add(words.get(offset + 1));
                }
                case Spv.SpvOpTypePointer -> pointeeTypes.put(words.get(offset + 1), words.get(offset + 3));
                case Spv.SpvOpVariable -> {
                    if (words.get(offset + 3) == Spv.SpvStorageClassOutput) {
                        outputTypes.put(words.get(offset + 2), pointeeTypes.get(words.get(offset + 1)));
                    }
                }
                case Spv.SpvOpConstantNull -> nullTypes.put(words.get(offset + 2), words.get(offset + 1));
                case Spv.SpvOpLabel -> {
                    block = words.get(offset + 1);
                    if (entryBlock == 0) entryBlock = block;
                }
                case Spv.SpvOpStore -> stores.add(new Store(block, words.get(offset + 1), words.get(offset + 2)));
                default -> { }
            }
            offset += header >>> 16;
        }
        locations.retainAll(outputTypes.keySet());
        return new Module(locations, outputTypes, nullTypes, stores, entryBlock);
    }
}
