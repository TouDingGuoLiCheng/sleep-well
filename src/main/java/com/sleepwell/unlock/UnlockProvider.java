package com.sleepwell.unlock;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Capability provider holding one player's unlocks. */
public final class UnlockProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
    private final PlayerUnlocks unlocks;
    private final LazyOptional<PlayerUnlocks> optional;

    public UnlockProvider(PlayerUnlocks unlocks) {
        this.unlocks = unlocks;
        this.optional = LazyOptional.of(() -> unlocks);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == UnlockCapabilities.PLAYER_UNLOCKS ? this.optional.cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return this.unlocks.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.unlocks.deserializeNBT(nbt);
    }
}
