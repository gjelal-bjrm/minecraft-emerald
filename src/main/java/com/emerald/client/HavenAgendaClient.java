package com.emerald.client;

import com.emerald.haven.journey.HavenAgendaPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;

/** L'agenda de Haven s'ouvre dans l'ecran de livre de Minecraft, avec les pages du serveur. */
public final class HavenAgendaClient {

    private HavenAgendaClient() {
    }

    public static void accept(HavenAgendaPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || payload.pages().isEmpty()) {
            return;
        }
        mc.setScreen(new BookViewScreen(new BookViewScreen.BookAccess(payload.pages())));
    }
}
