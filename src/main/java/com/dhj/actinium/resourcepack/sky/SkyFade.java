package com.dhj.actinium.resourcepack.sky;

import java.util.Properties;

record SkyFade(int startIn, int in, int full, int out) {
    static final int DAY = 24000;

    static SkyFade parse(Properties properties) {
        int start = time(properties.getProperty("startFadeIn"));
        int endIn = time(properties.getProperty("endFadeIn"));
        int end = time(properties.getProperty("endFadeOut"));
        int in = Math.floorMod(endIn - start, DAY);
        String value = properties.getProperty("startFadeOut");
        int startOut = value == null ? Math.floorMod(end - in, DAY) : time(value);
        int full = Math.floorMod(startOut - endIn, DAY);
        int out = Math.floorMod(end - startOut, DAY);
        if (in == 0 || out == 0 || in + full + out > DAY) {
            throw new IllegalArgumentException("Invalid sky fade intervals");
        }
        return new SkyFade(start, in, full, out);
    }

    float brightness(long time, float partialTicks) {
        float elapsed = (Math.floorMod(time - startIn, DAY) + partialTicks) % DAY;
        if (elapsed < in) return elapsed / in;
        elapsed -= in;
        if (elapsed < full) return 1;
        return Math.max(0, 1 - (elapsed - full) / out);
    }

    private static int time(String value) {
        if (value == null || !value.matches("\\d{1,2}:\\d{2}(:\\d{2})?")) {
            throw new IllegalArgumentException("Expected sky time hh:mm[:ss], got " + value);
        }
        String[] parts = value.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        int second = parts.length == 3 ? Integer.parseInt(parts[2]) : 0;
        if (hour > 23 || minute > 59 || second > 59) throw new IllegalArgumentException("Invalid sky time " + value);
        return Math.floorMod((hour * 3600 + minute * 60 + second) * 5 / 18 - 6000, DAY);
    }
}
