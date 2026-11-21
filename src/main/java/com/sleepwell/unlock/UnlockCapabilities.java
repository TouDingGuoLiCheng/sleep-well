package com.sleepwell.unlock;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

/** Registry handle for {@link PlayerUnlocks}. */
public final class UnlockCapabilities {
    public static final Capability<PlayerUnlocks> PLAYER_UNLOCKS =
            CapabilityManager.get(new CapabilityToken<>() {
            });

    private UnlockCapabilities() {
    }

    /** The player's unlocks; a throw-away object only when the capability is missing. */
    public static PlayerUnlocks of(Player player) {
        return player.getCapability(PLAYER_UNLOCKS).orElseGet(PlayerUnlocks::new);
    }
}
