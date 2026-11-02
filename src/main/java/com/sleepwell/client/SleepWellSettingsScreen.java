package com.sleepwell.client;

import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.AbstractMaidContainerGui;
import com.sleepwell.SleepWellMod;
import com.sleepwell.gui.SleepWellContainer;
import com.sleepwell.network.SaveSettingPacket;
import com.sleepwell.network.SleepWellNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** The Sleep Well page inside the maid GUI: three switches, two global and one per maid. */
public class SleepWellSettingsScreen extends AbstractMaidContainerGui<SleepWellContainer> {
    private static final int PANEL_X = 80;
    private static final int PANEL_Y = 28;
    private static final int PANEL_W = 176;
    private static final int PANEL_H = 136;
    private static final int ROW_X = 96;
    private static final int ROW_W = 144;
    private static final int ROW_H = 20;
    private static final int FIRST_ROW_Y = 44;
    private static final int ROW_STEP = 30;
    private static final int TITLE_Y = 32;
    private static final int TITLE_COLOR = 0xF2EEE4;
    /** PixelLab-generated wooden board with a quilted bottom edge. */
    private static final ResourceLocation PANEL =
            new ResourceLocation(SleepWellMod.MOD_ID, "textures/gui/sleep_well_panel.png");

    private final boolean[] state = new boolean[3];

    public SleepWellSettingsScreen(SleepWellContainer container, Inventory inventory, Component title) {
        super(container, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        addToggle(0, this.menu.sleepFallback(), "gui.sleep_well.sleep_fallback", FIRST_ROW_Y);
        addToggle(1, this.menu.campfireFlee(), "gui.sleep_well.campfire_flee", FIRST_ROW_Y + ROW_STEP);
        Button winter = addToggle(2, this.menu.winterSkip(), "gui.sleep_well.winter_skip", FIRST_ROW_Y + ROW_STEP * 2);
        if (!this.menu.sereneSeasons()) {
            winter.active = false;
            winter.setMessage(Component.translatable("gui.sleep_well.no_serene_seasons"));
        }
    }

    private Button addToggle(int key, boolean initial, String labelKey, int y) {
        this.state[key] = initial;
        Button button = Button.builder(label(labelKey, initial), b -> {
            this.state[key] = !this.state[key];
            b.setMessage(label(labelKey, this.state[key]));
            SleepWellNetwork.CHANNEL.sendToServer(
                    new SaveSettingPacket(this.menu.entityId(), (byte) key, this.state[key]));
        }).bounds(this.leftPos + ROW_X, this.topPos + y, ROW_W, ROW_H).build();
        return this.addRenderableWidget(button);
    }

    private static Component label(String labelKey, boolean on) {
        return Component.translatable(labelKey)
                .append("  ")
                .append(Component.translatable(on ? "gui.sleep_well.on" : "gui.sleep_well.off"));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        graphics.blit(PANEL, this.leftPos + PANEL_X, this.topPos + PANEL_Y,
                0, 0, PANEL_W, PANEL_H, 256, 256);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, Component.translatable("gui.sleep_well.title"),
                ROW_X, TITLE_Y, TITLE_COLOR, false);
    }
}
