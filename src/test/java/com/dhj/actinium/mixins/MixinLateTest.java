package com.dhj.actinium.mixins;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MixinLateTest {
    @Test
    void selectsExactConfigsForLoadedMods() {
        assertEquals(Set.of(), Set.copyOf(MixinLate.configsFor(modId -> false, className -> false)));

        // The lumenized config is gated on the embedded bloom class, not on a mod id:
        // it loads when the class is present even with no matching mod (GTCEu provides the
        // same class without a "lumenized" mod id), and stays off when only the mod id matches
        // but the class is absent.
        assertEquals(
            Set.of("mixins.actinium.lumenized.json"),
            Set.copyOf(MixinLate.configsFor(modId -> false, "gregtech.client.utils.BloomEffectUtil"::equals))
        );

        assertEquals(
            Set.of(),
            Set.copyOf(MixinLate.configsFor("lumenized"::equals, className -> false))
        );

        assertEquals(
            Set.of(),
            Set.copyOf(MixinLate.configsFor("betterfoliage"::equals, className -> false))
        );

        assertEquals(
            Set.of("mixins.actinium.betterfoliage.json"),
            Set.copyOf(MixinLate.configsFor(
                "betterfoliage"::equals,
                "mods.betterfoliage.client.Hooks"::equals
            ))
        );

        assertEquals(
            Set.of("mixins.actinium.rlfoliage.json"),
            Set.copyOf(MixinLate.configsFor(
                "betterfoliage"::equals,
                "betterfoliage.render.feature.RenderingHandler"::equals
            ))
        );

        assertEquals(
            Set.of("mixins.actinium.ccl.json"),
            Set.copyOf(MixinLate.configsFor("codechickenlib"::equals, className -> false))
        );

        assertEquals(
            Set.of("mixins.actinium.cofhcore.json"),
            Set.copyOf(MixinLate.configsFor("cofhcore"::equals, className -> false))
        );

        assertEquals(
            Set.of("mixins.actinium.gvclib.json"),
            Set.copyOf(MixinLate.configsFor("gvclib"::equals, className -> false))
        );

        assertEquals(
            Set.of("mixins.actinium.dsurround.json"),
            Set.copyOf(MixinLate.configsFor("dsurround"::equals, className -> false))
        );

        assertEquals(Set.of(), Set.copyOf(MixinLate.configsFor("mekanism"::equals, name -> false)));
        assertEquals(
            Set.of("mixins.actinium.mekanism.json"),
            Set.copyOf(MixinLate.configsFor("mekanism"::equals, "mekanism.client.entity.ParticleLaser"::equals))
        );

        assertEquals(
            Set.of("mixins.actinium.embers.json"),
            Set.copyOf(MixinLate.configsFor("embers"::equals, className -> false))
        );

        assertEquals(Set.of(), Set.copyOf(MixinLate.configsFor("librarianlib"::equals, name -> false)));
        assertEquals(
            Set.of("mixins.actinium.librarianlib.json"),
            Set.copyOf(MixinLate.configsFor(
                "librarianlib"::equals, "com.teamwizardry.librarianlib.core.client.RenderHookHandler$blockHooks$1"::equals
            ))
        );

        assertEquals(
            Set.of("mixins.actinium.mmce.json"),
            Set.copyOf(MixinLate.configsFor("modularmachinery"::equals, className -> false))
        );

        assertEquals(
            Set.of(
                "mixins.actinium.gibbed.json",
                "mixins.actinium.ichunutil.json",
                "mixins.actinium.lumenized.json",
                "mixins.actinium.revoui.json",
                "mixins.actinium.betterfoliage.json",
                "mixins.actinium.rlfoliage.json",
                "mixins.actinium.ccl.json",
                "mixins.actinium.voxelmap.json",
                "mixins.actinium.extrautils2.json",
                "mixins.actinium.cofhcore.json",
                "mixins.actinium.oldresearch.json",
                "mixins.actinium.botania.json",
                "mixins.actinium.hbm.json",
                "mixins.actinium.gvclib.json",
                "mixins.actinium.storagedrawers.json",
                "mixins.actinium.scannable.json",
                "mixins.actinium.littletiles.json",
                "mixins.actinium.architecturecraft.json",
                "mixins.actinium.obscuretooltips.json",
                "mixins.actinium.dsurround.json",
                "mixins.actinium.mekanism.json",
                "mixins.actinium.embers.json",
                "mixins.actinium.mmce.json",
                "mixins.actinium.librarianlib.json",
                "mixins.actinium.evilcraft.json"
            ),
            Set.copyOf(MixinLate.configsFor(modId -> true, className -> true))
        );
    }

    @Test
    void evilCraftConfigRequiresBothModsAndTheChaliceRenderer() {
        var mods = Set.of("evilcraft", "cyclopscore");
        String renderer = "org.cyclops.evilcraft.client.render.tileentity.RenderTileEntityEntangledChalice";

        assertEquals(Set.of(), Set.copyOf(MixinLate.configsFor("evilcraft"::equals, renderer::equals)));
        assertEquals(Set.of(), Set.copyOf(MixinLate.configsFor("cyclopscore"::equals, renderer::equals)));
        assertEquals(Set.of(), Set.copyOf(MixinLate.configsFor(mods::contains, name -> false)));
        assertEquals(Set.of("mixins.actinium.evilcraft.json"),
            Set.copyOf(MixinLate.configsFor(mods::contains, renderer::equals)));
    }
}
