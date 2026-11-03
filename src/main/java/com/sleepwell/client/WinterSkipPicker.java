package com.sleepwell.client;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.sleepwell.winter.SkipTaskSelection;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The gear overlay: a two column, icon bearing checkbox list of every work task the maid can be put on.
 * <p>
 * It is not a widget on purpose. It covers the whole maid window, draws last, and swallows every click while
 * open: the page's own switches and the maid inventory are sheltered behind it.
 */
final class WinterSkipPicker {
    private static final int ROW_H = 17;
    private static final int TITLE_H = 14;
    private static final int FOOTER_H = 22;
    private static final int PAD = 5;
    private static final int COL_GAP = 4;
    private static final int SCROLL_W = 4;
    private static final int CLOSE_W = 48;
    private static final int CLOSE_H = 14;
    private static final int TEXT = 0xF2EEE4;
    private static final int TEXT_DIM = 0xC9C4B8;
    private static final int HOVER = 0x3AFFFFFF;
    private static final int BOX_LINE = 0xCCF2EEE4;
    private static final int BOX_ON = 0xFF9BE37A;

    private final List<Row> rows = new ArrayList<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private final Consumer<List<String>> onChange;
    private final Runnable onClose;

    private boolean open;
    private int scroll;
    private boolean draggingThumb;
    // Geometry of the last render, so clicks and the wheel hit the same boxes.
    private int boxX;
    private int boxY;
    private int boxW;
    private int boxH;

    WinterSkipPicker(List<IMaidTask> tasks, Collection<? extends String> configured,
                     Runnable onClose, Consumer<List<String>> onChange) {
        this.onChange = onChange;
        this.onClose = onClose;
        rebuild(tasks, configured);
    }

    boolean isOpen() {
        return open;
    }

    void setOpen(boolean open) {
        this.open = open;
        this.draggingThumb = false;
        if (!open) {
            this.scroll = 0;
        }
    }

    /**
     * Tasks come from the page, which already asked TLM for the ones this maid can actually see, so addon
     * tasks (Maidsoul Kitchen and friends) are in the list and hidden compat tasks are not.
     */
    private void rebuild(List<IMaidTask> tasks, Collection<? extends String> configured) {
        selected.clear();
        for (String entry : SkipTaskSelection.normalize(configured)) {
            selected.add(SkipTaskSelection.bare(entry));
        }
        Map<String, Row> found = new LinkedHashMap<>();
        String idle = TaskManager.getIdleTask().getUid().toString();
        for (IMaidTask task : tasks) {
            String uid = task.getUid().toString();
            if (uid.equals(idle)) {
                continue;
            }
            found.putIfAbsent(SkipTaskSelection.bare(uid), new Row(SkipTaskSelection.bare(uid), task.getName(), task.getIcon()));
        }
        // Ids already in the config that the registry does not know stay listed, so they can be removed.
        for (String bare : selected) {
            found.putIfAbsent(bare, new Row(bare, Component.literal(bare), ItemStack.EMPTY));
        }
        rows.clear();
        rows.addAll(found.values());
        rows.sort(Comparator.comparing(row -> row.name().getString()));
    }

    private int colWidth() {
        return (boxW - 2 * PAD - SCROLL_W - COL_GAP) / 2;
    }

    private int visibleRows() {
        return Math.max(1, (boxH - TITLE_H - FOOTER_H) / ROW_H);
    }

    private int totalRows() {
        return (rows.size() + 1) / 2;
    }

    private int maxScroll() {
        return Math.max(0, totalRows() - visibleRows());
    }

    private int rowsTop() {
        return boxY + TITLE_H;
    }

    private int rowsHeight() {
        return boxH - TITLE_H - FOOTER_H;
    }

    private int closeX() {
        return boxX + boxW - PAD - CLOSE_W;
    }

    private int closeY() {
        return boxY + boxH - FOOTER_H + 4;
    }

