package com.sleepwell.client;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.sleepwell.winter.SkipTaskSelection;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

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
 * The gear overlay on the winter row: a scrolling checkbox list of every work task in the game.
 * <p>
 * It is not a widget on purpose. It draws on top of the page, swallows clicks while open, and reports the
 * new list through {@code onChange} so the page can write it to the config right away (same behaviour as
 * the switches above it: no save button).
 */
final class WinterSkipPicker {
    private static final int ROW_H = 12;
    private static final int TITLE_H = 14;
    private static final int FOOTER_H = 24;
    private static final int SCROLL_W = 5;
    private static final int TEXT = 0xF2EEE4;
    private static final int TEXT_DIM = 0xC9C4B8;
    private static final int HOVER = 0x33FFFFFF;
    private static final int BOX_LINE = 0xCCF2EEE4;
    private static final int BOX_ON = 0xFF9BE37A;

    private final List<Row> rows = new ArrayList<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private final Consumer<List<String>> onChange;
    private final EntityMaid maid;

    private boolean open;
    private int scroll;
    private boolean draggingThumb;
    // Hit boxes measured by the last render, so clicks and wheel use the same geometry.
    private int boxX;
    private int boxY;
    private int boxW;
    private int boxH;

    WinterSkipPicker(Collection<? extends String> configured, EntityMaid maid, Consumer<List<String>> onChange) {
        this.onChange = onChange;
        this.maid = maid;
        rebuild(configured);
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

    /** Tasks come from the registry, so addon tasks (Maidsoul Kitchen and friends) show up too. */
    private void rebuild(Collection<? extends String> configured) {
        selected.clear();
        for (String entry : SkipTaskSelection.normalize(configured)) {
            selected.add(SkipTaskSelection.bare(entry));
        }
        Map<String, Component> found = new LinkedHashMap<>();
        String idle = TaskManager.getIdleTask().getUid().toString();
        // Same list the maid's own task button shows, so hidden compat tasks stay out of the way.
        List<IMaidTask> tasks = maid != null ? TaskManager.getNotHiddenTaskList(maid) : TaskManager.getTaskIndex();
        for (IMaidTask task : tasks) {
            String uid = task.getUid().toString();
            if (uid.equals(idle)) {
                continue;
            }
            found.putIfAbsent(SkipTaskSelection.bare(uid), task.getName());
        }
        // Ids already in the config that the registry does not know stay listed, so they can be removed.
        for (String bare : selected) {
            found.putIfAbsent(bare, Component.literal(bare));
        }
        rows.clear();
        found.forEach((bare, name) -> rows.add(new Row(bare, name)));
        rows.sort(Comparator.comparing(row -> row.name.getString()));
    }

    private int visibleRows() {
        return Math.max(1, (boxH - TITLE_H - FOOTER_H) / ROW_H);
    }

    private int maxScroll() {
        return Math.max(0, rows.size() - visibleRows());
    }

    void render(GuiGraphics graphics, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        boxX = x;
        boxY = y;
        boxW = w;
        boxH = h;
        scroll = Mth.clamp(scroll, 0, maxScroll());

        graphics.fill(x, y, x + w, y + h, 0xE6141A2B);
        graphics.renderOutline(x, y, w, h, 0x77FFFFFF);
        graphics.drawString(font, Component.translatable("gui.sleep_well.skip_tasks"), x + 5, y + 3, TEXT, false);

        int rowsTop = y + TITLE_H;
        int rowsHeight = h - TITLE_H - FOOTER_H;
        int labelWidth = w - SCROLL_W - 20;
        int visible = visibleRows();
        if (rows.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.sleep_well.no_tasks"),
                    x + 5, rowsTop + 2, TEXT_DIM, false);
        }
        for (int i = 0; i < visible; i++) {
            int index = scroll + i;
            if (index >= rows.size()) {
                break;
            }
            Row row = rows.get(index);
            int rowY = rowsTop + i * ROW_H;
            boolean hovered = mouseX >= x + 4 && mouseX < x + w - SCROLL_W - 4
                    && mouseY >= rowY && mouseY < rowY + ROW_H;
            if (hovered) {
                graphics.fill(x + 4, rowY, x + w - SCROLL_W - 4, rowY + ROW_H, HOVER);
            }
            boolean on = selected.contains(row.bare());
            int boxTop = rowY + 2;
            graphics.fill(x + 5, boxTop, x + 13, boxTop + 8, 0x55000000);
            graphics.renderOutline(x + 5, boxTop, 8, 8, BOX_LINE);
            if (on) {
                graphics.fill(x + 7, boxTop + 2, x + 11, boxTop + 6, BOX_ON);
            }
            String label = font.plainSubstrByWidth(row.name().getString(), labelWidth);
            graphics.drawString(font, label, x + 17, rowY + 2, on ? TEXT : TEXT_DIM, false);
        }
        if (rows.size() > visible) {
            int trackX = x + w - SCROLL_W - 2;
            graphics.fill(trackX, rowsTop, trackX + 3, rowsTop + rowsHeight, 0x66141A2B);
            int thumbH = Math.max(10, rowsHeight * visible / rows.size());
            int thumbY = rowsTop + (rowsHeight - thumbH) * scroll / Math.max(1, maxScroll());
            graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xD8F2EEE4);
        }
    }

    /** True when the click landed on the overlay; the page stops passing it on in that case. */
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open || button != 0) {
            return false;
        }
        if (mouseX < boxX || mouseX >= boxX + boxW || mouseY < boxY || mouseY >= boxY + boxH) {
            return false;
        }
        int rowsTop = boxY + TITLE_H;
        int rowsHeight = boxH - TITLE_H - FOOTER_H;
        if (mouseX >= boxX + boxW - SCROLL_W - 4 && mouseY >= rowsTop && mouseY < rowsTop + rowsHeight) {
            draggingThumb = true;
            scrollToMouse(mouseY);
            return true;
        }
        if (mouseY >= rowsTop && mouseY < rowsTop + rowsHeight) {
            int index = scroll + (int) ((mouseY - rowsTop) / ROW_H);
            if (index >= 0 && index < rows.size()) {
                toggle(rows.get(index).bare());
            }
            return true;
        }
        return true;
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
        int rowsTop = boxY + TITLE_H;
        int rowsHeight = boxH - TITLE_H - FOOTER_H;
        int thumbH = Math.max(10, rowsHeight * visibleRows() / Math.max(1, rows.size()));
        double ratio = Mth.clamp((mouseY - rowsTop - thumbH / 2.0) / Math.max(1, rowsHeight - thumbH), 0, 1);
        scroll = (int) Math.round(ratio * maxScroll());
    }

    private void toggle(String bare) {
        if (!selected.remove(bare)) {
            selected.add(bare);
        }
        onChange.accept(SkipTaskSelection.normalize(selected));
    }

    private record Row(String bare, Component name) {
    }
}
