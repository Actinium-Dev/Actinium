package com.dhj.actinium.resourcepack;

import com.dhj.actinium.render.FastLitItemDisplayListCache;
import com.dhj.actinium.render.terrain.ActiniumWorldRenderer;
import com.gtnewhorizons.angelica.rendering.items.DroppedItemInstancer;
import com.gtnewhorizons.angelica.rendering.tesr.AngelicaTesrMeshCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/** Coordinates pack state with the atlas and models that depend on it. */
public final class ResourcePackReload {
    private static final List<Module<?, ?>> MODULES = new ArrayList<>();
    private static volatile Object[] published = new Object[0];
    private static List<IResourcePack> activePacks = List.of();
    private static volatile Frame current;

    private ResourcePackReload() {
    }

    /** Defines the preparation and publication steps for one type of pack rule. */
    public interface Module<P, S> {
        /** Returns the client option controlling this module. */
        String option();

        /** Parses all enabled rules using the resources for this reload. */
        P prepare(ResourcePackIndex index);

        /** Registers dependencies before the block atlas is stitched. */
        default void registerSprites(P prepared, TextureMap atlas) {
        }

        /** Contributes models while the result of baking is still mutable. */
        default void modelBake(P prepared, ModelBakeEvent event) {
        }

        /** Resolves the prepared rules against the stitched atlas and returns renderable state. */
        S finish(P prepared, ResourcePackIndex index, TextureMap atlas);

        /** Returns whether the module's snapshot contains atlas-dependent data. */
        default boolean requiresAtlas() {
            return false;
        }
    }

    /** Gives render code access to the last fully published result for one module. */
    public record Handle<S>(int index) {
        @SuppressWarnings("unchecked")
        public S get() {
            Object[] snapshot = published;
            return index < snapshot.length ? (S) snapshot[index] : null;
        }

        /** Reads the preparation value during a synchronous listener call for modules with identical P and S. */
        @SuppressWarnings("unchecked")
        public S getDuringReload() {
            Frame frame = current;
            if (frame != null && frame.owner == Thread.currentThread() && index < frame.prepared.size()) {
                Prepared<?, ?> prepared = frame.prepared.get(index);
                return prepared == null ? null : (S) prepared.value();
            }
            return get();
        }
    }

    /** Registers a module before Minecraft loads its models. */
    public static <P, S> Handle<S> register(Module<P, S> module) {
        var handle = new Handle<S>(MODULES.size());
        MODULES.add(module);
        return handle;
    }

    public static void install() {
        MinecraftForge.EVENT_BUS.register(new ResourcePackReload());
    }

    /** Runs the full resource manager reload with the actual active pack list. */
    public static void reload(IResourceManager manager, List<IResourcePack> packs, Runnable action) {
        if (manager != Minecraft.getMinecraft().getResourceManager()) {
            action.run();
            return;
        }
        List<IResourcePack> previousPacks = activePacks;
        activePacks = List.copyOf(packs);
        try {
            runFrame(manager, action, false);
        } catch (RuntimeException | Error failure) {
            activePacks = previousPacks;
            throw failure;
        }
    }

    /** Indexes resources after Forge installs the packs and before its listeners run. */
    public static void beforeListeners(IResourceManager manager) {
        if (current != null && current.manager == manager && current.index == null) {
            current.prepare();
        }
    }

    /** Handles the initial model listener registration, which runs outside a full reload. */
    public static void bakeModels(IResourceManager manager, Runnable action) {
        if (current != null) {
            action.run();
        } else {
            runFrame(manager, action, true);
        }
    }

    private static void runFrame(IResourceManager manager, Runnable action, boolean prepareImmediately) {
        if (manager != Minecraft.getMinecraft().getResourceManager() || MODULES.isEmpty()) {
            action.run();
            return;
        }
        if (current != null) {
            throw new IllegalStateException("Nested client resource pack reload");
        }
        var renderer = Minecraft.getMinecraft().world != null ? ActiniumWorldRenderer.instanceNullable() : null;
        boolean suspended = renderer != null && renderer.suspendForResourceReload();
        try {
            current = new Frame(manager);
            if (prepareImmediately) {
                current.prepare();
            }
            action.run();
            if (current.index != null) {
                publish(current);
            }
        } finally {
            current = null;
            if (suspended) {
                renderer.resumeAfterResourceReload();
            }
        }
    }

    private static void publish(Frame frame) {
        Object[] previous = published;
        Object[] next = new Object[frame.prepared.size()];
        for (int i = 0; i < next.length; i++) {
            Prepared<?, ?> prepared = frame.prepared.get(i);
            if (prepared != null) {
                if (prepared.module.requiresAtlas() && !frame.atlasStitched) {
                    next[i] = i < previous.length ? previous[i] : null;
                } else {
                    next[i] = prepared.finish(frame.index, frame.atlas);
                }
            }
        }
        FastLitItemDisplayListCache.clear();
        AngelicaTesrMeshCache.INSTANCE.clear();
        DroppedItemInstancer.clear();
        published = next;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void registerSprites(TextureStitchEvent.Pre event) {
        if (current != null && event.getMap() == Minecraft.getMinecraft().getTextureMapBlocks()) {
            current.atlas = event.getMap();
            for (var prepared : current.prepared) {
                if (prepared != null) {
                    prepared.registerSprites(current.atlas);
                }
            }
        }
    }

    @SubscribeEvent
    public void markStitched(TextureStitchEvent.Post event) {
        if (current != null && event.getMap() == current.atlas) {
            current.atlasStitched = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onModelBake(ModelBakeEvent event) {
        if (current != null) {
            for (var prepared : current.prepared) {
                if (prepared != null) {
                    prepared.modelBake(event);
                }
            }
        }
    }

    private static final class Frame {
        private final Thread owner = Thread.currentThread();
        private final IResourceManager manager;
        private final List<Prepared<?, ?>> prepared = new ArrayList<>(MODULES.size());
        private ResourcePackIndex index;
        private TextureMap atlas;
        private boolean atlasStitched;

        private Frame(IResourceManager manager) {
            this.manager = manager;
        }

        private void prepare() {
            this.index = ResourcePackIndex.load(manager, activePacks);
            for (var module : MODULES) {
                this.prepared.add(index.enabled(module.option()) ? prepare(module, index) : null);
            }
        }

        private static <P, S> Prepared<P, S> prepare(Module<P, S> module, ResourcePackIndex index) {
            return new Prepared<>(module, module.prepare(index));
        }
    }

    private record Prepared<P, S>(Module<P, S> module, P value) {
        void registerSprites(TextureMap atlas) {
            module.registerSprites(value, atlas);
        }

        void modelBake(ModelBakeEvent event) {
            module.modelBake(value, event);
        }

        S finish(ResourcePackIndex index, TextureMap atlas) {
            return module.finish(value, index, atlas);
        }
    }
}
