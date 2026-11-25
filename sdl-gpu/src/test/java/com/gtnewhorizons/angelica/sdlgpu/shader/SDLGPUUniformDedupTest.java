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
    void indexedLookupRequiresAnArrayAndANonEmptyIndex() {
        final ShaderManager manager = new ShaderManager(null);
        final int program = manager.createProgram();
        final ShaderManager.ProgramObject lookup = manager.getProgram(program);
        final List<ShaderManager.UboMember> members = List.of(
            new ShaderManager.UboMember("exposure", 0, 4, 0, 1, 1, Spvc.SPVC_BASETYPE_FP32, 1),
            new ShaderManager.UboMember("planes", 16, 16, 16, 4, 1, Spvc.SPVC_BASETYPE_FP32, 1));
        final ShaderManager.StageReflection reflection = new ShaderManager.StageReflection(
            ShaderManager.ResourceCounts.EMPTY, List.of(), List.of(), List.of(), List.of(), 32,
            members, List.of(), List.of(), List.of(), -1, 0, 0, 0, 0, ShaderManager.BlockReflection.emptyBlocks());
        ShaderManager.applyUboMembers(lookup, reflection, true);
        lookup.buildUniformSlotArrays();
        lookup.linked = true;

        assertTrue(manager.getUniformLocation(program, "exposure") >= 0);
        assertEquals(-1, manager.getUniformLocation(program, "exposure[0]"));
        final int plane = manager.getUniformLocation(program, "planes");
        assertTrue(plane >= 0);
        assertEquals(plane, manager.getUniformLocation(program, "planes[0]"));
        assertEquals(-1, manager.getUniformLocation(program, "planes[]"));
    }

    @Test
    void partialAndBulkClipPlaneWritesReachBothUniformBlocks() {
        st.releaseUniformStaging(prog);
        final ShaderManager manager = new ShaderManager(null);
        final int program = manager.createProgram();
        prog = manager.getProgram(program);
        final ShaderManager.UboMember planes = new ShaderManager.UboMember(
            "planes", 0, 32, 16, 4, 1, Spvc.SPVC_BASETYPE_FP32, 2);
        final ShaderManager.StageReflection reflection = new ShaderManager.StageReflection(
            ShaderManager.ResourceCounts.EMPTY, List.of(), List.of(), List.of(), List.of(), 32,
            List.of(planes), List.of(), List.of(), List.of(), -1, 0, 0, 0, 0, ShaderManager.BlockReflection.emptyBlocks());
        ShaderManager.applyUboMembers(prog, reflection, true);
        ShaderManager.applyUboMembers(prog, reflection, false);
        prog.buildUniformSlotArrays();
        prog.linked = true;
        st.boundProgramObj = prog;
        us = st.uniformStaging(prog);
        final int base = manager.getUniformLocation(program, "planes");
        final int second = manager.getUniformLocation(program, "planes[1]");
        assertTrue(base >= 0);
        assertEquals(base, manager.getUniformLocation(program, "planes[0]"));
        assertEquals(base + 1, second);
        assertEquals(-1, manager.getUniformLocation(program, "planes[2]"));

        final float[] initial = {1, 0, 0, -5, 0, 1, 0, -8};
        applier.putUniformFv(st, base, FloatBuffer.wrap(initial));
        applier.putUniformFv(st, second, FloatBuffer.wrap(new float[]{0, 0, 1, 3, 9, 9, 9, 9}));
        final float[] changed = {1, 0, 0, -5, 0, 0, 1, 3};
        assertArrayEquals(changed, readStage(us.vsUniformFb));
        assertArrayEquals(changed, readStage(us.fsUniformFb));
        applier.putUniformFv(st, base, FloatBuffer.wrap(initial));
        assertArrayEquals(initial, readStage(us.vsUniformFb));
        assertArrayEquals(initial, readStage(us.fsUniformFb));
    }

    @Test
    void elementWriteRestoresOtherElementsAfterAnotherProgramUpdatedTheSharedBlock() {
        final ShaderManager.ProgramObject first = programWithSharedPlanes();
        final ShaderManager.ProgramObject second = programWithSharedPlanes();
        final ContextState.UniformBlockState block = st.uniformBlocks[ShaderManager.BLOCK_PER_PASS];
        try {
            final float[] firstPlanes = {1, 0, 0, 2, 0, 1, 0, 4};
            st.boundProgramObj = first;
            applier.putUniform(st, 0, firstPlanes);
            st.boundProgramObj = second;
            applier.putUniform(st, 0, new float[]{0, 0, 1, 3, 1, 0, 0, 5});
            st.boundProgramObj = first;
            applier.putUniform(st, 1, new float[]{0, 1, 0, 4});
            assertArrayEquals(firstPlanes, readStage(block.staging(32)));
        } finally {
            st.releaseUniformStaging(first);
            st.releaseUniformStaging(second);
            block.free();
        }
    }

    private static ShaderManager.ProgramObject programWithSharedPlanes() {
        final ShaderManager.ProgramObject program = new ShaderManager.ProgramObject();
        program.nextUniformLocation = 2;
        program.arrayLenByLocation.put(0, 2);
        program.blockSize[ShaderManager.BLOCK_PER_PASS] = 32;
        program.blockMemberInfo[ShaderManager.BLOCK_PER_PASS].put(0,
            new ShaderManager.UniformMemberInfo(0, 32, 16, false, 4, 1, Spvc.SPVC_BASETYPE_FP32, 2));
        program.buildUniformSlotArrays();
        return program;
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
