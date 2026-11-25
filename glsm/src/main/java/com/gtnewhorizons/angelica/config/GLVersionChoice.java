package com.gtnewhorizons.angelica.config;

/**
 * Context profiles the client can request. Desktop requests are capped at the platform maximum.
 */
public enum GLVersionChoice {
    AUTO(0),
    GL33(33),
    GL40(40),
    GL41(41),
    GL42(42),
    GL43(43),
    GL44(44),
    GL45(45),
    GL46(46),
    GLES32(-2);

    private final int version;

    GLVersionChoice(int version) {
        this.version = version;
    }

    public int version() { return version; }

    public String label() {
        if (this == GLES32) return "OpenGL ES 3.2";
        return version / 10 + "." + version % 10;
    }

    public static GLVersionChoice of(int version, int ceiling) {
        if (version == GLES32.version) return GLES32;
        GLVersionChoice best = AUTO;
        for (final GLVersionChoice choice : values()) {
            if (choice.version > 0 && choice.version <= version && choice.version <= ceiling) best = choice;
        }
        return best;
    }
}
