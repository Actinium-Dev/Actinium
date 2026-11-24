package com.dhj.actinium.resourcepack.entity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class LegacyLineStyleTest {
    @Test
    void configuredOffsetsAndSagShapeBothRibbons() throws IOException {
        var style = LegacyLineStyle.read(properties("""
            width=0.25
            a=2
            b=0.5
            sx=1
            sy=-2
            sz=2
            segments=2
            tileFactor=2
            """), 1, 1, 1, 1, message -> fail(message));
        var vertices = new ArrayList<Vertex>();
        style.emit(2, 3, 4, 2, 6, -2, (x, y, z, u, v) -> vertices.add(new Vertex(x, y, z, u, v)));

        assertEquals(16, vertices.size());
        assertEquals(new Vertex(2, 5.5, 4, 0, 1), vertices.get(0));
        assertEquals(new Vertex(3.5, 6, 4, 5, 1), vertices.get(1));
        assertEquals(new Vertex(3.75, 6.25, 4, 5, 0), vertices.get(2));
        assertEquals(new Vertex(3.75, 6, 4.25, 5, 0), vertices.get(6));
        assertEquals(vertices.get(1), vertices.get(8));
        assertEquals(new Vertex(5, 7.5, 4, 0, 1), vertices.get(9));
    }

    @Test
    void invalidPropertiesKeepTheirDefaultsWithoutDiscardingValidProperties() throws IOException {
        var warnings = new ArrayList<String>();
        var style = LegacyLineStyle.read(properties("""
            width=NaN
            segments=-1
            sx=1.5
            """), 0.125, 2, 1, 6, warnings::add);

        assertEquals(List.of("width=NaN", "segments=-1"), warnings);
        var vertices = new ArrayList<Vertex>();
        style.emit(0, 0, 0, 0, 0, 0, (x, y, z, u, v) -> vertices.add(new Vertex(x, y, z, u, v)));
        assertEquals(48, vertices.size());
        assertEquals(new Vertex(0.125, 3.125, 0, 0, 0), vertices.get(3));
        assertEquals(new Vertex(1.5, 1, 0, 0, 1), vertices.get(41));
    }

    private static Properties properties(String text) throws IOException {
        var properties = new Properties();
        properties.load(new StringReader(text));
        return properties;
    }

    private record Vertex(double x, double y, double z, double u, double v) {}
}
