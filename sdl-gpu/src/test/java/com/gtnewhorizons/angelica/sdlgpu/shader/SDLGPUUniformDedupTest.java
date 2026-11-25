package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;
import com.gtnewhorizons.angelica.sdlgpu.pipeline.PipelineApplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.spvc.Spvc;

import java.nio.FloatBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SDLGPUUniformDedupTest {

    private PipelineApplier applier;
    private ContextState st;
    private ShaderManager.ProgramObject prog;
    private UniformStaging us;

    @BeforeEach
    void setUp() {
        applier = new PipelineApplier(null, null, null, null, null, null, null, null, null, null);
        st = new ContextState();
        prog = new ShaderManager.ProgramObject();
        prog.linked = true;
        prog.vertexUboSize = 64;
        prog.fragmentUboSize = 16;
        prog.nextUniformLocation = 3;
        for (int i = 0; i < 3; i++) {
            prog.vertexMemberInfo.put(i, new ShaderManager.UniformMemberInfo(
                i * 16, 16, 0, true, 4, 1, 0, 1));
        }
        prog.fragmentMemberInfo.put(2, new ShaderManager.UniformMemberInfo(0, 16, 0, false, 4, 1, 0, 1));
        prog.buildUniformSlotArrays();
        st.boundProgramObj = prog;
        st.boundProgram = 1;
        us = st.uniformStaging(prog);
    }

    @AfterEach
    void tearDown() {
        st.releaseUniformStaging(prog);
        MemoryUtil.nmemFree(st.vboBindingsAddr);
        st.eboBinding.free();
        st.cachedViewport.free();
        st.cachedScissor.free();
        st.cachedBlendColor.free();
        st.fragSamplerBindings.free();
        st.vertSamplerBindings.free();
    }

    @Test
    void singleElementArraySharesItsBaseLocationAndRejectsInvalidIndices() {
        final ShaderManager manager = new ShaderManager(null);
        final int program = manager.createProgram();
        final ShaderManager.ProgramObject arrayProgram = manager.getProgram(program);
        final ShaderManager.StageReflection reflection = reflection(32, List.of(
            new ShaderManager.UboMember("weights", 0, 16, 16, 1, 1, Spvc.SPVC_BASETYPE_FP32, 1),
            new ShaderManager.UboMember("exposure", 16, 4, 0, 1, 1, Spvc.SPVC_BASETYPE_FP32, 1)));
        ShaderManager.applyUboMembers(arrayProgram, reflection, true);
        arrayProgram.buildUniformSlotArrays();
        arrayProgram.linked = true;

        final int base = manager.getUniformLocation(program, "weights");
        final int element = manager.getUniformLocation(program, "weights[0]");
        final int exposure = manager.getUniformLocation(program, "exposure");
        assertTrue(base >= 0);
        assertTrue(exposure >= 0);
        assertEquals(base, element);
        for (String name : List.of("weights[]", "weights[-1]", "weights[+0]", "weights[ 0]", "weights[00]",
            "weights[0][0]", "weights[1]", "weights[2147483648]", "[0]", "exposure[0]")) {
            assertEquals(-1, manager.getUniformLocation(program, name), name);
        }

        try {
            st.boundProgramObj = arrayProgram;
            final FloatBuffer stage = st.uniformStaging(arrayProgram).vsUniformFb;
            applier.putUniform(st, exposure, new float[]{3});
            applier.putUniform(st, element, new float[]{7, 9});
            assertEquals(7f, stage.get(0));
            assertEquals(3f, stage.get(4));
            applier.putUniform(st, base, new float[]{11});
            assertEquals(11f, stage.get(0));
        } finally {
            st.releaseUniformStaging(arrayProgram);
        }
    }

    @Test
    void indexedAndBulkWritesUseTheArrayStrideInBothStages() {
        final ShaderManager manager = new ShaderManager(null);
        final int program = manager.createProgram();
        final ShaderManager.ProgramObject arrayProgram = manager.getProgram(program);
        final ShaderManager.StageReflection reflection = reflection(64, List.of(
            new ShaderManager.UboMember("weights", 0, 48, 16, 1, 1, Spvc.SPVC_BASETYPE_FP32, 3),
            new ShaderManager.UboMember("exposure", 48, 4, 0, 1, 1, Spvc.SPVC_BASETYPE_FP32, 1)));
        ShaderManager.applyUboMembers(arrayProgram, reflection, true);
        ShaderManager.applyUboMembers(arrayProgram, reflection, false);
        arrayProgram.buildUniformSlotArrays();
        arrayProgram.linked = true;
        final int base = manager.getUniformLocation(program, "weights");
        final int second = manager.getUniformLocation(program, "weights[1]");
        final int exposure = manager.getUniformLocation(program, "exposure");
        assertTrue(base >= 0);
        assertEquals(base, manager.getUniformLocation(program, "weights[0]"));
        assertEquals(base + 1, second);
        assertEquals(base + 2, manager.getUniformLocation(program, "weights[2]"));
        assertEquals(-1, manager.getUniformLocation(program, "weights[3]"));

        try {
            st.boundProgramObj = arrayProgram;
            final UniformStaging staging = st.uniformStaging(arrayProgram);
            applier.putUniform(st, exposure, new float[]{13});
            final FloatBuffer initial = FloatBuffer.wrap(new float[]{99, 2, 3, 5}).position(1);
            applier.putUniformFv(st, base, initial);
            assertEquals(1, initial.position());
            final float[] element = applier.reuseOrAlloc(st, second, 1);
            element[0] = 7;
            applier.putUniform(st, second, element);
            assertWeights(staging, 2, 7, 5, 13);

            staging.vsUniformDirty = false;
            staging.fsUniformDirty = false;
            applier.putUniform(st, second, element);
            assertFalse(staging.vsUniformDirty);
            assertFalse(staging.fsUniformDirty);

            applier.putUniformFv(st, second, FloatBuffer.wrap(new float[]{11, 17, 19}));
            assertWeights(staging, 2, 11, 17, 13);
            applier.putUniformFv(st, base, initial);
            assertWeights(staging, 2, 3, 5, 13);
        } finally {
            st.releaseUniformStaging(arrayProgram);
        }
    }

    @Test
    void elementWriteRestoresOtherElementsAfterAnotherProgramUpdatedTheSharedBlock() {
        final ShaderManager.ProgramObject first = programWithSharedArray();
        final ShaderManager.ProgramObject second = programWithSharedArray();
        final ContextState.UniformBlockState block = st.uniformBlocks[ShaderManager.BLOCK_PER_PASS];
        try {
            final float[] initial = {1, 2, 3, 4, 5, 6, 7, 8};
            st.boundProgramObj = first;
            applier.putUniform(st, 0, initial);
            st.boundProgramObj = second;
            applier.putUniform(st, 0, new float[]{9, 10, 11, 12, 13, 14, 15, 16});
            st.boundProgramObj = first;
            applier.putUniformFv(st, 1, FloatBuffer.wrap(new float[]{5, 6, 7, 8}));
            assertArrayEquals(initial, readStage(block.staging(32)));

            block.dirty = false;
            applier.putUniformFv(st, 1, FloatBuffer.wrap(new float[]{5, 6, 7, 8}));
            assertFalse(block.dirty);
        } finally {
            st.releaseUniformStaging(first);
            st.releaseUniformStaging(second);
            block.free();
        }
    }

    private static ShaderManager.ProgramObject programWithSharedArray() {
        final ShaderManager.ProgramObject program = new ShaderManager.ProgramObject();
        final ShaderManager.BlockReflection[] blocks = ShaderManager.BlockReflection.emptyBlocks();
        final List<ShaderManager.UboMember> members = List.of(new ShaderManager.UboMember(
            "colors", 0, 32, 16, 4, 1, Spvc.SPVC_BASETYPE_FP32, 2));
        blocks[ShaderManager.BLOCK_PER_PASS] = new ShaderManager.BlockReflection(32,
            ShaderManager.BLOCK_BINDINGS[ShaderManager.BLOCK_PER_PASS], members, true);
        ShaderManager.applyBlockMembers(program, reflection(0, List.of(), blocks));
        program.buildUniformSlotArrays();
        return program;
    }

    private static ShaderManager.StageReflection reflection(int size, List<ShaderManager.UboMember> members) {
        return reflection(size, members, ShaderManager.BlockReflection.emptyBlocks());
    }

    private static ShaderManager.StageReflection reflection(int size, List<ShaderManager.UboMember> members,
        ShaderManager.BlockReflection[] blocks) {
        return new ShaderManager.StageReflection(ShaderManager.ResourceCounts.EMPTY, List.of(), List.of(),
            List.of(), List.of(), size, members, List.of(), List.of(), List.of(), -1, 0, 0, 0, 0, blocks);
    }

    private static void assertWeights(UniformStaging staging, float first, float second, float third, float exposure) {
        for (FloatBuffer stage : List.of(staging.vsUniformFb, staging.fsUniformFb)) {
            assertEquals(first, stage.get(0));
            assertEquals(second, stage.get(4));
            assertEquals(third, stage.get(8));
            assertEquals(exposure, stage.get(12));
        }
    }

    private static float[] readStage(FloatBuffer buffer) {
        final float[] values = new float[buffer.capacity()];
        buffer.get(0, values);
        return values;
    }

    @Test
    void firstSet_dirtiesAndWritesUbo() {
        final float[] a = applier.reuseOrAlloc(st, 0, 4);
        a[0] = 1f; a[1] = 2f; a[2] = 3f; a[3] = 4f;
        applier.putUniform(st, 0, a);
        assertTrue(us.vsUniformDirty);
        assertEquals(1f, us.vsUniformFb.get(0));
        assertEquals(4f, us.vsUniformFb.get(3));
    }

    @Test
    void setSameValueTwice_secondCallDoesNotDirty() {
        final float[] a = applier.reuseOrAlloc(st, 0, 4);
        a[0] = 1f; a[1] = 2f; a[2] = 3f; a[3] = 4f;
        applier.putUniform(st, 0, a);
        us.vsUniformDirty = false;

        final float[] b = applier.reuseOrAlloc(st, 0, 4);
        assertSame(a, b, "reuseOrAlloc should return the cached array");
        b[0] = 1f; b[1] = 2f; b[2] = 3f; b[3] = 4f;
        applier.putUniform(st, 0, b);
        assertFalse(us.vsUniformDirty, "same-value reset must not re-dirty");
    }

    @Test
    void setDifferentValue_dirties() {
        final float[] a = applier.reuseOrAlloc(st, 0, 4);
        a[0] = 1f; a[1] = 2f; a[2] = 3f; a[3] = 4f;
        applier.putUniform(st, 0, a);
        us.vsUniformDirty = false;

        final float[] b = applier.reuseOrAlloc(st, 0, 4);
        b[0] = 1f; b[1] = 2f; b[2] = 3f; b[3] = 99f;
        applier.putUniform(st, 0, b);
        assertTrue(us.vsUniformDirty);
        assertEquals(99f, us.vsUniformFb.get(3));
    }

    @Test
    void differentSlots_independent() {
        final float[] a = applier.reuseOrAlloc(st, 0, 4);
        a[0] = 1f; a[1] = 2f; a[2] = 3f; a[3] = 4f;
        applier.putUniform(st, 0, a);
        final float[] b = applier.reuseOrAlloc(st, 1, 4);
        b[0] = 5f; b[1] = 6f; b[2] = 7f; b[3] = 8f;
        applier.putUniform(st, 1, b);
        assertEquals(1f, us.vsUniformFb.get(0));
        assertEquals(5f, us.vsUniformFb.get(4));
        assertEquals(8f, us.vsUniformFb.get(7));
    }

    @Test
    void fragmentSlot_independentDirtyFlag() {
        final float[] a = applier.reuseOrAlloc(st, 2, 4);
        a[0] = 9f; a[1] = 10f; a[2] = 11f; a[3] = 12f;
        applier.putUniform(st, 2, a);
        assertTrue(us.vsUniformDirty, "slot 2 has VS member at offset 32");
        assertTrue(us.fsUniformDirty, "slot 2 also has FS member at offset 0");
        assertEquals(9f, us.vsUniformFb.get(8));
        assertEquals(9f, us.fsUniformFb.get(0));
    }

    @Test
    void outOfRangeLocation_noCrash_noDirty() {
        applier.putUniform(st, 999, new float[]{1f, 2f, 3f, 4f});
        applier.putUniform(st, -1, new float[]{1f, 2f, 3f, 4f});
        assertFalse(us.vsUniformDirty);
        assertFalse(us.fsUniformDirty);
    }

    @Test
    void noProgramBound_reuseOrAllocReturnsNull_putIsNoOp() {
        st.boundProgramObj = null;
        assertNull(applier.reuseOrAlloc(st, 0, 4), "no program means nowhere to store");
        applier.putUniform(st, 0, new float[4]);
    }

    @Test
    void lengthChange_writesThrough() {
        final float[] a = applier.reuseOrAlloc(st, 0, 4);
        a[0] = 1f; a[1] = 2f; a[2] = 3f; a[3] = 4f;
        applier.putUniform(st, 0, a);
        us.vsUniformDirty = false;
        final float[] c = applier.reuseOrAlloc(st, 0, 2);
        assertNotSame(a, c);
        c[0] = 1f; c[1] = 2f;
        applier.putUniform(st, 0, c);
        assertTrue(us.vsUniformDirty);
    }
}
