package com.sleepwell.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sleepwell.config.SleepWellConfig;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

/** Registry handle plus a null-safe accessor for the per-maid settings. */
public final class MaidCapabilities {
    public static final Capability<MaidSettings> MAID_SETTINGS = CapabilityManager.get(new CapabilityToken<>() {
    });

    private MaidCapabilities() {
    }

    /** The maid's settings, or a throw-away default when the capability is missing (never for a real maid). */
    public static MaidSettings of(EntityMaid maid) {
        return maid.getCapability(MAID_SETTINGS)
                .orElseGet(() -> new MaidSettings(SleepWellConfig.WINTER_SKIP_DEFAULT_ENABLED.get()));
    }
}
