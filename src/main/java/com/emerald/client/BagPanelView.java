package com.emerald.client;

import com.emerald.menu.bag.BagMenu;
import com.emerald.menu.bag.BagPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Le panneau du sac a l'ecran : le meme dans l'inventaire d'Arcencium, la Forge, l'Autel
 * et l'Etabli. Les cases sont celles du menu (le jeu les dessine) ; ce panneau dessine
 * le fond, les cadres, la barre de defilement, les onglets et le bouton de tri, et
 * envoie au serveur les demandes de defilement, d'onglet et de tri par les boutons de
 * menu du jeu.
 *
 * LE DEFILEMENT EST DEMANDE, PAS PREDIT : la rangee et les cases reviennent du serveur a
 * la tique suivante. Pour qu'une molette tournee vite ne perde pas de crans, la rangee
 * demandee est retenue (pending) jusqu'a ce que le serveur la confirme.
 */
public final class BagPanelView {

    private static final int TRACK_X = 174;
    private static final int TRACK_Y = BagPanel.SLOTS_Y - 1;
    private static final int TRACK_W = 8;
    private static final int TRACK_H = BagPanel.ROWS * 18;
    private static final int TABS_Y = 132;
    private static final int TAB = 20;
    private static final int SORT_X = BagPanel.WIDTH - 8 - 18;
    private static final int SORT_Y = 133;
    private static final int MISSING = 0xFFADADAD;

    private final BagMenu menu;
    private final BagPanel panel;
    private int pendingRow = -1;
    private int pendingTicks;
    private boolean dragging;

