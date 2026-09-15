package com.emerald.jak.gun;

import com.emerald.haven.Haven;
import com.emerald.item.ModItems;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Le HUD du Morph Gun, au-dessus de la barre d'objets et des jauges de vie.
 *
 * QUATRE JAUGES, dans l'ordre de la croix (rouge, jaune, bleu, sombre), chacune
 * de {@value #CELL_W} x {@value #CELL_H} pixels : la reserve en chiffres, a la
 * couleur de sa famille, et une barre de la couleur du reflet du jeu. La famille
 * tenue est encadree. Une reserve vide clignote en rouge -- le chargeur disparait
 * du modele en meme temps (MorphGunItemRenderer) : sans cette explication, on
 * croirait a un bogue. Au-dessus, le nom de l'arme tenue ; « a sec » quand sa
 * reserve est vide.
 *
 * VISIBLE SEULEMENT arme en main dans Haven, hors vehicule (voitures et motos ont
 * leur rappel), ni spectateur, ni HUD cache (F1). Place au-dessus des coeurs, de
 * l'armure et de l'air (Gui.leftHeight et rightHeight, lus apres ces calques), et
 * les RELEVE ensuite : le nom de l'objet tenu et la barre d'action (vote, arrivee,
 * bascule invasion/paisible, bloc protege) passent au-dessus du HUD, jamais dessus.
 *
 * La mise en page vit dans {@link #draw}, que la planche hors ligne
 * (tools/gun_particles.py) reprend chiffre pour chiffre.
 */
public final class GunHud {

    public static final int CELL_W = 44;
    public static final int CELL_H = 15;
    public static final int GAP = 2;
    public static final int NAME_H = 11;
    public static final int WIDTH = 4 * CELL_W + 3 * GAP;
    /** Une ligne de texte vanilla posee a h - leftHeight : 9 pixels et l'ombre. */
    public static final int LINE_H = 10;

    private GunHud() {
    }

    static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui || !Haven.is(mc.level)
                || player.isPassenger() || player.isSpectator()) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.MORPH_GUN.get())) {
            return;
        }
        MorphGunData data = MorphGunData.of(stack);
        if (data == null) {
            return;
        }
        int below = Math.max(mc.gui.leftHeight, mc.gui.rightHeight);
        int bottom = graphics.guiHeight() - below - 2;
        draw(graphics, mc.font, graphics.guiWidth() / 2 - WIDTH / 2, bottom, data, mc.level.getGameTime());
        // Les calques vanilla suivants (nom de l'objet tenu, barre d'action) se posent a
        // h - leftHeight (h - leftHeight - 9 pour la barre d'action) et descendent d'une
        // ligne de texte : on leur donne la hauteur du HUD plus cette ligne, sinon le
        // compte a rebours du vote ou l'annonce de la bascule s'ecrivent sur les jauges.
        int used = heightAbove(below);
        mc.gui.leftHeight = used;
        mc.gui.rightHeight = used;
    }

    /** Ce que Gui.leftHeight et rightHeight valent apres le HUD, `below` etant leur maximum avant. */
    static int heightAbove(int below) {
        return below + 2 + CELL_H + NAME_H + LINE_H;
    }

    /** Le HUD, coin gauche en `left`, bas des jauges en `bottom`. */
    static void draw(GuiGraphics graphics, Font font, int left, int bottom, MorphGunData data, long time) {
        int top = bottom - CELL_H;
        GunForm.Family held = data.form().family;
        for (GunForm.Family family : GunForm.Family.values()) {
            int x = left + family.ordinal() * (CELL_W + GAP);
            int eco = data.eco(family);
            int text = 0xFF000000 | MorphGunItem.textColor(family);
            graphics.fill(x, top, x + CELL_W, bottom, 0xB0101014);
            if (family == held) {
                graphics.fill(x - 1, top - 1, x + CELL_W + 1, top, text);
                graphics.fill(x - 1, bottom, x + CELL_W + 1, bottom + 1, text);
                graphics.fill(x - 1, top, x, bottom, text);
                graphics.fill(x + CELL_W, top, x + CELL_W + 1, bottom, text);
            }
            int x0 = x + 3;
            int x1 = x + CELL_W - 3;
            graphics.fill(x0, bottom - 4, x1, bottom - 2, 0xFF2A2A30);
            int filled = (int) Math.round((x1 - x0) * eco / (double) family.capacity);
            if (eco > 0 && filled == 0) {
                filled = 1;
            }
            graphics.fill(x0, bottom - 4, x0 + filled, bottom - 2, 0xFF000000 | (family.color & 0xFFFFFF));
            String number = Integer.toString(eco);
            int color = eco > 0 ? text : (time / 6L) % 2L == 0L ? 0xFFFF5050 : 0xFF803030;
            graphics.drawString(font, number, x + (CELL_W - font.width(number)) / 2, top + 2, color, true);
        }
        Component name = Component.translatable(data.form().translationKey());
        if (data.eco(held) <= 0) {
            name = Component.translatable("hud.emeraldweapons.morph_gun.empty", name);
        }
        graphics.drawCenteredString(font, name, left + WIDTH / 2, top - NAME_H + 1,
                0xFF000000 | MorphGunItem.textColor(held));
    }
}
