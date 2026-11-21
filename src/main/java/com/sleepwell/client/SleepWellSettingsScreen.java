package com.sleepwell.client;

import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.AbstractMaidContainerGui;
import com.sleepwell.SleepWellMod;
import com.sleepwell.config.SleepWellConfig;
import com.sleepwell.gui.SleepWellContainer;
import com.sleepwell.network.SaveSettingPacket;
import com.sleepwell.client.quilt.QuiltBorder;
import com.sleepwell.client.quilt.QuiltStyle;
import com.sleepwell.client.quilt.QuiltTextures;
import com.sleepwell.network.SaveQuiltBorderPacket;
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
    /** How far the gear and quilt icon float inside the row's right edge, so the seam reads as a gap. */
    private static final int ICON_INSET = 4;
    /** Deep warm brown, because the sunny backdrop is far too bright for white text. */
    private static final int TITLE_COLOR = 0x4A3324;
    /** PixelLab art: a cosy sunlit bed, used as the page backdrop behind the translucent switches. */
    private static final ResourceLocation BACKGROUND =
            new ResourceLocation(SleepWellMod.MOD_ID, "textures/gui/sleep_well_bg.png");

    private final boolean[] state = new boolean[4];
    /** Serene Seasons is absent, so the winter switch is parked; the note is drawn under the rows. */
    private boolean sereneMissing;
    private final List<Button> toggles = new ArrayList<>();
    private Button gear;
    private Button quiltIcon;
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
        // The switches span the whole row: the gear and the quilt icon float on top of the right end instead of
        // carving a cell out of the button, which used to leave a visible seam in the middle of the row.
        Button winter = addToggle(2, this.menu.winterSkip(), "gui.sleep_well.winter_skip",
                FIRST_ROW_Y + ROW_STEP * 2, ROW_W);
        if (!this.menu.sereneSeasons()) {
            // Grey it out and put the reason on one quiet line under the switches: both replacing the label and
            // a hover tooltip ended up covering the row below.
            winter.active = false;
            this.sereneMissing = true;
        }
        addToggle(3, this.menu.lieIn(), "gui.sleep_well.lie_in", FIRST_ROW_Y + ROW_STEP * 3, ROW_W);

        this.picker = new WinterSkipPicker(this.notHiddenTasks, SleepWellConfig.WINTER_SKIP_TASKS.get(),
                this::closePicker, this::saveSkipTasks);
        this.gear = this.addRenderableWidget(new GearButton(this.leftPos + ROW_X + ROW_W - GEAR_W - ICON_INSET,
                this.topPos + FIRST_ROW_Y + ROW_STEP * 2, GEAR_W, ROW_H, b -> openPicker()));
        this.gear.active = this.menu.sereneSeasons();
        this.quiltIcon = this.addRenderableWidget(new QuiltIconButton(
                this.leftPos + ROW_X + ROW_W - QUILT_ICON_W - ICON_INSET,
                this.topPos + FIRST_ROW_Y + ROW_STEP * 3, QUILT_ICON_W, ROW_H, b -> openQuiltWall()));
    }

    /**
     * The quilt wall, as an overlay on this page instead of a page of its own: the quilt icon on the right of
     * the lie-in row opens it, and one plate per quilt is laid out three to a row inside it.
     */
    private static final int WALL_COLS = 2;
    /** Wide enough for a thumbnail beside the name, so the wall spans the whole 176 px panel. */
    private static final int WALL_CELL_W = 60;
    /** Two lines of text no longer fit: the plate shows the name only, the note moved to a hover hint. */
    private static final int WALL_CELL_H = 26;
    private static final int WALL_GAP = 4;
    /** Edge of the quilt thumbnail inside a plate; the name sits to its right. */
    private static final int THUMB = 16;
    /** The border column is narrower than a quilt plate: names only. */
    private static final int BORDER_CELL_W = 44;
    /** The border column, right of the divider: five plates, tighter than the quilts. */
    private static final int BORDER_CELL_H = 22;
    private static final int BORDER_GAP = 2;
    /** Space between the quilt grid and the divider, then the border column itself. */
    private static final int WALL_SPLIT = 8;
    /** Four rows is what the 136 px panel holds; more quilts than that get a second page. */
    private static final int WALL_ROWS = 4;
    private static final int WALL_PER_PAGE = WALL_COLS * WALL_ROWS;
    /** Where the "<" page arrow, the "1/2" counter and the ">" arrow sit in the title row. */
    private static final int PAGE_ARROW_Y = -12;
    private static final int PAGE_PREV_X = 92;
    private static final int PAGE_COUNT_X = 102;
    private static final int PAGE_NEXT_X = 124;
    /** Where the icon sits: right edge of the lie-in row, mirroring the gear on the winter row. */
    private static final int QUILT_ICON_W = 20;
    /** Size the folded-quilt icon occupies on the row: 18 px inside the 20 px row, so the plate's hairlines stay whole. */
    private static final int ICON_SIZE = 18;
    /** Native edge of the icon texture: the 512 px original ships untouched, no downsampled copy. */
    private static final int ICON_SOURCE = 512;
    private static final ResourceLocation QUILT_ICON =
            new ResourceLocation(SleepWellMod.MOD_ID, "textures/gui/quilt_icon.png");

    private boolean quiltOpen;
    /** Which sheet of the wall is showing; 8 plates fit one page. */
    private int quiltPage;
    private String chosenQuilt;
    private String chosenBorder;

    /** The border the server currently has for this maid, as far as the client mirror knows. */
    private String currentBorder() {
        if (this.chosenBorder != null) {
            return this.chosenBorder;
        }
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        return ClientQuiltStyles.borderOf(level == null ? null : level.getEntity(this.menu.entityId()));
    }

    private void openQuiltWall() {
        this.quiltOpen = true;
        // Open on the sheet that holds her current quilt: scrolling past a page to find it is friction.
        java.util.List<QuiltStyle> all = QuiltStyle.all();
        String current = currentQuiltId();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(current)) {
                this.quiltPage = i / WALL_PER_PAGE;
                break;
            }
        }
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

    /** The wall covers the page panel only: it used to start at the window edge and buried the inventory. */
    private int wallLeft() {
        return this.leftPos + PANEL_X;
    }

    private int wallTop() {
        return this.topPos + 44;
    }

    /** One page holds {@link #WALL_PER_PAGE} plates; everything below works in page-local slots. */
    private int wallPages() {
        // The wall holds the built-in faces plus one plate that opens the custom-quilt editor.
        return Math.max(1, (QuiltStyle.all().size() + 1 + WALL_PER_PAGE - 1) / WALL_PER_PAGE);
    }

    private int wallSlot(int index) {
        return index - this.quiltPage * WALL_PER_PAGE;
    }

    private int wallCellX(int index) {
        int slot = wallSlot(index);
        return wallLeft() + (slot % WALL_COLS) * (WALL_CELL_W + WALL_GAP);
    }

    private int wallCellY(int index) {
        int slot = wallSlot(index);
        return wallTop() + (slot / WALL_COLS) * (WALL_CELL_H + WALL_GAP);
    }

    /** The plates drawn this page: a slice of the registry, not the whole wall. */
    private int pageStart() {
        return this.quiltPage * WALL_PER_PAGE;
    }

    private int pageEnd(int size) {
        return Math.min(size, pageStart() + WALL_PER_PAGE);
    }

    /** True when the mouse is over one of the page arrows in the title row. */
    private boolean overPageArrow(double mouseX, double mouseY, int arrowX) {
        int x = wallLeft() + arrowX;
        int y = wallTop() + PAGE_ARROW_Y;
        return mouseX >= x - 1 && mouseX < x + 8 && mouseY >= y - 1 && mouseY < y + 9;
    }

    /** Draws the overlay: dimmed page, then one plate per quilt with its state. */
    private void renderQuiltWall(GuiGraphics graphics, int mouseX, int mouseY) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // Dim the page behind the plates: without it the sunny bed art shows through and everything reads as mush.
        graphics.fill(this.leftPos + PANEL_X, this.topPos + PANEL_Y,
                this.leftPos + PANEL_X + PANEL_W, this.topPos + PANEL_Y + PANEL_H, 0xE6141A2B);
        List<QuiltStyle> styles = QuiltStyle.all();
        String current = this.chosenQuilt != null ? this.chosenQuilt : this.menu.quiltStyle();
        String currentBorder = currentBorder();
        for (int i = pageStart(); i < pageEnd(styles.size() + 1); i++) {
            boolean customCell = i >= styles.size();
            QuiltStyle style = customCell ? null : styles.get(i);
            boolean locked = this.menu.quiltLocked() && (customCell || !style.isDefault());
            boolean selected = customCell ? current.startsWith("custom:") : style.id.equals(current);
            int x = wallCellX(i);
            int y = wallCellY(i);
            boolean hovered = !locked && mouseX >= x && mouseX < x + WALL_CELL_W
                    && mouseY >= y && mouseY < y + WALL_CELL_H;
            graphics.fill(x, y, x + WALL_CELL_W, y + WALL_CELL_H, hovered ? 0x8C33405E : 0x66141A2B);
            graphics.fill(x, y, x + WALL_CELL_W, y + 1, 0x55FFFFFF);
            graphics.renderOutline(x, y, WALL_CELL_W, WALL_CELL_H,
                    selected ? 0xFFF2E27A : (hovered ? 0xD8FFFFFF : 0x77FFFFFF));
            if (customCell) {
                // The last plate is not a quilt: it opens the editor where a player brings their own picture.
                graphics.drawString(this.font, (selected ? "✔" : "") + "自定义…", x + 5, y + 9,
                        locked ? 0x8C8C8C : 0xF2EEE4, false);
            } else {
                // The face itself as the plate's art: a player picks by look, not by reading names.
                int[] size = QuiltTextures.dimensions(style, currentBorder);
                blitScaled(graphics, QuiltTextures.texture(style, currentBorder), x + 4,
                        y + (WALL_CELL_H - THUMB) / 2, THUMB, size[0], size[1]);
                graphics.drawString(this.font, (selected ? "✔" : "") + wallName(style.displayName),
                        x + THUMB + 7, y + 9, locked ? 0x8C8C8C : 0xF2EEE4, false);
            }
        }
        graphics.drawString(this.font, Component.translatable("gui.sleep_well.quilt_title"),
                wallLeft(), wallTop() + PAGE_ARROW_Y, TITLE_COLOR, false);
        int pages = wallPages();
        if (pages > 1) {
            // Two arrows and a counter: the panel holds four rows, the wall now holds more than that.
            boolean prevHot = this.quiltPage > 0 && overPageArrow(mouseX, mouseY, PAGE_PREV_X);
            boolean nextHot = this.quiltPage < pages - 1 && overPageArrow(mouseX, mouseY, PAGE_NEXT_X);
            graphics.drawString(this.font, "<", wallLeft() + PAGE_PREV_X, wallTop() + PAGE_ARROW_Y,
                    this.quiltPage > 0 ? (prevHot ? 0xFFFFFFFF : 0xCCF2EEE4) : 0x55FFFFFF, false);
            graphics.drawString(this.font, (this.quiltPage + 1) + "/" + pages,
                    wallLeft() + PAGE_COUNT_X, wallTop() + PAGE_ARROW_Y, 0xCCF2EEE4, false);
            graphics.drawString(this.font, ">", wallLeft() + PAGE_NEXT_X, wallTop() + PAGE_ARROW_Y,
                    this.quiltPage < pages - 1 ? (nextHot ? 0xFFFFFFFF : 0xCCF2EEE4) : 0x55FFFFFF, false);
        }

        // The divider is what tells a player the wall has two halves now: faces left, borders right.
        graphics.fill(borderX() - WALL_SPLIT + 3, wallTop() - 4,
                borderX() - WALL_SPLIT + 4, wallTop() + 118, 0x66F2E27A);
        graphics.drawString(this.font, Component.translatable("gui.sleep_well.border_title"),
                borderX(), wallTop() - 12, TITLE_COLOR, false);

        List<QuiltBorder> borders = QuiltBorder.all();
        for (int i = 0; i < borders.size(); i++) {
            QuiltBorder border = borders.get(i);
            boolean locked = this.menu.quiltLocked() && !border.isNone();
            boolean selected = border.id.equals(currentBorder);
            int x = borderX();
            int y = borderCellY(i);
            boolean hovered = !locked && mouseX >= x && mouseX < x + BORDER_CELL_W
                    && mouseY >= y && mouseY < y + BORDER_CELL_H;
            graphics.fill(x, y, x + BORDER_CELL_W, y + BORDER_CELL_H, hovered ? 0x8C33405E : 0x66141A2B);
            graphics.fill(x, y, x + BORDER_CELL_W, y + 1, 0x55FFFFFF);
            graphics.renderOutline(x, y, BORDER_CELL_W, BORDER_CELL_H,
                    selected ? 0xFFF2E27A : (hovered ? 0xD8FFFFFF : 0x77FFFFFF));
            if (!border.isNone()) {
                blitCrop(graphics, border.texture(), x + 3, y + 3, 0, QuiltBorder.BAND, 16);
            }
            graphics.drawString(this.font, (selected ? "✔" : "") + border.displayName,
                    x + 21, y + 7, locked ? 0x8C8C8C : 0xF2EEE4, false);
        }
        RenderSystem.disableBlend();
    }

    /**
     * Draws a whole texture into a square of the given size, keeping its aspect ratio: the faces are tall, and
     * squashing one into a square icon makes every sunflower look wrong.
     */
    private static void blitScaled(GuiGraphics graphics, ResourceLocation texture, int x, int y, int size,
                                   int sourceWidth, int sourceHeight) {
        float scale = size / (float) sourceHeight;
        float drawnWidth = sourceWidth * scale;
        com.mojang.blaze3d.vertex.PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + (size - drawnWidth) * 0.5F, y, 0.0F);
        pose.scale(scale, scale, 1.0F);
        graphics.blit(texture, 0, 0, 0, 0, sourceWidth, sourceHeight, sourceWidth, sourceHeight);
        pose.popPose();
    }

    /** Crops a small square out of a texture untouched, for the border swatches. */
    private static void blitCrop(GuiGraphics graphics, ResourceLocation texture, int x, int y,
                                 int u, int v, int size) {
        graphics.blit(texture, x, y, u, v, size, size, 220, 284);
    }

    /** The quilt the wall is showing: the one just clicked, else the one the server reports. */
    private String currentQuiltId() {
        return this.chosenQuilt != null ? this.chosenQuilt : this.menu.quiltStyle();
    }

    /** The left edge of the border column, right of the quilt grid and the divider. */
    private int borderX() {
        return wallLeft() + WALL_COLS * WALL_CELL_W + WALL_GAP * (WALL_COLS - 1) + WALL_SPLIT;
    }

    /** Four characters is what fits beside a thumbnail; the full name is only one character longer. */
    private static String wallName(String name) {
        return name.length() <= 4 ? name : name.substring(0, 4);
    }

    private int borderCellY(int index) {
        return wallTop() + index * (BORDER_CELL_H + BORDER_GAP);
    }

    /** Returns true when the overlay swallowed the click. */
    private boolean quiltWallClick(double mouseX, double mouseY) {
        if (!this.quiltOpen) {
            return false;
        }
        int pages = wallPages();
        if (pages > 1) {
            if (overPageArrow(mouseX, mouseY, PAGE_PREV_X) && this.quiltPage > 0) {
                this.quiltPage--;
                return true;
            }
            if (overPageArrow(mouseX, mouseY, PAGE_NEXT_X) && this.quiltPage < pages - 1) {
                this.quiltPage++;
                return true;
            }
        }
        List<QuiltStyle> styles = QuiltStyle.all();
        for (int i = pageStart(); i < pageEnd(styles.size() + 1); i++) {
            int x = wallCellX(i);
            int y = wallCellY(i);
            if (mouseX >= x && mouseX < x + WALL_CELL_W && mouseY >= y && mouseY < y + WALL_CELL_H) {
                if (i >= styles.size()) {
                    openCustomEditor();
                    this.closeQuiltWall();
                    return true;
                }
                QuiltStyle style = styles.get(i);
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
        // Borders keep the wall open: a player usually tries two or three before settling.
        List<QuiltBorder> borders = QuiltBorder.all();
        for (int i = 0; i < borders.size(); i++) {
            QuiltBorder border = borders.get(i);
            int x = borderX();
            int y = borderCellY(i);
            if (mouseX >= x && mouseX < x + BORDER_CELL_W && mouseY >= y && mouseY < y + BORDER_CELL_H) {
                boolean locked = this.menu.quiltLocked() && !border.isNone();
                if (!locked) {
                    this.chosenBorder = border.id;
                    SleepWellNetwork.CHANNEL.sendToServer(
                            new SaveQuiltBorderPacket(this.menu.entityId(), border.id));
                }
                return true;
            }
        }
        this.closeQuiltWall();
        return true;
    }
    /** Opens the editor with whatever this maid wears now, so the frame starts where the player left it. */
    private void openCustomEditor() {
        net.minecraft.world.entity.Entity entity = this.minecraft.level == null ? null
                : this.minecraft.level.getEntity(this.menu.entityId());
        String current = this.chosenQuilt != null ? this.chosenQuilt : this.menu.quiltStyle();
        net.minecraft.client.Minecraft.getInstance().setScreen(new CustomQuiltScreen(
                this.menu.entityId(), current, ClientQuiltStyles.paramsOf(entity)));
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
        // The switches span the whole row, so their bounds cover the gear and the quilt icon. Hand the icons the
        // click first; otherwise the switch under them wins and the buttons look dead.
        if (this.gear != null && this.gear.visible && this.gear.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (this.quiltIcon != null && this.quiltIcon.visible
                && this.quiltIcon.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
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
        if (!this.quiltOpen) {
            graphics.drawString(this.font, Component.translatable("gui.sleep_well.title"),
                    ROW_X, TITLE_Y, TITLE_COLOR, false);
        }
        if (this.sereneMissing && !this.quiltOpen) {
            graphics.drawString(this.font, Component.translatable("gui.sleep_well.no_serene_seasons"),
                    ROW_X, FIRST_ROW_Y + ROW_STEP * 4 - 2, 0x8C6A4A, false);
        }
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
     * The quilt icon on the lie-in row: the 512 px folded-quilt art, sampled whole and scaled down to the slot
     * by the pose rather than by a pre-baked 16 px copy.
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
            // No plate of its own: the row's plate is already there, and a second outline reads as a broken button.
            if (hovered) {
                graphics.fill(x, y + 2, x + w, y + h - 2, 0x55FFFFFF);
            }
            // The folded quilt itself, straight from the original sheet: the pose shrinks all 512 px into the
            // slot, so the resource pack carries the full-resolution art instead of a downsampled 16 px copy.
            int size = ICON_SIZE;
            float scale = size / (float) ICON_SOURCE;
            com.mojang.blaze3d.vertex.PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(x + (w - size) / 2, y + (h - size) / 2, 0.0F);
            pose.scale(scale, scale, 1.0F);
            graphics.blit(QUILT_ICON, 0, 0, 0, 0, ICON_SOURCE, ICON_SOURCE, ICON_SOURCE, ICON_SOURCE);
            pose.popPose();
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
            // Same as the quilt icon: the row plate carries the box, the gear only draws itself.
            if (hovered) {
                graphics.fill(x, y + 2, x + w, y + h - 2, 0x55FFFFFF);
            }
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
