package com.sleepwell.client;

import com.github.tartaricacid.touhoulittlemaid.api.event.client.MaidContainerGuiEvent;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.AbstractMaidContainerGui;
import com.github.tartaricacid.touhoulittlemaid.client.gui.widget.button.MaidTabButton;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sleepwell.SleepWellMod;
import com.sleepwell.network.OpenSettingsPacket;
import com.sleepwell.network.SleepWellNetwork;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Adds the Sleep Well page tab to the maid GUI.
 * <p>
 * The tab is laid out after every tab already on the row (built-in ones and other addons), so it never
 * overlaps the Eat Properly tab. Clicking it asks the server to open our own page.
 */
@Mod.EventBusSubscriber(modid = SleepWellMod.MOD_ID, value = Dist.CLIENT)
public final class SleepWellMaidTab {
    private static final ResourceLocation ICON =
            new ResourceLocation(SleepWellMod.MOD_ID, "textures/gui/maid_tab_icon.png");
    private static final ResourceLocation SIDE =
            new ResourceLocation("touhou_little_maid", "textures/gui/maid_gui_side.png");
    /** Cosmetic only: our button draws its own icon and sends its own packet. */
    private static final int TAB_ID = 6;
    private static final int TOP_TAB_Y_OFFSET = 5;
    private static final int TAB_SPACING = 25;
    private static final int FIRST_EXTERNAL_TAB_X = 194;
    private static final int TAB_W = 24;
    private static final int TAB_H = 26;
    /** Source texture size: the untouched PixelLab art, never cropped or downscaled on disk. */
    private static final int ICON_SRC_W = 176;
    private static final int ICON_SRC_H = 136;
    private static final int SIDE_SELECTED_U = 107;
    private static final int SIDE_SELECTED_V = 21;

    private SleepWellMaidTab() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onGuiInit(MaidContainerGuiEvent.Init event) {
        AbstractMaidContainerGui<?> gui = event.getGui();
        EntityMaid maid = gui.getMaid();
        if (maid == null) {
            return;
        }
        int topY = event.getTopPos() + TOP_TAB_Y_OFFSET;
        int maxX = Integer.MIN_VALUE;
        for (var child : gui.children()) {
            if (child instanceof MaidTabButton tab && tab.getY() == topY) {
                maxX = Math.max(maxX, tab.getX());
            }
        }
        int x = maxX == Integer.MIN_VALUE ? event.getLeftPos() + FIRST_EXTERNAL_TAB_X : maxX + TAB_SPACING;
        boolean selected = gui instanceof SleepWellSettingsScreen;
        event.addButton("sleep_well_tab", new SleepWellTabButton(x, topY, selected, button -> {
            if (!selected) {
                SleepWellNetwork.CHANNEL.sendToServer(new OpenSettingsPacket(maid.getId()));
            }
        }));
    }

    private static final class SleepWellTabButton extends MaidTabButton {
        private final boolean selected;

        private SleepWellTabButton(int x, int y, boolean selected, OnPress onPress) {
            super(x, y, TAB_ID, "sleep_well", onPress);
            this.selected = selected;
            this.active = !selected;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            RenderSystem.enableDepthTest();
            if (this.selected) {
                graphics.blit(SIDE, this.getX(), this.getY(), SIDE_SELECTED_U, SIDE_SELECTED_V,
                        TAB_W, TAB_H, 256, 256);
            }
            // Fit the whole drawing inside the tab: width-bound, aspect kept, vertically centred.
            // Note the 11-arg overload: destination size first, then the source rect.
            int w = TAB_W;
            int h = Math.max(1, Math.round(w * (float) ICON_SRC_H / ICON_SRC_W));
            graphics.blit(ICON, this.getX(), this.getY() + (TAB_H - h) / 2,
                    w, h, 0, 0, ICON_SRC_W, ICON_SRC_H, ICON_SRC_W, ICON_SRC_H);
        }
    }
}
