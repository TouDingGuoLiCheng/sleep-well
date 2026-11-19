package com.sleepwell.maid;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Capability provider holding a maid's Sleep Well settings. */
public final class MaidSettingsProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
    private final MaidSettings settings;
    private final LazyOptional<MaidSettings> optional;

    public MaidSettingsProvider(MaidSettings settings) {
        this.settings = settings;
        this.optional = LazyOptional.of(() -> settings);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == MaidCapabilities.MAID_SETTINGS ? optional.cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return settings.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        settings.deserializeNBT(nbt);
    }
}
