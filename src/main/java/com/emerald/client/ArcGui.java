package com.emerald.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Le dessin des panneaux de l'inventaire d'Arcencium, dans le style des ecrans du jeu :
 * gris clair, un filet noir, la lumiere en haut a gauche et l'ombre en bas a droite.
 * Des aplats plutot qu'une texture : la hauteur et la largeur varient avec le nombre de
 * cases d'artefacts et de sacs.
 */
public final class ArcGui {

    public static final int BODY = 0xFFC6C6C6;
    public static final int LIGHT = 0xFFFFFFFF;
    public static final int SHADOW = 0xFF555555;
    public static final int EDGE = 0xFF000000;
    public static final int SLOT = 0xFF8B8B8B;
    public static final int SLOT_DARK = 0xFF373737;
    public static final int INK = 0xFF404040;
    public static final int PALE = 0xFF707070;

    private ArcGui() {
    }

    /** Un panneau aux coins arrondis, comme le fond d'un coffre. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 2, y, x + w - 3, y + 1, EDGE);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, EDGE);
        g.fill(x, y + 2, x + 1, y + h - 3, EDGE);
        g.fill(x + w - 1, y + 3, x + w, y + h - 2, EDGE);
        g.fill(x + 1, y + 1, x + 2, y + 2, EDGE);
        g.fill(x + w - 3, y + 1, x + w - 2, y + 2, EDGE);
        g.fill(x + w - 2, y + 2, x + w - 1, y + 3, EDGE);
        g.fill(x + 1, y + h - 3, x + 2, y + h - 2, EDGE);
        g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, BODY);
        g.fill(x + 2, y + 1, x + w - 3, y + 3, LIGHT);
        g.fill(x + 1, y + 2, x + 3, y + h - 3, LIGHT);
        g.fill(x + 3, y + h - 3, x + w - 2, y + h - 1, SHADOW);
        g.fill(x + w - 3, y + 3, x + w - 1, y + h - 2, SHADOW);
        g.fill(x + 2, y + h - 3, x + 3, y + h - 2, BODY);
        g.fill(x + w - 3, y + 2, x + w - 2, y + 3, BODY);
    }

    /** Le cadre d'une case : 18 x 18, l'objet se pose a (x + 1, y + 1). */
    public static void slot(GuiGraphics g, int x, int y) {
        inset(g, x, y, 18, 18);
    }

    /** Un creux : ombre en haut a gauche, lumiere en bas a droite. */
    public static void inset(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, SLOT);
        g.fill(x, y, x + w - 1, y + 1, SLOT_DARK);
        g.fill(x, y, x + 1, y + h - 1, SLOT_DARK);
        g.fill(x + 1, y + h - 1, x + w, y + h, LIGHT);
        g.fill(x + w - 1, y + 1, x + w, y + h, LIGHT);
    }

    /** Une bosse : un bouton ou le curseur de la barre de defilement. */
    public static void raised(GuiGraphics g, int x, int y, int w, int h, boolean hover) {
        g.fill(x, y, x + w, y + h, EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? 0xFFDADADA : BODY);
        g.fill(x + 1, y + 1, x + w - 2, y + 2, LIGHT);
        g.fill(x + 1, y + 1, x + 2, y + h - 2, LIGHT);
        g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, SHADOW);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, SHADOW);
    }

    /** Un cadre d'un pixel. */
    public static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /**
     * Un texte qui doit tenir dans sa colonne : reduit s'il le faut (jusqu'a la moitie),
     * centre sur sa ligne. Les reserves comptees avec le sac (« 300/4 », « 14/10 »)
     * debordaient sur la colonne voisine.
     */
    public static void drawFit(GuiGraphics g, net.minecraft.client.gui.Font font, net.minecraft.network.chat.Component text,
                               int x, int y, int color, int maxWidth) {
        int width = font.width(text);
        if (width <= maxWidth || maxWidth <= 0) {
            g.drawString(font, text, x, y, color, false);
            return;
        }
        float scale = Math.max(0.5F, maxWidth / (float) width);
        g.pose().pushPose();
        g.pose().translate(x, y + (font.lineHeight - 1) * (1.0F - scale) / 2.0F, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    public static void drawFit(GuiGraphics g, net.minecraft.client.gui.Font font, String text,
                               int x, int y, int color, int maxWidth) {
        drawFit(g, font, net.minecraft.network.chat.Component.literal(text), x, y, color, maxWidth);
    }

    /** L'icone du tri : trois barres, de la plus longue a la plus courte. */
    public static void sortIcon(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 3, y + 4, x + 15, y + 6, color);
        g.fill(x + 3, y + 8, x + 12, y + 10, color);
        g.fill(x + 3, y + 12, x + 9, y + 14, color);
    }

    /** L'icone de la poubelle, dans une case vide. */
    public static void trashIcon(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 6, y + 2, x + 10, y + 3, color);
        g.fill(x + 3, y + 3, x + 13, y + 5, color);
        g.fill(x + 4, y + 6, x + 12, y + 7, color);
        g.fill(x + 4, y + 6, x + 5, y + 15, color);
        g.fill(x + 11, y + 6, x + 12, y + 15, color);
        g.fill(x + 4, y + 14, x + 12, y + 15, color);
        g.fill(x + 6, y + 8, x + 7, y + 13, color);
        g.fill(x + 9, y + 8, x + 10, y + 13, color);
    }
}
