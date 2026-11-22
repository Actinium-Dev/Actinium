package com.dhj.actinium.compat.librarianlib;

import com.dhj.actinium.render.terrain.compile.light.BlockQuadLightOverride;
import com.gtnewhorizon.gtnhlib.compat.Mods;

public final class LibrarianLibGlowCompat {
    public static final boolean IS_LOADED = Mods.isModPresent("librarianlib")
            && LibrarianLibGlowCompat.class.getClassLoader().getResource(
                    "com/teamwizardry/librarianlib/core/client/RenderHookHandler$blockHooks$1.class") != null;

    private LibrarianLibGlowCompat() {
    }

    public static BlockQuadLightOverride create() {
        return IS_LOADED ? LibrarianLibBlockLightOverride.create() : null;
    }
}
