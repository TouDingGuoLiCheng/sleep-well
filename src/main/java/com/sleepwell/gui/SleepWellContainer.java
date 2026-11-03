package com.sleepwell.gui;

import com.github.tartaricacid.touhoulittlemaid.inventory.container.AbstractMaidContainer;
import com.sleepwell.SleepWellMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Menu behind the Sleep Well page; it only carries the switch states. */
public class SleepWellContainer extends AbstractMaidContainer {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, SleepWellMod.MOD_ID);

    public static final RegistryObject<MenuType<SleepWellContainer>> TYPE = MENUS.register("settings",
            () -> IForgeMenuType.create(SleepWellContainer::fromNetwork));

    private final int entityId;
    private final boolean sleepFallback;
    private final boolean campfireFlee;
    private final boolean winterSkip;
    private final boolean lieIn;
    private final boolean sereneSeasons;

    public SleepWellContainer(int id, Inventory inventory, int entityId,
                              boolean sleepFallback, boolean campfireFlee, boolean winterSkip,
                              boolean lieIn, boolean sereneSeasons) {
        super(TYPE.get(), id, inventory, entityId);
        this.entityId = entityId;
        this.sleepFallback = sleepFallback;
        this.campfireFlee = campfireFlee;
        this.winterSkip = winterSkip;
        this.lieIn = lieIn;
        this.sereneSeasons = sereneSeasons;
    }

    public int entityId() {
        return entityId;
    }

    public boolean sleepFallback() {
        return sleepFallback;
    }

    public boolean campfireFlee() {
        return campfireFlee;
    }

    public boolean winterSkip() {
        return winterSkip;
    }

    public boolean lieIn() {
        return lieIn;
    }

    public boolean sereneSeasons() {
        return sereneSeasons;
    }

    /** No item slots on this page; shift-click does nothing. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    public static void writeData(FriendlyByteBuf buf, int entityId, boolean sleepFallback,
                                 boolean campfireFlee, boolean winterSkip, boolean lieIn, boolean sereneSeasons) {
        buf.writeVarInt(entityId);
        buf.writeBoolean(sleepFallback);
        buf.writeBoolean(campfireFlee);
        buf.writeBoolean(winterSkip);
        buf.writeBoolean(lieIn);
        buf.writeBoolean(sereneSeasons);
    }

    private static SleepWellContainer fromNetwork(int windowId, Inventory inventory, FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        return new SleepWellContainer(windowId, inventory, entityId,
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    public static MenuProvider create(int entityId, boolean sleepFallback, boolean campfireFlee,
                                      boolean winterSkip, boolean lieIn, boolean sereneSeasons) {
        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("gui.sleep_well.title");
            }

            @Override
            public SleepWellContainer createMenu(int id, Inventory inventory, Player player) {
                return new SleepWellContainer(id, inventory, entityId,
                        sleepFallback, campfireFlee, winterSkip, lieIn, sereneSeasons);
            }
        };
    }
}
