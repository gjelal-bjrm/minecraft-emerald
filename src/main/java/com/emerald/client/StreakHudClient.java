package com.emerald.client;

import com.emerald.network.StreakPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * LA SERIE, A L'ECRAN.
 *
 * « On doit voir visuellement la serie s'il y en a une. » Sous la colonne de
 * la meteo, un bloc qui n'existe que pendant une serie :
 *
 *     SERIE x2        7 kills
 *     ############.....      <- les huit secondes qui restent, qui se vident
 *
 * A chaque palier, un « x2 ! » au centre de l'ecran, grossi, dans l'esprit des
 * chiffres de degats, et une cloche qui monte. Quand la barre se vide :
 * « Serie perdue » en gris, une seconde, et le bloc disparait.
 *
 * La barre se calcule ICI, depuis le tic du dernier kill : le serveur ne parle
 * qu'aux kills. C'est ce qui la rend parfaitement fluide sans un paquet par
 * tique.
 */
public final class StreakHudClient {

    /** La fenetre de la serie, en tiques : la meme que celle du serveur. */
    public static final int WINDOW = 160;
    private static final int WIDTH = 116;
    private static final int GOLD = 0xFFFFC46B;
    private static final int GREY = 0xFF9AA0A6;
    private static final int POP_TICKS = 28;
    private static final int LOST_TICKS = 24;

    private static int count;
    private static int multiplierTenths = 10;
    private static long killTick = -1L;
    private static long popUntil = -1L;
    private static String popText = "";
    private static long lostUntil = -1L;

    private StreakHudClient() {
    }

    public static void accept(StreakPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? 0L : mc.level.getGameTime();
        if (payload.count() <= 0) {
            if (count > 0) {
                lostUntil = now + LOST_TICKS;
            }
            count = 0;
            multiplierTenths = 10;
            killTick = -1L;
            return;
        }
        // UN PALIER FRANCHI SE FETE : le chiffre saute au milieu de l'ecran et
        // une cloche monte d'un ton par palier
        if (payload.multiplierTenths() > multiplierTenths || count == 0) {
            if (payload.multiplierTenths() > 10) {
                popText = "×" + label(payload.multiplierTenths()) + " !";
                popUntil = now + POP_TICKS;
                if (mc.player != null) {
                    mc.player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.9F,
                            0.9F + 0.15F * (payload.multiplierTenths() / 5));
                }
            }
        }
        count = payload.count();
        multiplierTenths = payload.multiplierTenths();
        killTick = payload.killTick();
        lostUntil = -1L;
    }

    private static String label(int tenths) {
        return tenths % 10 == 0 ? String.valueOf(tenths / 10) : (tenths / 10) + "," + (tenths % 10);
    }

    /** Dessine le bloc et rend la hauteur occupee, zero s'il n'y a rien. */
    public static int render(GuiGraphics graphics, Minecraft mc, int x, int y) {
        if (mc.level == null) {
            return 0;
        }
        long now = mc.level.getGameTime();
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(true);

        if (count <= 0) {
            if (lostUntil > now) {
                graphics.fill(x, y, x + WIDTH, y + 12, 0x8C060608);
                graphics.drawString(mc.font, Component.translatable("game.emeraldweapons.hud.streak.lost"),
                        x + 3, y + 2, GREY, false);
                return 14;
            }
            return 0;
        }
        float left = WINDOW - (now - killTick + partial);
        if (left <= 0.0F) {
            // le serveur confirmera d'un paquet ; en attendant la barre est vide
            left = 0.0F;
        }
        graphics.fill(x, y, x + WIDTH, y + 22, 0x8C060608);
        Component head = Component.translatable("game.emeraldweapons.hud.streak")
                .append(multiplierTenths > 10 ? " ×" + label(multiplierTenths) : "");
        graphics.drawString(mc.font, head, x + 3, y + 2, GOLD, false);
        Component kills = Component.translatable("game.emeraldweapons.hud.streak.kills", count);
        graphics.drawString(mc.font, kills, x + WIDTH - 3 - mc.font.width(kills), y + 2, 0xFFF2F2F2, false);
        // la barre : ce qui reste des huit secondes
        int barW = WIDTH - 6;
        int filled = Math.round(barW * Math.max(0.0F, Math.min(1.0F, left / WINDOW)));
        graphics.fill(x + 3, y + 14, x + 3 + barW, y + 18, 0xFF2A2A2E);
        graphics.fill(x + 3, y + 14, x + 3 + filled, y + 18, left < 40 ? 0xFFFF616B : GOLD);

        if (popUntil > now) {
            // LE PALIER, au centre : grossi a l'impact, puis stable
            float t = (popUntil - now - partial) / (float) POP_TICKS;   // 1 -> 0
            float scale = 2.2F + 1.2F * Math.max(0.0F, t - 0.7F) / 0.3F;
            int cx = graphics.guiWidth() / 2;
            int cy = graphics.guiHeight() / 2 - 44;
            graphics.pose().pushPose();
            graphics.pose().translate(cx, cy, 0.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            int alpha = (int) (255 * Math.min(1.0F, t * 3.0F));
            graphics.drawCenteredString(mc.font, popText, 0, -4, (alpha << 24) | 0xFFC46B);
            graphics.pose().popPose();
        }
        return 24;
    }
}
