package com.emerald.jak.vehicle;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.network.VehicleModePayload;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;

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
 *
 * L'EQUILIBRE (VehicleAttitude) : ce client publie celui de la voiture qu'il conduit
 * ({@link #sendAttitude}), recoit le souffle des explosions qui la touchent
 * ({@link #acceptImpulse}), et dessine les passagers penches avec leur vehicule -- une
 * moto couchee a 40 degres sous un pilote reste droit, sinon.
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

    /** Envoie au serveur l'equilibre de la voiture que ce client conduit (JakVehicleEntity.publishAttitude). */
    static void sendAttitude(float pitch, float roll) {
        PacketDistributor.sendToServer(new VehicleAttitudePayload(pitch, roll));
    }

    /** Le souffle d'une explosion sur la voiture que ce client conduit : il l'applique lui-meme. */
    public static void acceptImpulse(VehicleImpulsePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getEntity(payload.vehicle()) instanceof JakVehicleEntity car
                && car.isControlledByLocalInstance()) {
            car.applyImpulse(payload.impulse(), payload.at());
        }
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

        /**
         * Les passagers dessines penches, par identifiant : une pile, car un dessin peut en
         * contenir un autre. Seuls ceux-la sont empiles, et Post ne depile que le sien : un
         * dessin annule apres nous (sans Post) ne desaccorde rien.
         */
        private static final Deque<Integer> TILTED = new ArrayDeque<>();

        /**
         * Le passager penche avec son vehicule : la meme inclinaison que la carrosserie
         * (JakVehicleRenderer), autour du meme centre de masse, conjuguee par le lacet du
         * vehicule. Priorite la plus basse : si un autre mod annule le dessin, nous ne
         * poussons rien que Post ne defasse.
         */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onRiderPre(RenderLivingEvent.Pre<?, ?> event) {
            LivingEntity rider = event.getEntity();
            if (!(rider.getVehicle() instanceof JakVehicleEntity car)) {
                return;
            }
            float partial = event.getPartialTick();
            float pitch = car.pitch(partial);
            float roll = car.roll(partial);
            if (Math.abs(pitch) < 0.05F && Math.abs(roll) < 0.05F) {
                return;
            }
            float yaw = car.getViewYRot(partial);
            double yawRad = Math.toRadians(yaw);
            double cmZ = car.spec().balance.cmZ();
            Vec3 pivot = car.getPosition(partial).add(-Math.sin(yawRad) * cmZ, 0.0, Math.cos(yawRad) * cmZ)
                    .subtract(rider.getPosition(partial));
            PoseStack pose = event.getPoseStack();
            pose.pushPose();
            pose.translate(pivot.x, pivot.y, pivot.z);
            pose.mulPose(Axis.YP.rotationDegrees(-yaw));
            JakVehicleRenderer.PivotTilt.apply(pose, 0.0, pitch, roll);
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            pose.translate(-pivot.x, -pivot.y, -pivot.z);
            TILTED.push(rider.getId());
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onRiderPost(RenderLivingEvent.Post<?, ?> event) {
            Integer top = TILTED.peek();
            if (top != null && top == event.getEntity().getId()) {
                TILTED.pop();
                event.getPoseStack().popPose();
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
