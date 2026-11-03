package com.sleepwell.client;

import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.AbstractMaidContainerGui;
import com.sleepwell.SleepWellMod;
import com.sleepwell.gui.SleepWellContainer;
import com.sleepwell.network.SaveSettingPacket;
import com.sleepwell.network.SleepWellNetwork;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
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
    private static final int FIRST_ROW_Y = 48;
    private static final int ROW_STEP = 24;
    private static final int TITLE_Y = 32;
    /** Deep warm brown, because the sunny backdrop is far too bright for white text. */
    private static final int TITLE_COLOR = 0x4A3324;
    /** PixelLab art: a cosy sunlit bed, used as the page backdrop behind the translucent switches. */
    private static final ResourceLocation BACKGROUND =
            new ResourceLocation(SleepWellMod.MOD_ID, "textures/gui/sleep_well_bg.png");

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
        Button button = new SoftButton(this.leftPos + ROW_X, this.topPos + y, ROW_W, ROW_H,
                label(labelKey, initial), b -> {
            this.state[key] = !this.state[key];
            b.setMessage(label(labelKey, this.state[key]));
            SleepWellNetwork.CHANNEL.sendToServer(
                    new SaveSettingPacket(this.menu.entityId(), (byte) key, this.state[key]));
        });
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
        graphics.blit(BACKGROUND, this.leftPos + PANEL_X, this.topPos + PANEL_Y,
                PANEL_W, PANEL_H, 0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, Component.translatable("gui.sleep_well.title"),
                ROW_X, TITLE_Y, TITLE_COLOR, false);
    }

    /**
     * Acrylic-style plate: a translucent body plus a one pixel light border, so the background art
     * stays visible behind the switches instead of being covered by the vanilla button texture.
     */
    private static final class SoftButton extends Button {
        private SoftButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, supplier -> supplier.get());
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            boolean hovered = this.active && isHoveredOrFocused();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.fill(x, y, x + w, y + h, hovered ? 0x8C33405E : 0x66141A2B);
            graphics.fill(x, y, x + w, y + 1, 0x55FFFFFF);
            graphics.renderOutline(x, y, w, h, hovered ? 0xD8FFFFFF : 0x77FFFFFF);
            graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                    x + w / 2, y + (h - 8) / 2, this.active ? 0xF2EEE4 : 0x8C8C8C);
            RenderSystem.disableBlend();
        }
    }
}
