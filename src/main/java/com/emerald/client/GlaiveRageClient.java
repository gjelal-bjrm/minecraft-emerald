package com.emerald.client;

import com.emerald.item.ModItems;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.weapons.ArcenciumGlaiveItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.awt.Color;

/**
 * La barre de Rage du Glaive.
 *
 * Les cinq crans se lisaient deja sur l'objet -- la foudre monte dans la lame
 * -- mais une texture d'inventaire large de trente-deux pixels ne dit pas un
 * compte : on voit que quelque chose s'allume, on ne sait pas combien il
 * reste. Or toute la conduite de cette arme tient au compte, puisque c'est a
 * cinq que la Curee part et a trois que la Ruee cloue.
 *
 * La barre ne s'affiche QUE lorsqu'on tient l'arme, et disparait des que la
 * Rage retombe : une jauge permanente a zero devient un element de decor qu'on
 * ne regarde plus, et le mode a deja son panneau en haut a gauche.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class GlaiveRageClient {

    private static final int WIDTH = 62;
    private static final int HEIGHT = 5;
    /** Ce que la barre laisse entre elle et la barre d'action. */
    private static final int LIFT = 24;

    private GlaiveRageClient() {
    }

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR,
                ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "glaive_rage"),
                (LayeredDraw.Layer) GlaiveRageClient::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        ItemStack stack = mc.player.getMainHandItem();
        if (!stack.is(ModItems.ARCENCIUM_GLAIVE.get())) {
            stack = mc.player.getOffhandItem();
            if (!stack.is(ModItems.ARCENCIUM_GLAIVE.get())) {
                return;
            }
        }
        int rage = ArcenciumGlaiveItem.rage(stack, mc.level);
        if (rage <= 0) {
            return;
        }

        int x = (graphics.guiWidth() - WIDTH) / 2;
        int y = graphics.guiHeight() - LIFT - HEIGHT;

        // le fond, et son cadre : sans eux la barre se perd sur un ciel clair
        graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, 0xC0060608);

        // Les cinq crans sont SEPARES, et non une jauge continue.
        //
        // Le compte est ce qui compte : on doit voir « trois sur cinq » d'un
        // coup d'oeil, pas estimer une proportion. Une barre pleine a soixante
        // pour cent ne dit pas si la Curee est a un coup ou a deux.
        int cell = WIDTH / ArcenciumGlaiveItem.RAGE_MAX;
        long time = mc.level.getGameTime();
        for (int i = 0; i < ArcenciumGlaiveItem.RAGE_MAX; i++) {
            int left = x + i * cell;
            int right = left + cell - 1;
            if (i < rage) {
                // la teinte tourne, comme la foudre de la lame : les deux
                // signaux doivent se reconnaitre comme un seul
                float hue = (float) (((time * 0.01) + i * 0.09) % 1.0);
                int rgb = Color.HSBtoRGB(hue, 0.62F, 1.0F);
                graphics.fill(left, y, right, y + HEIGHT, 0xFF000000 | rgb);
            } else {
                graphics.fill(left, y, right, y + HEIGHT, 0x66202028);
            }
        }

        // A PLEINE RAGE, la barre se souligne.
        //
        // C'est le seul etat qui change ce que fait le clic gauche : le
        // signaler autrement que par « une case de plus » evite d'avoir a
        // compter au moment ou l'on a le moins de temps pour le faire.
        if (rage >= ArcenciumGlaiveItem.RAGE_MAX) {
            float hue = (float) ((time * 0.02) % 1.0);
            int rgb = Color.HSBtoRGB(hue, 0.45F, 1.0F);
            graphics.fill(x - 1, y + HEIGHT + 1, x + WIDTH + 1, y + HEIGHT + 2,
                    0xFF000000 | rgb);
        }
    }
}
