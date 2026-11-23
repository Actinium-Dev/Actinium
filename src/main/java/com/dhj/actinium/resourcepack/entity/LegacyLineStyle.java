package com.dhj.actinium.resourcepack.entity;

import java.util.Properties;
import java.util.function.Consumer;

/** Describes the two textured ribbons used by a legacy MCPatcher line. */
record LegacyLineStyle(double width, double a, double b, double sx, double sy, double sz, int segments, double tileFactor) {
    private static final int MAX_SEGMENTS = 8192;

    static LegacyLineStyle read(Properties properties, double width, double a, double b, int segments,
            Consumer<String> invalid) {
        return new LegacyLineStyle(number(properties, "width", width, invalid), number(properties, "a", a, invalid),
            number(properties, "b", b, invalid), number(properties, "sx", 0, invalid),
            number(properties, "sy", 0, invalid), number(properties, "sz", 0, invalid),
            segments(properties, segments, invalid), number(properties, "tileFactor", 24, invalid));
    }

    private static double number(Properties properties, String key, double defaults, Consumer<String> invalid) {
        String value = properties.getProperty(key, "").trim();
        if (value.isEmpty()) return defaults;
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed)) throw new NumberFormatException("Expected a finite number");
            return parsed;
        } catch (NumberFormatException e) {
            invalid.accept(key + "=" + value);
            return defaults;
        }
    }

    private static int segments(Properties properties, int defaults, Consumer<String> invalid) {
        String value = properties.getProperty("segments", "").trim();
        if (value.isEmpty()) return defaults;
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1 || parsed > MAX_SEGMENTS) throw new NumberFormatException("Segment count outside supported range");
            return parsed;
        } catch (NumberFormatException e) {
            invalid.accept("segments=" + value);
            return defaults;
        }
    }

    /** Emits consecutive quads in the original MCPatcher vertex and UV order. */
    void emit(double x, double y, double z, double dx, double dy, double dz, Vertex vertex) {
        dx += sx;
        dy += sy;
        dz += sz;
        double tileStep = Math.sqrt(dx * dx + dy * dy + dz * dz) * (tileFactor / segments);
        double x0 = x;
        double y0 = y + a + b;
        double z0 = z;
        double u0 = 0;
        for (int i = 1; i <= segments; i++) {
            double s = i / (double) segments;
            double x1 = x + s * dx;
            double y1 = y + (s * s + s) * 0.5 * dy + a * (1 - s) + b;
            double z1 = z + s * dz;
            double u1 = (segments - i) * tileStep;

            vertex.put(x0, y0, z0, u0, 1);
            vertex.put(x1, y1, z1, u1, 1);
            vertex.put(x1 + width, y1 + width, z1, u1, 0);
            vertex.put(x0 + width, y0 + width, z0, u0, 0);
            vertex.put(x0, y0 + width, z0, u0, 1);
            vertex.put(x1, y1 + width, z1, u1, 1);
            vertex.put(x1 + width, y1, z1 + width, u1, 0);
            vertex.put(x0 + width, y0, z0 + width, u0, 0);

            x0 = x1;
            y0 = y1;
            z0 = z1;
            u0 = u1;
        }
    }

    /** Receives one vertex of a textured ribbon. */
    @FunctionalInterface
    interface Vertex {
        void put(double x, double y, double z, double u, double v);
    }
}
