package com.emerald.client;

import com.emerald.menu.curio.CurioPanel;
import com.emerald.menu.curio.CurioRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.AbstractContainerMenu;

import javax.annotation.Nullable;

/**
 * La fenetre des artefacts a l'ecran (CurioPanel) : le fond, les cadres des cases montrees, la
 * barre de defilement. La molette et la barre demandent la rangee au serveur ; comme pour le sac,
 * la rangee demandee est retenue jusqu'a ce qu'il la confirme.
 */
public final class CurioPanelView {

    private static final int TRACK_X = CurioPanel.SLOTS_X - 1 + CurioPanel.COLS * 18 + 2;
    private static final int TRACK_Y = CurioPanel.SLOTS_Y - 1;
    private static final int TRACK_W = 7;
    private static final int TRACK_H = CurioPanel.ROWS * 18;
    private static final int MISSING = 0xFFADADAD;

    private final AbstractContainerMenu menu;
    private final CurioPanel panel;
    private int pendingRow = -1;
    private int pendingTicks;
    private boolean dragging;

    public CurioPanelView(AbstractContainerMenu menu, CurioPanel panel) {
        this.menu = menu;
        this.panel = panel;
    }

    public void tick() {
        if (this.pendingRow >= 0 && (this.panel.firstRow() == this.pendingRow || ++this.pendingTicks > 20)) {
            this.pendingRow = -1;
            this.pendingTicks = 0;
        }
    }

    private int targetRow() {
        return this.pendingRow >= 0 ? this.pendingRow : this.panel.firstRow();
    }

    public void renderBg(GuiGraphics g, int left, int top, int mouseX, int mouseY) {
        int x = left + this.panel.x;
        int y = top + this.panel.y;
        ArcGui.panel(g, x, y, CurioPanel.WIDTH, CurioPanel.HEIGHT);
        for (int row = 0; row < CurioPanel.ROWS; row++) {
            for (int col = 0; col < CurioPanel.COLS; col++) {
                int sx = x + CurioPanel.SLOTS_X - 1 + col * 18;
                int sy = y + CurioPanel.SLOTS_Y - 1 + row * 18;
                if (this.panel.ref(col + row * CurioPanel.COLS) != null) {
                    ArcGui.slot(g, sx, sy);
                } else {
                    g.fill(sx, sy, sx + 18, sy + 18, MISSING);
                }
            }
        }
        ArcGui.inset(g, x + TRACK_X, y + TRACK_Y, TRACK_W, TRACK_H);
        int maxFirst = this.panel.maxFirstRow();
        int thumb = thumbHeight();
        int ty = y + TRACK_Y + 1 + (maxFirst == 0 ? 0 : (TRACK_H - 2 - thumb) * targetRow() / maxFirst);
        boolean hover = BagPanelView.inside(mouseX, mouseY, x + TRACK_X, y + TRACK_Y, TRACK_W, TRACK_H);
        ArcGui.raised(g, x + TRACK_X + 1, ty, TRACK_W - 2, thumb, hover || this.dragging);
    }

    private int thumbHeight() {
        int rows = Math.max(CurioPanel.ROWS, this.panel.rows());
        return Math.max(10, (TRACK_H - 2) * CurioPanel.ROWS / rows);
    }

    /** Le type de la case d'artefact sous la souris, pour l'infobulle d'une case vide. */
    @Nullable
    public CurioRef refAt(int windowSlot) {
        return this.panel.ref(windowSlot);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int left, int top) {
        int x = left + this.panel.x;
        int y = top + this.panel.y;
        if (button == 0 && this.panel.maxFirstRow() > 0
                && BagPanelView.inside(mouseX, mouseY, x + TRACK_X, y + TRACK_Y, TRACK_W, TRACK_H)) {
            this.dragging = true;
            scrollTo(mouseY, y);
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int left, int top) {
        if (!this.dragging) {
            return false;
        }
        scrollTo(mouseY, top + this.panel.y);
        return true;
    }

    public boolean mouseReleased(int button) {
        if (this.dragging && button == 0) {
            this.dragging = false;
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY, int left, int top) {
        int x = left + this.panel.x;
        int y = top + this.panel.y;
        if (scrollY == 0 || !BagPanelView.inside(mouseX, mouseY, x, y, CurioPanel.WIDTH, CurioPanel.HEIGHT)) {
            return false;
        }
        requestRow(targetRow() + (scrollY > 0 ? -1 : 1));
        return true;
    }

    private void scrollTo(double mouseY, int y) {
        int thumb = thumbHeight();
        double f = (mouseY - (y + TRACK_Y + 1) - thumb / 2.0) / Math.max(1, TRACK_H - 2 - thumb);
        requestRow((int) Math.round(Mth.clamp(f, 0.0, 1.0) * this.panel.maxFirstRow()));
    }

    private void requestRow(int row) {
        int clamped = Mth.clamp(row, 0, this.panel.maxFirstRow());
        if (clamped == targetRow()) {
            return;
        }
        this.pendingRow = clamped;
        this.pendingTicks = 0;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, CurioPanel.BUTTON_ROW + clamped);
        }
    }
}
