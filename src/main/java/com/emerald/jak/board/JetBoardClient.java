package com.emerald.jak.board;

import com.emerald.haven.Haven;
import com.emerald.item.ModItems;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.network.JetBoardTogglePayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

/**
 * Le JET-Board, cote client (cahier §100) : sa touche et son dessin en objet.
 *
 * LA TOUCHE est ; (a droite du L ; M sur un clavier AZERTY) : toutes les lettres sont deja prises
 * dans le profil du joueur -- 446 mods --, et ; ne l'est pas. Reglable dans les commandes. Elle
 * ne fait que demander : le serveur sort la planche ou la range (JetBoard.toggle).
 */
public final class JetBoardClient {

    public static final KeyMapping KEY = new KeyMapping(JetBoard.KEY_NAME, KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SEMICOLON, "key.categories.emeraldweapons");

    private JetBoardClient() {
    }

    static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        @Nullable
        private JetBoardItemRenderer renderer;

        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (this.renderer == null) {
                this.renderer = new JetBoardItemRenderer();
            }
            return this.renderer;
        }
    };

    @EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            while (KEY.consumeClick()) {
                if (mc.screen == null && mc.player != null && mc.level != null && Haven.is(mc.level)) {
                    PacketDistributor.sendToServer(JetBoardTogglePayload.INSTANCE);
                }
            }
        }
    }

    /** Evenements du bus du mod. BUS = MOD EXPLICITE (voir MorphGunClient.Setup). */
    @EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(KEY);
        }

        @SubscribeEvent
        public static void onRegisterExtensions(RegisterClientExtensionsEvent event) {
            event.registerItem(EXTENSIONS, ModItems.JET_BOARD.get());
        }
    }
}
