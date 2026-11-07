package com.sleepwell.client;

import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.AbstractMaidContainerGui;
import com.sleepwell.SleepWellMod;
import com.sleepwell.config.SleepWellConfig;
import com.sleepwell.gui.SleepWellContainer;
import com.sleepwell.network.SaveSettingPacket;
import com.sleepwell.client.quilt.QuiltStyle;
import com.sleepwell.network.SaveQuiltStylePacket;
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
 * The Sleep Well page inside the maid GUI: four switches — two global (path fix, campfire flee) and two
 * per maid (no work in winter, lie-in / naps).
 * <p>
 * The winter switch carries a gear on its right edge; the gear opens a full window checkbox list, because
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
    /** Deep warm brown, because the sunny backdrop is far too bright for white text. */
    private static final int TITLE_COLOR = 0x4A3324;
    /** PixelLab art: a cosy sunlit bed, used as the page backdrop behind the translucent switches. */
    private static final ResourceLocation BACKGROUND =
            new ResourceLocation(SleepWellMod.MOD_ID, "textures/gui/sleep_well_bg.png");

    private final boolean[] state = new boolean[4];
    private final List<Button> toggles = new ArrayList<>();
    private Button gear;
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
        addToggle(3, this.menu.lieIn(), "gui.sleep_well.lie_in", FIRST_ROW_Y + ROW_STEP * 3,
                ROW_W - QUILT_ICON_W - GEAR_GAP);

        this.picker = new WinterSkipPicker(this.notHiddenTasks, SleepWellConfig.WINTER_SKIP_TASKS.get(),
                this::closePicker, this::saveSkipTasks);
        this.gear = this.addRenderableWidget(new GearButton(this.leftPos + ROW_X + ROW_W - GEAR_W,
                this.topPos + FIRST_ROW_Y + ROW_STEP * 2, GEAR_W, ROW_H, b -> openPicker()));
        this.gear.active = this.menu.sereneSeasons();
        this.addRenderableWidget(new QuiltIconButton(this.leftPos + ROW_X + ROW_W - QUILT_ICON_W,
                this.topPos + FIRST_ROW_Y + ROW_STEP * 3, QUILT_ICON_W, ROW_H, b -> openQuiltWall()));
    }

    /**
     * The quilt wall, as an overlay on this page instead of a page of its own: the quilt icon on the right of
     * the lie-in row opens it, and one plate per quilt is laid out three to a row inside it.
     */
    private static final int WALL_COLS = 3;
    private static final int WALL_CELL_W = 52;
    private static final int WALL_CELL_H = 34;
    private static final int WALL_GAP = 4;
    /** Where the icon sits: right edge of the lie-in row, mirroring the gear on the winter row. */
    private static final int QUILT_ICON_W = 20;

    private boolean quiltOpen;
    private String chosenQuilt;

    private void openQuiltWall() {
        this.quiltOpen = true;
        this.toggles.forEach(button -> button.visible = false);
        if (this.gear != null) {
            this.gear.visible = false;
        }
    }

    private void closeQuiltWall() {
        this.quiltOpen = false;
        this.toggles.forEach(button -> button.visible = true);
        if (this.gear != null) {
            this.gear.visible = this.menu.sereneSeasons();
        }
    }

    private int wallLeft() {
        return this.leftPos + ROW_X;
    }

    private int wallTop() {
        return this.topPos + FIRST_ROW_Y;
    }

    private int wallCellX(int index) {
        return wallLeft() + (index % WALL_COLS) * (WALL_CELL_W + WALL_GAP);
    }

    private int wallCellY(int index) {
        return wallTop() + (index / WALL_COLS) * (WALL_CELL_H + WALL_GAP);
    }

    /** Draws the overlay: dimmed page, then one plate per quilt with its state. */
    private void renderQuiltWall(GuiGraphics graphics, int mouseX, int mouseY) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight,
                0xD9141A2B);
        List<QuiltStyle> styles = QuiltStyle.all();
        String current = this.chosenQuilt != null ? this.chosenQuilt : this.menu.quiltStyle();
        for (int i = 0; i < styles.size(); i++) {
            QuiltStyle style = styles.get(i);
            boolean locked = this.menu.quiltLocked() && !style.isDefault();
            boolean selected = style.id.equals(current);
            int x = wallCellX(i);
            int y = wallCellY(i);
            boolean hovered = !locked && mouseX >= x && mouseX < x + WALL_CELL_W
                    && mouseY >= y && mouseY < y + WALL_CELL_H;
            graphics.fill(x, y, x + WALL_CELL_W, y + WALL_CELL_H, hovered ? 0x8C33405E : 0x66141A2B);
            graphics.fill(x, y, x + WALL_CELL_W, y + 1, 0x55FFFFFF);
            graphics.renderOutline(x, y, WALL_CELL_W, WALL_CELL_H,
                    selected ? 0xFFF2E27A : (hovered ? 0xD8FFFFFF : 0x77FFFFFF));
            graphics.drawString(this.font, (selected ? "✔ " : "") + style.displayName, x + 4, y + 5,
                    locked ? 0x8C8C8C : 0xF2EEE4, false);
            if (locked) {
                graphics.drawString(this.font, Component.translatable("gui.sleep_well.quilt_locked"),
                        x + 4, y + 18, 0x8C8C8C, false);
            }
        }
        graphics.drawString(this.font, Component.translatable("gui.sleep_well.quilt_title"),
                wallLeft(), wallTop() - 12, 0xF2EEE4, false);
        RenderSystem.disableBlend();
    }

    /** Returns true when the overlay swallowed the click. */
    private boolean quiltWallClick(double mouseX, double mouseY) {
        if (!this.quiltOpen) {
            return false;
        }
        List<QuiltStyle> styles = QuiltStyle.all();
        for (int i = 0; i < styles.size(); i++) {
            QuiltStyle style = styles.get(i);
            int x = wallCellX(i);
            int y = wallCellY(i);
            if (mouseX >= x && mouseX < x + WALL_CELL_W && mouseY >= y && mouseY < y + WALL_CELL_H) {
                boolean locked = this.menu.quiltLocked() && !style.isDefault();
                if (!locked) {
                    this.chosenQuilt = style.id;
                    SleepWellNetwork.CHANNEL.sendToServer(
                            new SaveQuiltStylePacket(this.menu.entityId(), style.id));
                }
                this.closeQuiltWall();
                return true;
            }
        }
        this.closeQuiltWall();
        return true;
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
    }

    private void closePicker() {
        if (this.picker == null) {
            return;
        }
        this.picker.setOpen(false);
        this.toggles.forEach(button -> button.visible = true);
        this.gear.visible = true;
    }

    private boolean pickerOpen() {
        return this.picker != null && this.picker.isOpen();
    }

    /**
     * While the overlay is up it owns the mouse: clicks never reach the page or the maid inventory, which is
     * what kept the boxes from ever being ticked before.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (quiltWallClick(mouseX, mouseY)) {
            return true;
        }
        if (pickerOpen()) {
            this.picker.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (pickerOpen()) {
            this.picker.mouseScrolled(delta);
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (pickerOpen() && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            closePicker();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
        if (this.quiltOpen) {
            renderQuiltWall(graphics, mouseX, mouseY);
        }
        if (pickerOpen()) {
            // Covers the whole maid window, so the page's own black margins are hidden behind it too.
            this.picker.render(graphics, this.font, this.leftPos, this.topPos,
                    this.imageWidth, this.imageHeight, mouseX, mouseY);
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

    /**
     * The quilt icon on the lie-in row. Placeholder art: a 2x2 gingham patch drawn with rectangles, so the
     * page needs no texture until real quilt art exists.
     */
    private static final class QuiltIconButton extends SoftButton {
        private QuiltIconButton(int x, int y, int width, int height, OnPress onPress) {
            super(x, y, width, height, Component.empty(), onPress);
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
            graphics.renderOutline(x, y, w, h, hovered ? 0xD8FFFFFF : 0x77FFFFFF);
            int cell = 5;
            int px = x + (w - cell * 2) / 2;
            int py = y + (h - cell * 2) / 2;
            for (int cx = 0; cx < 2; cx++) {
                for (int cy = 0; cy < 2; cy++) {
                    graphics.fill(px + cx * cell, py + cy * cell, px + cx * cell + cell,
                            py + cy * cell + cell, (cx + cy) % 2 == 0 ? 0xFF7A5C8F : 0xFFE8DDF0);
                }
            }
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
