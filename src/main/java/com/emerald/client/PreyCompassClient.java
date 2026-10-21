package com.emerald.client;

import com.emerald.network.VeinSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * LA BOUSSOLE DE LA BATTUE (21 oct. 2026, cahier §113).
 *
 * « Quand la Battue arrive, je ne sais meme pas ou est la cible. J'aurais aime quelque
 * chose qui me dise dans quelle direction aller. » Le cap de la Proie arrivait deja au
 * client toutes les deux secondes, mais il ne se lisait que dans une fleche d'un caractere,
 * au bas du panneau de gauche : on ne la voyait pas en courant.
 *
 * Ici, une REGLE en haut au centre de l'ecran, comme une boussole de marche : son milieu est
 * droit devant, ses bords a quatre-vingt-dix degres. Un repere dore y glisse vers la Proie ;
 * derriere soi, il se colle au bord avec un chevron qui dit de quel cote tourner. Dessous :
 * la distance, et s'il faut monter ou descendre.
 *
 * Ce n'est qu'un CAP : rien ne se dessine dans le monde, ni losange ni nom a travers les
 * murs -- la demande du 22 septembre contre l'effet « wall hack » tient toujours. De pres,
 * la Proie brille d'elle-meme (BattueHunt.PREY_SIGHT).
 */
public final class PreyCompassClient {

    private static final int WIDTH = 182;
    private static final int TOP = 4;
    private static final int GOLD = 0xFFFFC46B;
    private static final int PALE = 0xFFE8DCC0;
    /** Le demi-angle que couvre la regle. */
    private static final double SPAN = 90.0;

    private PreyCompassClient() {
    }

    /** Dessine la boussole s'il y a une Proie connue ; rend la hauteur occupee, zero sinon. */
    public static int render(GuiGraphics graphics, Minecraft mc) {
        if (mc.player == null) {
            return 0;
        }
        BlockPos prey = null;
        java.util.List<Long> shown = VeinHudClient.shown();
        for (int i = 0; i < shown.size(); i++) {
            if (VeinSyncPayload.kindAt(VeinHudClient.kinds(), i) == VeinSyncPayload.KIND_PREY) {
                prey = BlockPos.of(shown.get(i));
                break;
            }
        }
        if (prey == null) {
            return 0;
        }
        double dx = prey.getX() + 0.5 - mc.player.getX();
        double dy = prey.getY() + 0.5 - mc.player.getY();
        double dz = prey.getZ() + 0.5 - mc.player.getZ();
        // meme repere que la fleche du panneau, verifie a l'ecran (VeinHudClient.arrow)
        double toTarget = Math.toDegrees(Math.atan2(dz, dx));
        double looking = mc.player.getYRot() + 90.0;
        double delta = Mth.wrapDegrees(toTarget - looking);

        int cx = graphics.guiWidth() / 2;
        int left = cx - WIDTH / 2;
        int y = TOP;
        // la regle : un fond, des graduations tous les 22,5 degres, le milieu marque
        graphics.fill(left, y, left + WIDTH, y + 12, 0x8C060608);
        for (int k = -4; k <= 4; k++) {
            int tx = cx + (int) Math.round(k * 22.5 / SPAN * (WIDTH / 2.0 - 4));
            int h = k == 0 ? 8 : (k % 2 == 0 ? 5 : 3);
            graphics.fill(tx, y + 12 - h, tx + 1, y + 12, k == 0 ? 0xFFFFFFFF : 0x80FFFFFF);
        }
        // le repere : sur la regle si la Proie est devant, colle au bord sinon
        boolean behind = Math.abs(delta) > SPAN;
        double clamped = Math.max(-SPAN, Math.min(SPAN, delta));
        int mx = cx + (int) Math.round(clamped / SPAN * (WIDTH / 2.0 - 4));
        int pulse = (mc.level == null ? 0 : (int) (mc.level.getGameTime() % 20)) < 10 ? GOLD : 0xFFFFE2A8;
        graphics.fill(mx - 2, y + 1, mx + 3, y + 11, pulse);
        if (behind) {
            String chevron = delta > 0 ? "»" : "«";
            int w = mc.font.width(chevron);
            int cxv = delta > 0 ? left + WIDTH + 3 : left - 3 - w;
            graphics.drawString(mc.font, chevron, cxv, y + 2, GOLD, true);
        } else if (Math.abs(delta) <= 6.0) {
            // aligne : on peut foncer tout droit
            graphics.fill(cx - 1, y - 2, cx + 2, y, GOLD);
        }
        // dessous : le nom, la distance, la hauteur
        int flat = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        int up = (int) Math.round(dy);
        String height = Math.abs(up) <= 2 ? "" : (up > 0 ? "  ▲" + up : "  ▼" + (-up));
        Component label = Component.translatable("weather.emeraldweapons.vein.prey")
                .append(Component.literal("  " + flat + " m" + height));
        int lw = mc.font.width(label);
        graphics.drawString(mc.font, label, cx - lw / 2, y + 15, behind ? PALE : GOLD, true);
        return 15 + 10;
    }
}
