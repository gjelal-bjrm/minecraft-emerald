package com.emerald.client;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.network.ProbeInfoPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Le panneau de la Sonde du Sanctuaire.
 *
 * Il s'affiche seul, sans commande, tant qu'on tient la Sonde : c'est toute la
 * difference avec ce qu'on avait avant. Verifier un bloc valait une commande ;
 * en parcourir vingt n'en valait aucune, et l'on renoncait a chercher.
 *
 * Quatre lignes, dans cet ordre : le chantier d'abord, parce que c'est lui qui
 * designe la routine et donc la ligne de code ; l'adresse dans la structure
 * ensuite, parce qu'elle est stable d'une partie a l'autre ; puis le bloc et
 * enfin sa position dans le monde, qui ne servent qu'a s'y retrouver sur place.
 *
 * Le panneau se place a DROITE : la gauche appartient au panneau de partie, et
 * deux cadres qui se recouvrent ne se lisent ni l'un ni l'autre.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class ProbeHudClient {

    private static ProbeInfoPayload info = new ProbeInfoPayload("", "", "", "");

    private static final int MARGIN = 6;
    private static final int PAD = 4;
    private static final int LINE = 10;

    private ProbeHudClient() {
    }

    public static void accept(ProbeInfoPayload payload) {
        info = payload;
    }

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR,
                ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "probe_panel"),
                (LayeredDraw.Layer) ProbeHudClient::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || info.block().isEmpty()) {
            return;
        }
        var font = mc.font;
        String[] lines = {
                info.part(),
                info.local(),
                info.block(),
                info.world(),
        };
        int[] colours = {0xFF9CE8FF, 0xFFF8D870, 0xFFE8E8E8, 0xFF9A9AA2};

        int width = 0;
        int rows = 0;
        for (String line : lines) {
            if (line != null && !line.isEmpty()) {
                width = Math.max(width, font.width(line));
                rows++;
            }
        }
        if (rows == 0) {
            return;
        }
        int w = width + PAD * 2;
        int h = rows * LINE + PAD * 2 - 2;
        int x = graphics.guiWidth() - MARGIN - w;
        int y = MARGIN;

        graphics.fill(x, y, x + w, y + h, 0xC0060608);
        // un filet clair sur le bord gauche : le cadre doit se detacher d'un
        // ciel clair sans pour autant devenir un element de decor
        graphics.fill(x, y, x + 1, y + h, 0xFF9CE8FF);

        int row = 0;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i] == null || lines[i].isEmpty()) {
                continue;
            }
            graphics.drawString(font, lines[i], x + PAD, y + PAD + row * LINE,
                    colours[i], false);
            row++;
        }
    }
}