    void render(GuiGraphics graphics, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        boxX = x;
        boxY = y;
        boxW = w;
        boxH = h;
        scroll = Mth.clamp(scroll, 0, maxScroll());

        graphics.fill(x, y, x + w, y + h, 0xF0141A2B);
        graphics.renderOutline(x, y, w, h, 0x99FFFFFF);

        Component title = Component.translatable("gui.sleep_well.skip_tasks");
        graphics.drawString(font, title, x + PAD, y + 3, TEXT, false);
        Component count = Component.translatable("gui.sleep_well.skip_count", selected.size(), rows.size());
        int countWidth = font.width(count);
        graphics.drawString(font, count, x + w - PAD - countWidth, y + 3, TEXT_DIM, false);
        graphics.fill(x + PAD, y + TITLE_H - 2, x + w - PAD, y + TITLE_H - 1, 0x55FFFFFF);

        int labelWidth = colWidth() - 32;
        int visible = visibleRows();
        for (int r = 0; r < visible; r++) {
            int rowIndex = scroll + r;
            for (int column = 0; column < 2; column++) {
                int index = rowIndex * 2 + column;
                if (index >= rows.size()) {
                    break;
                }
                Row row = rows.get(index);
                int cellX = x + PAD + column * (colWidth() + COL_GAP);
                int cellY = rowsTop() + r * ROW_H;
                boolean hovered = mouseX >= cellX && mouseX < cellX + colWidth()
                        && mouseY >= cellY && mouseY < cellY + ROW_H;
                if (hovered) {
                    graphics.fill(cellX, cellY, cellX + colWidth(), cellY + ROW_H - 1, HOVER);
                }
                boolean on = selected.contains(row.bare());
                int boxTop = cellY + 4;
                graphics.fill(cellX + 1, boxTop, cellX + 9, boxTop + 8, 0x66000000);
                graphics.renderOutline(cellX + 1, boxTop, 8, 8, BOX_LINE);
                if (on) {
                    graphics.fill(cellX + 3, boxTop + 2, cellX + 7, boxTop + 6, BOX_ON);
                }
                if (!row.icon().isEmpty()) {
                    graphics.renderItem(row.icon(), cellX + 12, cellY);
                }
                String full = row.name().getString();
                String label = font.plainSubstrByWidth(full, Math.max(8, labelWidth));
                if (label.length() < full.length() && labelWidth > 12) {
                    label = font.plainSubstrByWidth(full, labelWidth - 6) + "…";
                }
                graphics.drawString(font, label, cellX + 31, cellY + 4, on ? TEXT : TEXT_DIM, false);
            }
        }

        if (rows.size() > visible * 2) {
            int trackX = x + w - PAD - SCROLL_W + 1;
            int trackTop = rowsTop();
            int trackHeight = rowsHeight();
            graphics.fill(trackX, trackTop, trackX + 2, trackTop + trackHeight, 0x66141A2B);
            int thumbH = Math.max(10, trackHeight * visible / Math.max(1, totalRows()));
            int thumbY = trackTop + (trackHeight - thumbH) * scroll / Math.max(1, maxScroll());
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xD8F2EEE4);
        }

        boolean closeHovered = mouseX >= closeX() && mouseX < closeX() + CLOSE_W
                && mouseY >= closeY() && mouseY < closeY() + CLOSE_H;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.fill(closeX(), closeY(), closeX() + CLOSE_W, closeY() + CLOSE_H,
                closeHovered ? 0x8C4A6B9E : 0x6633405E);
        graphics.renderOutline(closeX(), closeY(), CLOSE_W, CLOSE_H, closeHovered ? 0xD8FFFFFF : 0x77FFFFFF);
        graphics.drawCenteredString(font, Component.translatable("gui.sleep_well.done"),
                closeX() + CLOSE_W / 2, closeY() + 3, TEXT);
        RenderSystem.disableBlend();
    }

    /** Modal: whatever lands here is consumed, so nothing behind the overlay reacts. */
    void mouseClicked(double mouseX, double mouseY, int button) {
        if (!open || button != 0) {
            return;
        }
        if (mouseX >= closeX() && mouseX < closeX() + CLOSE_W
                && mouseY >= closeY() && mouseY < closeY() + CLOSE_H) {
            onClose.run();
            return;
        }
        if (mouseX >= boxX + boxW - PAD - SCROLL_W && mouseY >= rowsTop() && mouseY < rowsTop() + rowsHeight()) {
            draggingThumb = true;
            scrollToMouse(mouseY);
            return;
        }
        if (mouseY >= rowsTop() && mouseY < rowsTop() + rowsHeight()
                && mouseX >= boxX + PAD && mouseX < boxX + boxW - PAD - SCROLL_W) {
            int rowIndex = scroll + (int) ((mouseY - rowsTop()) / ROW_H);
            int column = (int) ((mouseX - boxX - PAD) / (colWidth() + COL_GAP));
            if (column >= 0 && column < 2) {
                int index = rowIndex * 2 + column;
                if (index >= 0 && index < rows.size() && withinColumn(mouseX, column)) {
                    toggle(rows.get(index).bare());
                }
            }
        }
    }

    private boolean withinColumn(double mouseX, int column) {
        int cellX = boxX + PAD + column * (colWidth() + COL_GAP);
        return mouseX >= cellX && mouseX < cellX + colWidth();
    }

    void mouseDragged(double mouseY) {
        if (open && draggingThumb) {
            scrollToMouse(mouseY);
        }
    }

    void mouseReleased() {
        draggingThumb = false;
    }

    boolean mouseScrolled(double delta) {
        if (!open) {
            return false;
        }
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, maxScroll());
        return true;
    }

    private void scrollToMouse(double mouseY) {
        int trackHeight = rowsHeight();
        int thumbH = Math.max(10, trackHeight * visibleRows() / Math.max(1, totalRows()));
        double ratio = Mth.clamp((mouseY - rowsTop() - thumbH / 2.0) / Math.max(1, trackHeight - thumbH), 0, 1);
        scroll = (int) Math.round(ratio * maxScroll());
    }

    private void toggle(String bare) {
        if (!selected.remove(bare)) {
            selected.add(bare);
        }
        onChange.accept(SkipTaskSelection.normalize(selected));
    }

    private record Row(String bare, Component name, ItemStack icon) {
    }
}
