package com.dhj.actinium.resourcepack.item;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.renderer.block.model.ModelBlock;
import net.minecraft.util.ResourceLocation;

/** Translates pack-relative CIT paths to real resources and Forge model identifiers. */
public final class CitPaths {
    private static final String MODEL_PREFIX = "models/actinium_cit/";

    private CitPaths() {}

    static ResourceLocation file(ResourceLocation rule, String value, String extension) {
        String text = value.trim();
        if (text.isEmpty()) throw new IllegalArgumentException("Empty CIT path");
        ResourceLocation path = new ResourceLocation(text);
        String namespace = text.contains(":") ? path.getNamespace() : rule.getNamespace();
        String name = path.getPath();
        if (name.startsWith("~/")) name = root(rule) + name.substring(2);
        else if (name.startsWith("/")) name = name.substring(1);
        else if (!absolute(name)) {
            if (text.contains(":")) name = (extension.equals(".json") ? "models/" : "textures/") + name;
            else name = rule.getPath().substring(0, rule.getPath().lastIndexOf('/') + 1) + name;
        }
        Deque<String> segments = new ArrayDeque<>();
        for (String segment : name.split("/")) {
            if (segment.equals("..")) {
                if (segments.isEmpty()) throw new IllegalArgumentException("CIT path escapes its namespace: " + value);
                segments.removeLast();
            } else if (!segment.equals(".") && !segment.isEmpty()) segments.addLast(segment);
        }
        name = String.join("/", segments);
        return new ResourceLocation(namespace, name.endsWith(extension) ? name : name + extension);
    }

    private static boolean absolute(String name) {
        return name.startsWith("optifine/") || name.startsWith("mcpatcher/") || name.startsWith("textures/") || name.startsWith("models/");
    }

    private static String root(ResourceLocation rule) {
        return rule.getPath().startsWith("mcpatcher/") ? "mcpatcher/" : "optifine/";
    }

    static ResourceLocation modelId(ResourceLocation file) {
        return new ResourceLocation(file.getNamespace(), "actinium_cit/" + file.getPath().substring(0, file.getPath().length() - 5));
    }

    /** Resolves the synthetic Forge model path to the original pack JSON. */
    public static ResourceLocation modelFile(ResourceLocation location) {
        String path = location.getPath();
        return path.startsWith(MODEL_PREFIX) ? new ResourceLocation(location.getNamespace(), path.substring(MODEL_PREFIX.length()) + ".json") : null;
    }

    /** Resolves local texture references before Forge collects the model's dependencies. */
    public static void rewriteModelTextures(ResourceLocation location, ModelBlock model) {
        ResourceLocation file = modelFile(location);
        if (file == null) return;
        model.textures.replaceAll((key, value) -> {
            if (value.startsWith("#") || !(value.startsWith("./") || value.startsWith("../") || value.startsWith("~/")
                || value.startsWith("optifine/") || value.startsWith("mcpatcher/"))) return value;
            return CitAtlasSprite.id(file(file, value, ".png")).toString();
        });
    }
}
