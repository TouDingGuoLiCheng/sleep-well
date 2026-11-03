package com.sleepwell.client;

import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.AbstractMaidContainerGui;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sleepwell.SleepWellMod;
import com.sleepwell.config.SleepWellConfig;
import com.sleepwell.gui.SleepWellContainer;
import com.sleepwell.network.SaveSettingPacket;
import com.sleepwell.network.SaveSkipTasksPacket;
import com.sleepwell.network.SleepWellNetwork;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * The Sleep Well page inside the maid GUI: three switches, two global and one per maid.
 * <p>
 * The winter switch carries a gear on its right edge; the gear opens the scrolling task list, because
 * "which tasks count as work" is a config list the page had no way to edit before.
 */
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
    /** Width carved out of the winter row for the gear. */
    private static final int GEAR_W = 20;
    private static final int GEAR_GAP = 2;
    private static final int PICKER_X = 92;
    private static final int PICKER_Y = 40;
    private static final int PICKER_W = 152;
    private static final int PICKER_H = 124;
    /** Deep warm brown, because the sunny backdrop is far too bright for white text. */
    private static final int TITLE_COLOR = 0x4A3324;
    /** PixelLab art: a cosy sunlit bed, used as the page backdrop behind the translucent switches. */
    private static final ResourceLocation BACKGROUND =
            new ResourceLocation(SleepWellMod.MOD_ID, "textures/gui/sleep_well_bg.png");

    private final boolean[] state = new boolean[3];
    private final List<Button> toggles = new ArrayList<>();
    private Button gear;
    private Button done;
    private WinterSkipPicker picker;

    public SleepWellSettingsScreen(SleepWellContainer container, Inventory inventory, Component title) {
        super(container, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        toggles.clear();
        addToggle(0, this.menu.sleepFallback(), "gui.sleep_well.sleep_fallback", FIRST_ROW_Y, ROW_W);
        addToggle(1, this.menu.campfireFlee(), "gui.sleep_well.campfire_flee", FIRST_ROW_Y + ROW_STEP, ROW_W);
        Button winter = addToggle(2, this.menu.winterSkip(), "gui.sleep_well.winter_skip",
                FIRST_ROW_Y + ROW_STEP * 2, ROW_W - GEAR_W - GEAR_GAP);
        if (!this.menu.sereneSeasons()) {
            winter.active = false;
            winter.setMessage(Component.translatable("gui.sleep_well.no_serene_seasons"));
        }

        this.picker = new WinterSkipPicker(SleepWellConfig.WINTER_SKIP_TASKS.get(), maidOf(this.menu.entityId()),
                this::saveSkipTasks);
        this.gear = this.addRenderableWidget(new GearButton(this.leftPos + ROW_X + ROW_W - GEAR_W,
                this.topPos + FIRST_ROW_Y + ROW_STEP * 2, GEAR_W, ROW_H, b -> openPicker()));
        this.gear.active = this.menu.sereneSeasons();
        this.done = this.addRenderableWidget(Button.builder(Component.translatable("gui.sleep_well.done"),
                        b -> closePicker())
                .bounds(this.leftPos + PICKER_X + (PICKER_W - 56) / 2,
                        this.topPos + PICKER_Y + PICKER_H - 20, 56, 16)
                .build());
        this.done.visible = false;
    }

    private Button addToggle(int key, boolean initial, String labelKey, int y, int width) {
        this.state[key] = initial;
        Button button = new SoftButton(this.leftPos + ROW_X, this.topPos + y, width, ROW_H,
                label(labelKey, initial), b -> {
            this.state[key] = !this.state[key];
            b.setMessage(label(labelKey, this.state[key]));
            SleepWellNetwork.CHANNEL.sendToServer(
                    new SaveSettingPacket(this.menu.entityId(), (byte) key, this.state[key]));
        });
        this.toggles.add(button);
        return this.addRenderableWidget(button);
    }

    /** The maid herself, so the list matches her own task button and skips hidden compat tasks. */
    private static EntityMaid maidOf(int entityId) {
        var level = Minecraft.getInstance().level;
        return level != null && level.getEntity(entityId) instanceof EntityMaid maid ? maid : null;
    }

    private static Component label(String labelKey, boolean on) {
        return Component.translatable(labelKey)
                .append("  ")
                .append(Component.translatable(on ? "gui.sleep_well.on" : "gui.sleep_well.off"));
    }

    /** Ticking a box applies the list at once, the same way the switches above behave. */
    private void saveSkipTasks(List<String> tasks) {
        SleepWellConfig.WINTER_SKIP_TASKS.set(tasks);
        SleepWellNetwork.CHANNEL.sendToServer(new SaveSkipTasksPacket(this.menu.entityId(), tasks));
    }

    private void openPicker() {
        if (this.picker == null) {
            return;
        }
        this.picker.setOpen(true);
        this.toggles.forEach(button -> button.visible = false);
        this.gear.visible = false;
        this.done.visible = true;
    }

    private void closePicker() {
        if (this.picker == null) {
            return;
        }
        this.picker.setOpen(false);
        this.toggles.forEach(button -> button.visible = true);
        this.gear.visible = true;
        this.done.visible = false;
    }

    private boolean pickerOpen() {
        return this.picker != null && this.picker.isOpen();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!pickerOpen()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // The overlay is modal: everything it does not need is swallowed so the page behind stays put.
        this.picker.mouseClicked(mouseX, mouseY, button);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (pickerOpen() && this.picker.mouseScrolled(delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (pickerOpen()) {
            this.picker.mouseDragged(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.picker != null) {
            this.picker.mouseReleased();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        graphics.blit(BACKGROUND, this.leftPos + PANEL_X, this.topPos + PANEL_Y,
                PANEL_W, PANEL_H, 0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (pickerOpen()) {
            this.picker.render(graphics, this.font,
                    this.leftPos + PICKER_X, this.topPos + PICKER_Y, PICKER_W, PICKER_H, mouseX, mouseY);
        }
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
    private static class SoftButton extends Button {
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

    /** The gear next to the winter switch; drawn with rectangles so it needs no texture. */
    private static final class GearButton extends SoftButton {
        private GearButton(int x, int y, int width, int height, OnPress onPress) {
            super(x, y, width, height, Component.empty(), onPress);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            boolean hovered = this.active && isHoveredOrFocused();
            int cx = x + w / 2;
            int cy = y + h / 2;
            int color = this.active ? (hovered ? 0xFFFFFFFF : 0xE8F2EEE4) : 0x8C8C8C;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.fill(x, y, x + w, y + h, hovered ? 0x8C33405E : 0x66141A2B);
            graphics.renderOutline(x, y, w, h, hovered ? 0xD8FFFFFF : 0x77FFFFFF);
            // Body, four teeth, then a dark hub punched back out.
            graphics.fill(cx - 3, cy - 3, cx + 3, cy + 3, color);
            graphics.fill(cx - 5, cy - 1, cx + 5, cy + 1, color);
            graphics.fill(cx - 1, cy - 5, cx + 1, cy + 5, color);
            graphics.fill(cx - 4, cy - 4, cx - 2, cy - 2, color);
            graphics.fill(cx + 2, cy - 4, cx + 4, cy - 2, color);
            graphics.fill(cx - 4, cy + 2, cx - 2, cy + 4, color);
            graphics.fill(cx + 2, cy + 2, cx + 4, cy + 4, color);
            graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFF141A2B);
            RenderSystem.disableBlend();
        }
    }
}
