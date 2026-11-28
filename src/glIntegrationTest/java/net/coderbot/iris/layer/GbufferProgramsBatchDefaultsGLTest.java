package net.coderbot.iris.layer;

import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.GLStateManagerIntegrationFixture;
import com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard;
import net.coderbot.iris.gl.shader.ProgramCreator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.opengl.GL20;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GbufferProgramsBatchDefaultsGLTest extends GLStateManagerIntegrationFixture {
    private Runnable previousFlush;
    private int flushes;

    @BeforeAll
    static void initializeGlsm() {
        installDefaultGlsmConfig();
        GLStateManager.bindDisplayContext(new Object(), context);
    }

    @BeforeEach
    void observeBatchFlushes() {
        previousFlush = BatchStateGuard.flush;
        BatchStateGuard.flush = () -> flushes++;
    }

    @AfterEach
    void restoreBatchFlush() {
        BatchStateGuard.flush = previousFlush;
        assertFalse(BatchStateGuard.isSuspended());
    }

    @Test
    void consecutiveEntityPhasesKeepTheBatchQueued() {
        for (int entity = 0; entity < 2; entity++) {
            try (GbufferPrograms.EntityPhase phase = GbufferPrograms.enterEntityPhase()) {
                assertTrue(phase.changedPhase());
            }
        }

        assertEquals(0, flushes, "Entity defaults must not drain the preceding entity's batch");
    }

    @Test
    void entityNestedInsideBlockEntityKeepsTheBatchQueued() {
        GbufferPrograms.beginBlockEntities();
        try {
            try (GbufferPrograms.EntityPhase phase = GbufferPrograms.enterEntityPhase()) {
                assertTrue(phase.changedPhase());
            }
        } finally {
            GbufferPrograms.endBlockEntities();
        }

        assertEquals(0, flushes, "Entering and restoring owned phases must not drain queued geometry");
    }

    @Test
    void defaultsReachTheDriverWithoutChangingAnOuterSuspension() {
        GL20.glVertexAttrib4f(ProgramCreator.AT_TANGENT, 0.0f, 1.0f, 0.0f, -1.0f);
        GL20.glVertexAttrib4f(ProgramCreator.AT_MIDBLOCK, 1.0f, 2.0f, 3.0f, 1.0f);
        BatchStateGuard.suspend();
        try {
            GbufferPrograms.setBlockEntityDefaults();
            assertTrue(BatchStateGuard.isSuspended());
            assertAttribute(ProgramCreator.MC_ENTITY, -1.0f, -1.0f, 0.0f, 1.0f);
            assertAttribute(ProgramCreator.MC_MID_TEX_COORD, 0.5f, 0.5f, 0.0f, 1.0f);
            assertAttribute(ProgramCreator.AT_TANGENT, 1.0f, 0.0f, 0.0f, 1.0f);
            assertAttribute(ProgramCreator.AT_MIDBLOCK, 0.0f, 0.0f, 0.0f, 0.0f);
        } finally {
            BatchStateGuard.resume();
        }
        assertEquals(0, flushes);
    }

    @ParameterizedTest
    @ValueSource(ints = { ProgramCreator.AT_TANGENT, ProgramCreator.AT_MIDBLOCK })
    void rendererAttributeMutationStillFlushesBeforeChangingTheDriverState(int attribute) {
        GbufferPrograms.setBlockEntityDefaults();
        float[] original = attribute == ProgramCreator.AT_TANGENT
            ? new float[] { 1.0f, 0.0f, 0.0f, 1.0f }
            : new float[] { 0.0f, 0.0f, 0.0f, 0.0f };
        flushes = 0;
        BatchStateGuard.flush = () -> {
            assertAttribute(attribute, original);
            flushes++;
        };

        GLSMCurrentAttribs.glVertexAttrib4f(attribute, 0.0f, 1.0f, 0.0f, -1.0f);

        assertEquals(1, flushes);
        assertAttribute(attribute, 0.0f, 1.0f, 0.0f, -1.0f);
    }

    private static void assertAttribute(int index, float... expected) {
        float[] actual = new float[4];
        GL20.glGetVertexAttribfv(index, GL20.GL_CURRENT_VERTEX_ATTRIB, actual);
        assertArrayEquals(expected, actual);
    }
}
