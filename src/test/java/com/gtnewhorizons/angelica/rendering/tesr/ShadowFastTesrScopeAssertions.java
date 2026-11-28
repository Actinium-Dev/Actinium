package com.gtnewhorizons.angelica.rendering.tesr;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Gives the transformed dispatcher probe package-local access to the real batch scope. */
public final class ShadowFastTesrScopeAssertions {
    private ShadowFastTesrScopeAssertions() {}

    public static void assertBalanced() {
        assertEquals(0L, TesrBatchRenderer.INSTANCE.currentScope());
    }
}
