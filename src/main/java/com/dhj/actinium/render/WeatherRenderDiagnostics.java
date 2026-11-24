package com.dhj.actinium.render;

import com.dhj.actinium.runtime.ActiniumRuntime;
import com.gtnewhorizons.angelica.glsm.backend.BackendManager;
import com.gtnewhorizons.angelica.glsm.debug.GLSMPerfDebug;

import java.util.Arrays;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

/** Reports weather path selections and submitted columns on the render thread. */
final class WeatherRenderDiagnostics {
    private static final long REPORT_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(5);
    private static final Path[] PATHS = Path.values();
    private static final int[] passes = new int[PATHS.length];

    private static long windowStart;
    private static int vanillaDraws;
    private static int providerDraws;
    private static long vanillaColumns;
    private static long providerColumns;

    private WeatherRenderDiagnostics() {}

    enum Path {
        FFP,
        IRIS,
        DRY,
        OPTION_DISABLED,
        NESTED_WORLD,
        DYNAMIC_SURROUNDINGS,
        UNSUPPORTED_BACKEND,
        FORGE_HANDLER,
        IRIS_VARIANT_UNAVAILABLE,
        FFP_UNAVAILABLE
    }

    static void recordPass(Path path) {
        if (!GLSMPerfDebug.isEnabled()) {
            if (windowStart != 0L) {
                reset();
            }
            return;
        }

        passes[path.ordinal()]++;
        long now = System.nanoTime();
        if (windowStart == 0L) {
            windowStart = now;
        } else if (now - windowStart >= REPORT_INTERVAL_NANOS) {
            report(now);
        }
    }

    static void recordDraw(boolean provider, int columns) {
        if (!GLSMPerfDebug.isEnabled()) {
            return;
        }
        if (provider) {
            providerDraws++;
            providerColumns += columns;
        } else {
            vanillaDraws++;
            vanillaColumns += columns;
        }
    }

    static void endWorld() {
        if (windowStart != 0L && GLSMPerfDebug.isEnabled()) {
            report(System.nanoTime());
        }
        reset();
    }

    private static void report(long now) {
        StringJoiner paths = new StringJoiner(",");
        for (Path path : PATHS) {
            int count = passes[path.ordinal()];
            if (count != 0) {
                paths.add(path.name() + "=" + count);
            }
        }
        if (paths.length() != 0) {
            ActiniumRuntime.logger().info("[Weather] backend={} windowMs={} passes=[{}] vanillaDraws={} vanillaColumns={} providerDraws={} providerColumns={}",
                BackendManager.RENDER_BACKEND.getName(), TimeUnit.NANOSECONDS.toMillis(now - windowStart), paths,
                vanillaDraws, vanillaColumns, providerDraws, providerColumns);
        }
        reset();
    }

    private static void reset() {
        windowStart = 0L;
        Arrays.fill(passes, 0);
        vanillaDraws = 0;
        providerDraws = 0;
        vanillaColumns = 0L;
        providerColumns = 0L;
    }
}
