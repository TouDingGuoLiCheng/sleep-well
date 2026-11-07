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
    /** Source is a 64x64 wine fox icon, drawn as large as the 24x26 tab plate allows. */
    private static final int ICON_SRC = 64;
    private static final int ICON_DRAW = 24;
    private static final int ICON_INSET_X = 0;
    private static final int ICON_INSET_Y = 1;
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
        int shown = gui instanceof SleepWellSettingsScreen screen ? screen.page() : -1;
        for (int page : new int[]{OpenSettingsPacket.PAGE_SETTINGS, OpenSettingsPacket.PAGE_QUILT}) {
            boolean selected = shown == page;
            int tabX = x + (page == OpenSettingsPacket.PAGE_SETTINGS ? 0 : TAB_SPACING);
            event.addButton("sleep_well_tab_" + page, new SleepWellTabButton(tabX, topY, selected, page,
                    button -> {
                        if (!selected) {
                            SleepWellNetwork.CHANNEL.sendToServer(new OpenSettingsPacket(maid.getId(), page));
                        }
                    }));
        }
    }

    private static final class SleepWellTabButton extends MaidTabButton {
        private final boolean selected;
        private final int page;

        private SleepWellTabButton(int x, int y, boolean selected, int page, OnPress onPress) {
            super(x, y, TAB_ID, "sleep_well", onPress);
            this.selected = selected;
            this.page = page;
            this.active = !selected;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            RenderSystem.enableDepthTest();
            if (this.selected) {
                graphics.blit(SIDE, this.getX(), this.getY(), SIDE_SELECTED_U, SIDE_SELECTED_V,
                        TAB_W, TAB_H, 256, 256);
            }
            if (this.page == OpenSettingsPacket.PAGE_SETTINGS) {
                // Same inset as TLM's own tabs: a 16x16 icon in the upper middle of the 24x26 plate.
                graphics.blit(ICON, this.getX() + ICON_INSET_X, this.getY() + ICON_INSET_Y,
                        ICON_DRAW, ICON_DRAW, 0, 0, ICON_SRC, ICON_SRC, ICON_SRC, ICON_SRC);
            } else {
                // Placeholder quilt icon: a 3x3 gingham patch, drawn with rectangles so it needs no art yet.
                int px = this.getX() + 3;
                int py = this.getY() + 3;
                int cell = 6;
                for (int cx = 0; cx < 3; cx++) {
                    for (int cy = 0; cy < 3; cy++) {
                        boolean dark = (cx + cy) % 2 == 0;
                        graphics.fill(px + cx * cell, py + cy * cell, px + cx * cell + cell,
                                py + cy * cell + cell, dark ? 0xFF7A5C8F : 0xFFE8DDF0);
                    }
                }
                graphics.renderOutline(px - 1, py - 1, cell * 3 + 2, cell * 3 + 2, 0xFF3A2C46);
            }
        }
    }
}