    public BagPanelView(BagMenu menu) {
        this.menu = menu;
        this.panel = menu.bag();
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

    // ================================================================ dessin

    /** Le fond du panneau. Coordonnees de l'ecran : left et top sont l'origine de l'image. */
    public void renderBg(GuiGraphics g, int left, int top, int mouseX, int mouseY) {
        int x = left + this.panel.x;
        int y = top + this.panel.y;
        ArcGui.panel(g, x, y, BagPanel.WIDTH, BagPanel.HEIGHT);
        boolean present = this.panel.present();
        for (int row = 0; row < BagPanel.ROWS; row++) {
            for (int col = 0; col < BagPanel.COLS; col++) {
                int sx = x + BagPanel.SLOTS_X - 1 + col * 18;
                int sy = y + BagPanel.SLOTS_Y - 1 + row * 18;
                if (present && this.panel.shows(col + row * BagPanel.COLS)) {
                    ArcGui.slot(g, sx, sy);
                } else {
                    g.fill(sx, sy, sx + 18, sy + 18, MISSING);
                }
            }
        }
        ArcGui.inset(g, x + TRACK_X, y + TRACK_Y, TRACK_W, TRACK_H);
        int maxFirst = this.panel.maxFirstRow();
        if (present) {
            int thumb = thumbHeight();
            int ty = y + TRACK_Y + 1 + (maxFirst == 0 ? 0 : (TRACK_H - 2 - thumb) * targetRow() / maxFirst);
            boolean hover = inside(mouseX, mouseY, x + TRACK_X, y + TRACK_Y, TRACK_W, TRACK_H);
            ArcGui.raised(g, x + TRACK_X + 1, ty, TRACK_W - 2, thumb, hover || this.dragging);
        }
        List<ItemStack> icons = this.panel.icons();
        int tabs = Math.min(this.panel.tabs(), icons.size());
        if (tabs > 1) {
            for (int k = 0; k < tabs; k++) {
                int tx = x + 8 + k * (TAB + 2);
                int ty = y + TABS_Y;
                if (k == this.panel.selected()) {
                    ArcGui.raised(g, tx, ty, TAB, TAB, true);
                    ArcGui.outline(g, tx - 1, ty - 1, TAB + 2, TAB + 2, 0xFFFFD36B);
                } else {
                    ArcGui.inset(g, tx, ty, TAB, TAB);
                    if (inside(mouseX, mouseY, tx, ty, TAB, TAB)) {
                        g.fill(tx + 1, ty + 1, tx + TAB - 1, ty + TAB - 1, 0x30FFFFFF);
                    }
                }
                g.renderItem(icons.get(k), tx + 2, ty + 2);
            }
        }
        if (present) {
            int bx = x + SORT_X;
            int by = y + SORT_Y;
            ArcGui.raised(g, bx, by, 18, 18, inside(mouseX, mouseY, bx, by, 18, 18));
            ArcGui.sortIcon(g, bx, by, ArcGui.INK);
        }
    }

    private int thumbHeight() {
        int rows = Math.max(BagPanel.ROWS, this.panel.rows());
        return Math.max(10, (TRACK_H - 2) * BagPanel.ROWS / rows);
    }

    /** Les textes du panneau. Coordonnees de l'image (la pose est deja deplacee). */
    public void renderLabels(GuiGraphics g, Font font) {
        int x = this.panel.x;
        int y = this.panel.y;
        if (!this.panel.present()) {
            Component none = Component.translatable("gui.emeraldweapons.bag.none");
            g.drawString(font, none, x + (BagPanel.WIDTH - font.width(none)) / 2, y + 50, ArcGui.INK, false);
            List<FormattedCharSequence> lines = font.split(
                    Component.translatable("gui.emeraldweapons.bag.none.hint"), BagPanel.WIDTH - 24);
            for (int i = 0; i < lines.size(); i++) {
                FormattedCharSequence line = lines.get(i);
                g.drawString(font, line, x + (BagPanel.WIDTH - font.width(line)) / 2, y + 66 + i * 10,
                        ArcGui.PALE, false);
            }
            return;
        }
        Component count = Component.translatable("gui.emeraldweapons.bag.slots", this.panel.size());
        int countWidth = font.width(count);
        g.drawString(font, count, x + BagPanel.WIDTH - 8 - countWidth, y + 6, ArcGui.PALE, false);
        String title = font.plainSubstrByWidth(bagName().getString(), BagPanel.WIDTH - 20 - countWidth);
        g.drawString(font, title, x + 8, y + 6, ArcGui.INK, false);
        if (Math.min(this.panel.tabs(), this.panel.icons().size()) <= 1) {
            List<FormattedCharSequence> hint = font.split(
                    Component.translatable("gui.emeraldweapons.bag.hint"), SORT_X - 12);
            for (int i = 0; i < Math.min(2, hint.size()); i++) {
                g.drawString(font, hint.get(i), x + 8, y + TABS_Y + 2 + i * 10, ArcGui.PALE, false);
            }
        }
    }

    private Component bagName() {
        List<ItemStack> icons = this.panel.icons();
        int selected = this.panel.selected();
        if (selected >= 0 && selected < icons.size()) {
            return icons.get(selected).getHoverName();
        }
        return Component.translatable("gui.emeraldweapons.bag");
    }

    /** L'infobulle des onglets et du bouton de tri. Coordonnees de l'ecran. */
    public void renderTooltip(GuiGraphics g, Font font, int mouseX, int mouseY, int left, int top) {
        int x = left + this.panel.x;
        int y = top + this.panel.y;
        if (this.panel.present() && inside(mouseX, mouseY, x + SORT_X, y + SORT_Y, 18, 18)) {
            g.renderTooltip(font, Component.translatable("gui.emeraldweapons.bag.sort"), mouseX, mouseY);
            return;
        }
        List<ItemStack> icons = this.panel.icons();
        int tabs = Math.min(this.panel.tabs(), icons.size());
        if (tabs > 1) {
            for (int k = 0; k < tabs; k++) {
                if (inside(mouseX, mouseY, x + 8 + k * (TAB + 2), y + TABS_Y, TAB, TAB)) {
                    g.renderTooltip(font, icons.get(k).getHoverName(), mouseX, mouseY);
                    return;
                }
            }
        }
    }

    // ================================================================ souris

    public boolean mouseClicked(double mouseX, double mouseY, int button, int left, int top) {
        if (button != 0) {
            return false;
        }
        int x = left + this.panel.x;
        int y = top + this.panel.y;
        if (this.panel.present() && inside(mouseX, mouseY, x + SORT_X, y + SORT_Y, 18, 18)) {
            send(BagPanel.BUTTON_SORT_BAG);
            return true;
        }
        int tabs = Math.min(this.panel.tabs(), this.panel.icons().size());
        if (tabs > 1) {
            for (int k = 0; k < tabs; k++) {
                if (inside(mouseX, mouseY, x + 8 + k * (TAB + 2), y + TABS_Y, TAB, TAB)) {
                    if (k != this.panel.selected()) {
                        this.pendingRow = -1;
                        send(BagPanel.BUTTON_TAB + k);
                    }
                    return true;
                }
            }
        }
        if (this.panel.maxFirstRow() > 0 && inside(mouseX, mouseY, x + TRACK_X, y + TRACK_Y, TRACK_W, TRACK_H)) {
            this.dragging = true;
            scrollTo(mouseY, y);
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, int left, int top) {
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
        if (!this.panel.present() || scrollY == 0
                || !inside(mouseX, mouseY, x, y, BagPanel.WIDTH, BagPanel.HEIGHT)) {
            return false;
        }
        requestRow(targetRow() + (scrollY > 0 ? -1 : 1));
        return true;
    }

    private void scrollTo(double mouseY, int y) {
        int maxFirst = this.panel.maxFirstRow();
        int thumb = thumbHeight();
        double f = (mouseY - (y + TRACK_Y + 1) - thumb / 2.0) / Math.max(1, TRACK_H - 2 - thumb);
        requestRow((int) Math.round(Mth.clamp(f, 0.0, 1.0) * maxFirst));
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
            minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, BagPanel.BUTTON_ROW + clamped);
        }
    }

    private void send(int id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    /**
     * LE NOMBRE D'UNE CASE DU SAC au-dela de 99 : en petit, dans le coin de la case. Le jeu
     * ne prevoit que deux chiffres ; « 256 » occupait toute la case, et le « 5 » d'une case
     * collait au « 180 » de la suivante. Vrai si la case est dessinee ici.
     */
    public static boolean renderCount(GuiGraphics g, Font font, ItemStack stack, net.minecraft.world.inventory.Slot slot,
                                      @javax.annotation.Nullable String countString, int imageWidth) {
        if (!(slot instanceof com.emerald.menu.bag.BagSlot) || countString != null || stack.getCount() <= 99) {
            return false;
        }
        int x = slot.x;
        int y = slot.y;
        g.renderItem(stack, x, y, x + y * imageWidth);
        g.renderItemDecorations(font, stack, x, y, "");
        String text = String.valueOf(stack.getCount());
        g.pose().pushPose();
        g.pose().translate(x + 17.0F, y + 17.0F, 200.0F);
        g.pose().scale(0.75F, 0.75F, 1.0F);
        g.drawString(font, text, -font.width(text), -8, 0xFFFFFF, true);
        g.pose().popPose();
        return true;
    }

    /** Le bouton de tri de l'inventaire, commun aux ecrans. */
    public static void sortInventory(BagMenu menu) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, BagPanel.BUTTON_SORT_INVENTORY);
        }
    }

    static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
