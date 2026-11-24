package com.dhj.actinium.render;

import com.gtnewhorizons.angelica.api.weather.WeatherColumnProvider;
import com.gtnewhorizons.angelica.api.weather.WeatherColumnSink;
import com.gtnewhorizons.angelica.glsm.ffp.WeatherInstancedAttribs;
import com.gtnewhorizons.angelica.glsm.ffp.WeatherParams;
import net.minecraft.util.ResourceLocation;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class WeatherColumnBatch implements WeatherColumnSink {
    private final List<Layer> layers = new ArrayList<>();
    private ByteBuffer data = ByteBuffer.allocateDirect(64 * WeatherInstancedAttribs.STRIDE).order(ByteOrder.nativeOrder());
    private int count;
    private int originX;
    private int originZ;
    private boolean collecting;
    private Key key;

    /** Collects new GPU instance data when the provider, its revision or the column origin changes. */
    boolean update(WeatherColumnProvider provider, int revision, int dimension, int x, int z) {
        if (key != null && key.provider() == provider && key.revision() == revision
                && key.dimension() == dimension && key.x() == x && key.z() == z) {
            return false;
        }

        clear();
        originX = x;
        originZ = z;
        collecting = true;
        try {
            provider.collectColumns(this);
        } finally {
            collecting = false;
        }
        key = new Key(provider, revision, dimension, x, z);
        return true;
    }

    void clear() {
        key = null;
        count = 0;
        layers.clear();
    }

    ByteBuffer bytes() {
        return data.duplicate().order(data.order()).limit(count * WeatherInstancedAttribs.STRIDE);
    }

    List<Layer> layers() {
        return layers;
    }

    int endOfLayer(int layerIndex) {
        return layerIndex + 1 == layers.size() ? count : layers.get(layerIndex + 1).first();
    }

    @Override
    public void beginLayer(ResourceLocation texture, int scrollPeriod, float uDriftScale, float vDriftScale, float fadeScale) {
        requireCollecting();
        Objects.requireNonNull(texture, "Weather layer texture");
        if (scrollPeriod < 1 || scrollPeriod > WeatherParams.TICK_WRAP || Integer.bitCount(scrollPeriod) != 1) {
            throw new IllegalArgumentException("Weather layer scroll period must be a power of two between 1 and " + WeatherParams.TICK_WRAP);
        }
        if (!Float.isFinite(uDriftScale) || !Float.isFinite(vDriftScale) || !Float.isFinite(fadeScale)) {
            throw new IllegalArgumentException("Weather layer scales must be finite");
        }
        layers.add(new Layer(texture, count, scrollPeriod, uDriftScale, vDriftScale, fadeScale));
    }

    @Override
    public void column(int x, int z, float bottom, float top, float uBase, float uDrift, float vBase, float vDrift,
                       int brightness, int scrollOffset, float scrollScale, float r, float g, float b, float a) {
        requireCollecting();
        if (layers.isEmpty()) {
            throw new IllegalStateException("Begin a weather layer before emitting columns");
        }
        if (brightness < 0 || brightness >= (1 << 24)) {
            throw new IllegalArgumentException("Weather column brightness must fit in 24 bits");
        }
        if ((long) (count + 1) * WeatherInstancedAttribs.STRIDE > data.capacity()) {
            ByteBuffer grown = ByteBuffer.allocateDirect(Math.multiplyExact(data.capacity(), 2)).order(data.order());
            grown.put(bytes()).clear();
            data = grown;
        }
        Layer layer = layers.getLast();
        WeatherInstancedAttribs.putColumn(data, count++, (float) ((long) x - originX), (float) ((long) z - originZ),
            bottom, top, uBase, uDrift, vBase, vDrift, brightness, scrollOffset, scrollScale,
            WeatherInstancedAttribs.SOURCE_PROVIDER, r, g, b, a,
            layer.period(), layer.uDriftScale(), layer.vDriftScale(), layer.fadeScale());
    }

    private void requireCollecting() {
        if (!collecting) {
            throw new IllegalStateException("The weather sink is only valid during collectColumns");
        }
    }

    record Layer(ResourceLocation texture, int first, int period, float uDriftScale, float vDriftScale, float fadeScale) {}

    private record Key(WeatherColumnProvider provider, int revision, int dimension, int x, int z) {}
}
