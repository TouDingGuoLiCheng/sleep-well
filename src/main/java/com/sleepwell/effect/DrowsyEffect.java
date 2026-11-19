package com.sleepwell.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 困倦: -15 % movement speed, the price of skipping her own sleep window. The mirror of {@link VigorEffect}.
 */
public class DrowsyEffect extends MobEffect {
    public static final double SPEED_PENALTY = -0.15D;
    private static final String SPEED_MODIFIER = "b2c6f9a1-4d5e-4f3c-8b88-7a2e3d4c5b66";

    public DrowsyEffect() {
        super(MobEffectCategory.HARMFUL, 0x7C8AA0);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, SPEED_MODIFIER, SPEED_PENALTY,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}
