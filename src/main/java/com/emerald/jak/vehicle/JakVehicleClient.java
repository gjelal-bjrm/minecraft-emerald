package com.emerald.jak.vehicle;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.network.VehicleModePayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Le cote client des voitures : la touche de zone de survol, son indice a
 * l'ecran, et la camera.
 *
 * LA TOUCHE NE BASCULE RIEN. Elle demande au serveur, qui verifie que le
 * joueur conduit, bascule, et renvoie le mode a tous par la donnee d'entite.
 *
 * ESPACE PAR DEFAUT, ACTIVE AU VOLANT SEULEMENT. Le jeu change de zone sur R2.
 * R, retenu d'abord, est aussi la touche d'Iris qui recharge les shaders -- le
 * jeu figeait a chaque montee et chaque descente --, et celle de JEI, d'Iron's
 * Spellbooks et du retour d'artefact. Espace ne sert a rien d'autre dans une
 * voiture (on en descend avec Maj). Le contexte AT_THE_WHEEL n'est actif qu'au
 * volant : a pied, Espace ne fait que sauter (KeyMappingLookup ne clique que
 * les touches actives), et le menu des touches ne signale pas de conflit avec
 * le saut. L'identifiant a change (hover_zone, puis vehicle_zone) : options.txt
 * garde la touche par identifiant, et R y serait restee. Reglable dans les touches.
 *
 * L'INDICE reprend le texte du jeu (#x0147, « changer de zone de survol ») et
 * dit dans quelle zone on vole : on ne voit pas toujours de la voiture si l'on
 * est au ras du sol ou sur la voie.
 *
 * LA CAMERA recule a neuf blocs en vue a la troisieme personne : a quatre, la
 * distance ordinaire, elle se retrouvait dans une voiture de huit.
 */
public final class JakVehicleClient {

    /** Au volant d'une voiture ou d'une moto, sans ecran ouvert. */
    private static final IKeyConflictContext AT_THE_WHEEL = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            return KeyConflictContext.IN_GAME.isActive() && driving(Minecraft.getInstance());
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return this == other;
        }
    };

    public static final KeyMapping HOVER_KEY = new KeyMapping(
            "key.emeraldweapons.vehicle_zone",
            AT_THE_WHEEL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_SPACE,
            "key.categories.emeraldweapons");

    public static final float CAMERA_DISTANCE = 9.0F;

    private JakVehicleClient() {
    }

    private static boolean driving(Minecraft mc) {
        return mc.player != null && mc.player.getVehicle() instanceof JakVehicleEntity car
                && car.getControllingPassenger() == mc.player;
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen != null || !driving(mc)
                || !(mc.player.getVehicle() instanceof JakVehicleEntity car)) {
            return;
        }
        Component zone = Component.translatable(VehicleDynamics.isHigh(car.mode())
                ? "hud.emeraldweapons.vehicle.zone_high" : "hud.emeraldweapons.vehicle.zone_low");
        Component hint = Component.translatable("hud.emeraldweapons.vehicle.hover_hint",
                HOVER_KEY.getTranslatedKeyMessage(), zone);
        int width = mc.font.width(hint);
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() - 80;
        graphics.fill(x - 3, y - 2, x + width + 3, y + 10, 0x80000000);
        graphics.drawString(mc.font, hint, x, y, 0xFFE8F4FF, true);
    }

    @EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            while (HOVER_KEY.consumeClick()) {
                if (mc.screen == null && driving(mc)) {
                    PacketDistributor.sendToServer(VehicleModePayload.INSTANCE);
                }
            }
        }

        @SubscribeEvent
        public static void onCameraDistance(CalculateDetachedCameraDistanceEvent event) {
            Entity viewer = event.getCamera().getEntity();
            if (viewer != null && viewer.getVehicle() instanceof JakVehicleEntity) {
                event.setDistance(Math.max(event.getDistance(), CAMERA_DISTANCE));
            }
        }
    }

    /**
     * BUS = MOD EXPLICITE. NeoForge 21.1.193 (dev) range seul un ecouteur d'apres son
     * evenement et marque cette valeur pour suppression ; 21.1.174, celui du profil
     * CurseForge du joueur, refuse au demarrage un IModBusEvent ecoute sur le bus du
     * jeu (ArcenciumBowClient, cahier §33). Ces deux-ci en sont.
     */
    @EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        public static void onParticles(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
            VehicleParticles.register(event);
        }

        @SubscribeEvent
        public static void onRegisterLayers(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.HOTBAR,
                    ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "vehicle_hint"),
                    (LayeredDraw.Layer) JakVehicleClient::render);
        }

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(HOVER_KEY);
        }
    }
}
