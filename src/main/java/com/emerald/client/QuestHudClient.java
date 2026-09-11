package com.emerald.client;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.network.QuestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * La ligne du carnet : une seule, au-dessus de la fiche du Heros, qui dit
 * l'etape en cours. Pas de panneau, pas de liste -- le carnet n'est pas un
 * menu, c'est un cap. Le detail s'obtient avec /arcencium quete.
 */
public final class QuestHudClient {

    /** La fiche du Heros fait 26 de haut a 4 du bord ; on se pose juste au-dessus. */
    private static final int ABOVE_HERO = 26 + 4 + 6;
    private static final int MARGIN = 4;
    private static final int AQUA = 0xFF9CE8FF;
    private static final int PALE = 0xFFB8B6C4;

    private static QuestPayload page;

    private QuestHudClient() {
    }

    public static void accept(QuestPayload payload) {
        page = payload;
    }

    private static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (page == null || mc.player == null || mc.options.hideGui || mc.screen != null
                || page.key().isEmpty()) {
            return;
        }
        int y = graphics.guiHeight() - ABOVE_HERO - 10;
        Component head = Component.translatable("quest.emeraldweapons.hud",
                page.step() + 1, page.total());
        Component title = Component.translatable("quest.emeraldweapons." + page.key() + ".title");
        graphics.drawString(mc.font, head, MARGIN, y, PALE, true);
        graphics.drawString(mc.font, title, MARGIN + mc.font.width(head) + 4, y, AQUA, true);
    }

    @EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT,
            bus = EventBusSubscriber.Bus.MOD)
    public static class Setup {
        @SubscribeEvent
        public static void onRegisterLayers(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.HOTBAR,
                    ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "quest_hud"),
                    (LayeredDraw.Layer) QuestHudClient::render);
        }
    }
}
