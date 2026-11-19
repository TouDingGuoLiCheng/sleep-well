package com.sleepwell.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 干劲: +30 % movement speed for a few minutes after a proper sleep.
 * <p>
 * The bonus lives on the MOVEMENT_SPEED attribute with the same operation as vanilla Speed, so it adds up the
 * way players already expect and shows in the attribute screen instead of quietly patching her speed.
 */
public class VigorEffect extends MobEffect {
    /** Same operation as vanilla Speed: everything already on the attribute is multiplied by 1.30. */
    public static final double SPEED_BONUS = 0.30D;
    /** Fixed id so a re-applied effect replaces its own modifier instead of stacking copies. */
    private static final String SPEED_MODIFIER = "a1b5e8f0-3c4d-4e2b-9a77-6f1d2c3b4a55";

    public VigorEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xF2A03A);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, SPEED_MODIFIER, SPEED_BONUS,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}
