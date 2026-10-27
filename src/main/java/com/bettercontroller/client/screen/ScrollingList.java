package com.bettercontroller.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Minimal vertical list with mouse-wheel scrolling, clamped at both ends. Hit testing uses the
 * same scroll offset as rendering, so a click always selects the row that is drawn under the cursor.
 */
final class ScrollingList {
    static final int ROW_HEIGHT = 22;

    record Row(String title, String detail, int color) {
    }

    private int x;
    private int y;
    private int width;
    private int height;
    private double scroll;
    private int selected = -1;

    void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = Math.max(ROW_HEIGHT, height);
    }

    int selected() {
        return selected;
    }

    void setSelected(int index) {
        selected = index;
    }

    boolean contains(double mx, double my) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }

    void scroll(double amount, int rowCount) {
        scroll = Mth.clamp(scroll - amount * ROW_HEIGHT, 0, maxScroll(rowCount));
    }

    /** @return the row index under the cursor, or -1 */
    int rowAt(double mx, double my, int rowCount) {
        if (!contains(mx, my)) {
            return -1;
        }
        int index = (int) ((my - y + scroll) / ROW_HEIGHT);
        return index >= 0 && index < rowCount ? index : -1;
    }

    void render(GuiGraphics g, Font font, List<Row> rows, int mouseX, int mouseY) {
        scroll = Mth.clamp(scroll, 0, maxScroll(rows.size()));
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF505050);
        g.fill(x, y, x + width, y + height, 0xFF181818);
        g.enableScissor(x, y, x + width, y + height);
        int hovered = rowAt(mouseX, mouseY, rows.size());
        for (int i = 0; i < rows.size(); i++) {
            int top = y + i * ROW_HEIGHT - (int) scroll;
            if (top + ROW_HEIGHT < y || top > y + height) {
                continue;
            }
            if (i == selected) {
                g.fill(x, top, x + width, top + ROW_HEIGHT, 0xFF3A5A8A);
            } else if (i == hovered) {
                g.fill(x, top, x + width, top + ROW_HEIGHT, 0xFF2C2C2C);
            }
            Row row = rows.get(i);
            g.drawString(font, row.title(), x + 6, top + 3, row.color(), false);
            g.drawString(font, font.plainSubstrByWidth(row.detail(), width - 12), x + 6, top + 12, 0xFF9A9A9A, false);
        }
        g.disableScissor();
        int max = maxScroll(rows.size());
        if (max > 0) {
            int barHeight = Math.max(10, height * height / (rows.size() * ROW_HEIGHT));
            int barTop = y + (int) ((height - barHeight) * (scroll / max));
            g.fill(x + width - 3, barTop, x + width, barTop + barHeight, 0xFF8A8A8A);
        }
    }

    private int maxScroll(int rowCount) {
        return Math.max(0, rowCount * ROW_HEIGHT - height);
    }
}
