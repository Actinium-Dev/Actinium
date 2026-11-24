package com.dhj.actinium.render;

import com.gtnewhorizons.angelica.api.weather.WeatherColumnProvider;
import com.gtnewhorizons.angelica.api.weather.WeatherColumnSink;
import com.gtnewhorizons.angelica.glsm.ffp.WeatherInstancedAttribs;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherColumnBatchTest {
    @Test
    void reusesColumnsUntilRevisionOriginDimensionOrProviderChanges() {
        WeatherColumnBatch batch = new WeatherColumnBatch();
        Provider first = new Provider(sink -> {});
        assertTrue(batch.update(first, 0, 0, -20, 10));
        assertFalse(batch.update(first, 0, 0, -20, 10));
        assertTrue(batch.update(first, 1, 0, -20, 10));
        assertTrue(batch.update(first, 1, 0, -19, 10));
        assertTrue(batch.update(first, 1, 1, -19, 10));
        assertEquals(4, first.collections);

        Provider second = new Provider(sink -> {});
        assertTrue(batch.update(second, 1, 1, -19, 10));
        batch.clear();
        assertTrue(batch.update(second, 1, 1, -19, 10));
        assertEquals(2, second.collections);
    }

    @Test
    void preservesLayerRangesAndCameraRelativeColumnsWhenTheBufferGrows() {
        WeatherColumnBatch batch = new WeatherColumnBatch();
        ResourceLocation rain = new ResourceLocation("test", "rain");
        ResourceLocation empty = new ResourceLocation("test", "empty");
        ResourceLocation snow = new ResourceLocation("test", "snow");
        int originX = 29_000_002;
        int originZ = -29_000_002;
        int rainColumns = 100;
        int period = 16;
        Provider provider = new Provider(sink -> {
            sink.beginLayer(rain, 8, 0, 0, 1);
            for (int i = 0; i < rainColumns; i++) {
                emit(sink, originX + i, originZ);
            }
            sink.beginLayer(empty, 1, 0, 0, 1);
            sink.beginLayer(snow, period, 3, 4, 1);
            emit(sink, originX - 1, originZ + 1);
        });

        batch.update(provider, 0, 0, originX, originZ);
        var layers = batch.layers();
        assertEquals(rain, layers.get(0).texture());
        assertEquals(rainColumns, batch.endOfLayer(0) - layers.get(0).first());
        assertEquals(0, batch.endOfLayer(1) - layers.get(1).first());
        assertEquals(snow, layers.get(2).texture());
        assertEquals(1, batch.endOfLayer(2) - layers.get(2).first());

        var bytes = batch.bytes();
        int snowOffset = layers.getLast().first() * WeatherInstancedAttribs.STRIDE;
        assertEquals(-1.0F, bytes.getFloat(snowOffset + WeatherInstancedAttribs.OFFSET_COLUMN_SPAN));
        assertEquals(1.0F, bytes.getFloat(snowOffset + WeatherInstancedAttribs.OFFSET_COLUMN_SPAN + Float.BYTES));
        assertEquals(period, bytes.getFloat(snowOffset + WeatherInstancedAttribs.OFFSET_LAYER));
        assertEquals(rainColumns + 1, bytes.remaining() / WeatherInstancedAttribs.STRIDE);
    }

    @Test
    void rejectsColumnsBeforeTheFirstLayerAndDoesNotCacheAFailedCollection() {
        WeatherColumnBatch batch = new WeatherColumnBatch();
        Provider provider = new Provider(sink -> emit(sink, 0, 0));
        assertThrows(IllegalStateException.class, () -> batch.update(provider, 0, 0, 0, 0));
        assertThrows(IllegalStateException.class, () -> batch.update(provider, 0, 0, 0, 0));
        assertEquals(2, provider.collections);
    }

    private static void emit(WeatherColumnSink sink, int x, int z) {
        sink.column(x, z, 40, 60, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1);
    }

    private static final class Provider implements WeatherColumnProvider {
        private final Consumer<WeatherColumnSink> collect;
        private int collections;

        Provider(Consumer<WeatherColumnSink> collect) {
            this.collect = collect;
        }

        @Override
        public boolean rendersVanillaWeather() {
            return false;
        }

        @Override
        public int beginFrame(WorldClient world, EntityLivingBase view, float partialTicks) {
            return 0;
        }

        @Override
        public int columnRevision() {
            return 0;
        }

        @Override
        public float fadeRadius() {
            return 20;
        }

        @Override
        public void collectColumns(WeatherColumnSink sink) {
            collections++;
            collect.accept(sink);
        }
    }
}
