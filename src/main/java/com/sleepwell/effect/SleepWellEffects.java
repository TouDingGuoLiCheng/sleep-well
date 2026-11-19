package com.sleepwell.effect;

import com.sleepwell.SleepWellMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The two status effects sleep pays out: 干劲 after a real sleep in her own window, 困倦 when she skipped it.
 * <p>
 * Icons are looked up by registry name the vanilla way: {@code textures/mob_effect/vigor.png} and
 * {@code textures/mob_effect/drowsy.png}.
 */
public final class SleepWellEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, SleepWellMod.MOD_ID);

    public static final RegistryObject<MobEffect> VIGOR = EFFECTS.register("vigor", VigorEffect::new);
    public static final RegistryObject<MobEffect> DROWSY = EFFECTS.register("drowsy", DrowsyEffect::new);

    private SleepWellEffects() {
    }
}
