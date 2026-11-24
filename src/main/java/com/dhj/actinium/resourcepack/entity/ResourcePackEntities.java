package com.dhj.actinium.resourcepack.entity;

import com.dhj.actinium.resourcepack.ResourcePackIndex;
import com.dhj.actinium.resourcepack.ResourcePackReload;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

public final class ResourcePackEntities {
    private static ResourcePackReload.Handle<RandomTexturePack> random;
    private static ResourcePackReload.Handle<IndependentEmissive> emissive;
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private ResourcePackEntities() {}

    public static void register() {
        random = ResourcePackReload.register(new ResourcePackReload.Module<RandomTexturePack, RandomTexturePack>() {
            @Override public String option() { return "randomEntities"; }
            @Override public RandomTexturePack prepare(ResourcePackIndex index) { return RandomTexturePack.load(index); }
            @Override public RandomTexturePack finish(RandomTexturePack prepared, ResourcePackIndex index, TextureMap atlas) { return prepared; }
        });
        emissive = ResourcePackReload.register(new ResourcePackReload.Module<IndependentEmissive, IndependentEmissive>() {
            @Override public String option() { return "emissive"; }
            @Override public IndependentEmissive prepare(ResourcePackIndex index) { return IndependentEmissive.load(index); }
            @Override public IndependentEmissive finish(IndependentEmissive prepared, ResourcePackIndex index, TextureMap atlas) { return prepared; }
        });
        LegacyEntityLines.register();
    }

    public static void entityAdded(Entity entity) { RandomTextureSubjects.entityAdded(entity); }

    /** Reports whether a legacy overlay was supplied by the active pack stack. */
    public static boolean hasOverlay(ResourceLocation id) {
        RandomTexturePack snapshot = random == null ? null : random.get();
        return snapshot != null && snapshot.contains(id);
    }

    public static Scope enter(Entity entity, boolean outline) {
        return new Scope(entity, null, !outline);
    }

    public static Scope enter(TileEntity tile, int destroyStage) {
        return new Scope(null, tile, destroyStage < 0);
    }

    public static ResourceLocation select(ResourceLocation original) {
        Scope scope = CURRENT.get();
        RandomTexturePack rules = random == null ? null : random.get();
        if (scope == null || rules == null || !rules.contains(original) || GLSMTextures.getActiveTextureUnit() != 0) return original;
        if (scope.subject == null) scope.subject = scope.entity == null
            ? RandomTextureSubjects.tile(scope.tile) : RandomTextureSubjects.entity(scope.entity, rules.usesCareer());
        return scope.subject == null ? original : rules.select(original, scope.subject);
    }

    public static void bound(TextureManager textures, ResourceLocation selected) {
        Scope scope = CURRENT.get();
        IndependentEmissive pack = emissive == null ? null : emissive.get();
        if (scope != null && scope.allowEmissive && pack != null && GLSMTextures.getActiveTextureUnit() == 0) {
            scope.draw = pack.bound(textures, selected);
        }
    }

    static IndependentEmissive.Draw activeDraw() {
        Scope scope = CURRENT.get();
        return scope == null ? null : scope.draw;
    }

    public static final class Scope implements AutoCloseable {
        private final Scope previous = CURRENT.get();
        private final Entity entity;
        private final TileEntity tile;
        private final boolean allowEmissive;
        private RandomTextureRules.Subject subject;
        private IndependentEmissive.Draw draw;

        private Scope(Entity entity, TileEntity tile, boolean allowEmissive) {
            this.entity = entity;
            this.tile = tile;
            this.allowEmissive = allowEmissive;
            CURRENT.set(this);
        }

        @Override
        public void close() {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
