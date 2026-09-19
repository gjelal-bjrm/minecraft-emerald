package com.emerald.jak.gun;

import com.emerald.haven.Haven;
import com.emerald.init.Jak3Registry;
import com.emerald.item.ModItems;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.particles.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Le tir du Morph Gun, cote client : la gachette, et les traces des tirs instantanes.
 *
 * LA GACHETTE. Le clic gauche tenu ne rappelle l'evenement d'entree que si l'on
 * vise un bloc (Minecraft.continueAttack) : on lit donc keyAttack.isDown() a
 * chaque tique, arme en main dans Haven, sans ecran ouvert, hors vehicule. Un
 * GunTriggerPayload part a chaque changement, et « tenue » est redit toutes les
 * {@value GunSpec#TRIGGER_KEEPALIVE} tiques ; l'ouverture d'un ecran relache. Le
 * coup de poing vanilla est annule (comme ArcenciumScepterClient).
 *
 * LES TRACES (GunTracePayload) partent de la bouche approchee du canon : en
 * premiere personne, devant et a droite de la camera ; sinon, devant le joueur.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class GunClient {

    private static boolean sent;
    private static int since;

    private GunClient() {
    }

    /** L'arme de Haven en main du joueur local. */
    static boolean holding(Minecraft mc) {
        LocalPlayer player = mc.player;
        return player != null && mc.level != null && Haven.is(mc.level) && player.isAlive() && !player.isSpectator()
                && player.getMainHandItem().is(ModItems.MORPH_GUN.get());
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.player == null) {
            sent = false;
            since = 0;
            return;
        }
        boolean want = holding(mc) && !mc.player.isPassenger() && mc.screen == null && mc.getOverlay() == null
                && mc.options.keyAttack.isDown();
        since++;
        if (want != sent || (want && since >= GunSpec.TRIGGER_KEEPALIVE)) {
            PacketDistributor.sendToServer(new GunTriggerPayload(want));
            sent = want;
            since = 0;
        }
    }

    @SubscribeEvent
    public static void onInput(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack() && holding(Minecraft.getInstance())) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    /** La bouche approchee du canon d'un joueur, au temps partiel donne. */
    public static Vec3 muzzle(Player player, float partial) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 eye = player.getEyePosition(partial);
        Vec3 look = player.getViewVector(partial);
        Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
        right = right.lengthSqr() < 1.0e-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        Vec3 up = right.cross(look).normalize();
        boolean firstPerson = player == mc.player && mc.options.getCameraType().isFirstPerson();
        return firstPerson
                ? eye.add(look.scale(0.9)).add(right.scale(0.22)).add(up.scale(-0.2))
                : eye.add(look.scale(1.1)).add(right.scale(0.3)).add(up.scale(-0.35));
    }

    /** Les traces d'une salve, recues du serveur. */
    public static void accept(GunTracePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        Entity shooter = level.getEntity(payload.shooter());
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        Vec3 from = shooter instanceof Player player ? muzzle(player, partial)
                : new Vec3(payload.ox(), payload.oy(), payload.oz());
        RandomSource random = level.random;
        if (payload.weapon() == GunTracePayload.REFLEXOR) {
            reflexor(level, payload, shooter instanceof Player ? from : new Vec3(payload.ox(), payload.oy(), payload.oz()));
            return;
        }
        if (payload.weapon() == GunTracePayload.GYRO) {
            gyro(level, payload);
            return;
        }
        if (payload.weapon() == GunTracePayload.ARC) {
            arc(level, payload, shooter instanceof Player ? from : new Vec3(payload.ox(), payload.oy(), payload.oz()));
            return;
        }
        if (payload.weapon() == GunTracePayload.NEEDLES) {
            needles(level, payload);
            return;
        }
        boolean scatter = payload.weapon() == GunTracePayload.SCATTER;
        if (scatter) {
            level.addParticle(ModParticles.GUN_SCATTER_FLASH.get(), true, from.x, from.y, from.z, 0.0, 0.0, 0.0);
        }
        float[] ends = payload.ends();
        for (int i = 0; i < payload.count(); i++) {
            Vec3 end = new Vec3(ends[i * 4], ends[i * 4 + 1], ends[i * 4 + 2]);
            int flag = (int) ends[i * 4 + 3];
            Vec3 d = end.subtract(from);
            double length = d.length();
            if (scatter) {
                level.addParticle(ModParticles.GUN_SCATTER_PELLET.get(), true, from.x, from.y, from.z,
                        d.x / 3.0, d.y / 3.0, d.z / 3.0);
                int dots = Math.min(10, (int) (length / 1.5));
                for (int k = 1; k <= dots; k++) {
                    double t = k / (double) (dots + 1);
                    level.addParticle(ModParticles.GUN_SCATTER_PELLET.get(), true, from.x + d.x * t, from.y + d.y * t,
                            from.z + d.z * t, 0.0, 0.0, 0.0);
                }
                if (flag != GunTracePayload.MISS) {
                    for (int k = 0; k < 3; k++) {
                        level.addParticle(ModParticles.GUN_SCATTER_PELLET.get(), true, end.x, end.y, end.z,
                                (random.nextDouble() - 0.5) * 0.12, random.nextDouble() * 0.1, (random.nextDouble() - 0.5) * 0.12);
                    }
                }
            } else {
                level.addParticle(ModParticles.GUN_VULCAN_TRACER.get(), true, from.x, from.y, from.z,
                        d.x / 2.0, d.y / 2.0, d.z / 2.0);
                int dots = Math.min(160, (int) (length / 0.5));
                for (int k = 1; k <= dots; k++) {
                    double t = k / (double) (dots + 1);
                    level.addParticle(ModParticles.GUN_VULCAN_TRACER.get(), true, from.x + d.x * t, from.y + d.y * t,
                            from.z + d.z * t, 0.0, 0.0, 0.0);
                }
                if (flag != GunTracePayload.MISS) {
                    for (int k = 0; k < 4; k++) {
                        level.addParticle(ModParticles.GUN_VULCAN_SPARK.get(), true, end.x, end.y, end.z,
                                (random.nextDouble() - 0.5) * 0.3, random.nextDouble() * 0.2, (random.nextDouble() - 0.5) * 0.3);
                    }
                }
            }
        }
    }

    /** Le chemin du Beam Reflexor pendant une tique : une ligne brisee, un eclat a chaque rebond. */
    private static void reflexor(ClientLevel level, GunTracePayload payload, Vec3 from) {
        RandomSource random = level.random;
        float[] ends = payload.ends();
        Vec3 at = from;
        for (int i = 0; i < payload.count(); i++) {
            Vec3 end = new Vec3(ends[i * 4], ends[i * 4 + 1], ends[i * 4 + 2]);
            Vec3 d = end.subtract(at);
            int dots = Math.min(64, Math.max(1, (int) (d.length() * 4.0)));
            for (int k = 0; k < dots; k++) {
                double t = k / (double) dots;
                level.addParticle(ModParticles.GUN_REFLEXOR_BOLT.get(), true, at.x + d.x * t, at.y + d.y * t, at.z + d.z * t,
                        0.0, 0.0, 0.0);
            }
            if ((int) ends[i * 4 + 3] != GunTracePayload.MISS) {
                for (int k = 0; k < 6; k++) {
                    level.addParticle(ModParticles.GUN_REFLEXOR_SPARK.get(), true, end.x, end.y, end.z,
                            (random.nextDouble() - 0.5) * 0.3, random.nextDouble() * 0.2, (random.nextDouble() - 0.5) * 0.3);
                }
            }
            at = end;
        }
    }

    /** La corde de foudre de l'Arc Wielder pendant une tique : une ligne brisee qui tremble, un eclat a chaque monstre accroche. */
    private static void arc(ClientLevel level, GunTracePayload payload, Vec3 from) {
        RandomSource random = level.random;
        float[] ends = payload.ends();
        Vec3 at = from;
        for (int i = 0; i < payload.count(); i++) {
            Vec3 end = new Vec3(ends[i * 4], ends[i * 4 + 1], ends[i * 4 + 2]);
            Vec3 d = end.subtract(at);
            int dots = Math.min(40, Math.max(1, (int) (d.length() * 4.0)));
            for (int k = 0; k < dots; k++) {
                double t = k / (double) dots;
                // la foudre tremble : un ecart au hasard, nul aux deux bouts du segment
                double shake = 0.4 * Math.sin(Math.PI * t);
                level.addParticle(ModParticles.GUN_ARC_BOLT.get(), true,
                        at.x + d.x * t + (random.nextDouble() - 0.5) * shake, at.y + d.y * t + (random.nextDouble() - 0.5) * shake,
                        at.z + d.z * t + (random.nextDouble() - 0.5) * shake, 0.0, 0.0, 0.0);
            }
            if ((int) ends[i * 4 + 3] != GunTracePayload.MISS) {
                for (int k = 0; k < 3; k++) {
                    level.addParticle(ModParticles.GUN_ARC_SPARK.get(), true, end.x, end.y, end.z,
                            (random.nextDouble() - 0.5) * 0.3, random.nextDouble() * 0.2, (random.nextDouble() - 0.5) * 0.3);
                }
            }
            at = end;
        }
    }

    /** Les aiguilles du Needle Lazer : des paires debut-fin, le chemin de chacune pendant la tique. */
    private static void needles(ClientLevel level, GunTracePayload payload) {
        RandomSource random = level.random;
        float[] ends = payload.ends();
        for (int i = 0; i + 1 < payload.count(); i += 2) {
            Vec3 from = new Vec3(ends[i * 4], ends[i * 4 + 1], ends[i * 4 + 2]);
            Vec3 to = new Vec3(ends[(i + 1) * 4], ends[(i + 1) * 4 + 1], ends[(i + 1) * 4 + 2]);
            Vec3 d = to.subtract(from);
            int dots = Math.min(24, Math.max(1, (int) (d.length() * 4.0)));
            for (int k = 0; k < dots; k++) {
                double t = k / (double) dots;
                level.addParticle(ModParticles.GUN_NEEDLE_TRAIL.get(), true, from.x + d.x * t, from.y + d.y * t,
                        from.z + d.z * t, 0.0, 0.0, 0.0);
            }
            if ((int) ends[(i + 1) * 4 + 3] != GunTracePayload.MISS) {
                for (int k = 0; k < 3; k++) {
                    level.addParticle(ModParticles.GUN_NEEDLE_SPARK.get(), true, to.x, to.y, to.z,
                            (random.nextDouble() - 0.5) * 0.25, random.nextDouble() * 0.15, (random.nextDouble() - 0.5) * 0.25);
                }
            }
        }
    }

    /** La salve de la soucoupe du Gyro Burster : un trait par tir, de la soucoupe a sa fin. */
    private static void gyro(ClientLevel level, GunTracePayload payload) {
        RandomSource random = level.random;
        Vec3 from = new Vec3(payload.ox(), payload.oy(), payload.oz());
        float[] ends = payload.ends();
        for (int i = 0; i < payload.count(); i++) {
            Vec3 end = new Vec3(ends[i * 4], ends[i * 4 + 1], ends[i * 4 + 2]);
            Vec3 d = end.subtract(from);
            level.addParticle(ModParticles.GUN_GYRO_TRACER.get(), true, from.x, from.y, from.z, d.x / 2.0, d.y / 2.0, d.z / 2.0);
            int dots = Math.min(72, (int) (d.length() / 0.5));
            for (int k = 1; k <= dots; k++) {
                double t = k / (double) (dots + 1);
                level.addParticle(ModParticles.GUN_GYRO_TRACER.get(), true, from.x + d.x * t, from.y + d.y * t,
                        from.z + d.z * t, 0.0, 0.0, 0.0);
            }
            if ((int) ends[i * 4 + 3] != GunTracePayload.MISS) {
                for (int k = 0; k < 3; k++) {
                    level.addParticle(ModParticles.GUN_GYRO_SPARK.get(), true, end.x, end.y, end.z,
                            (random.nextDouble() - 0.5) * 0.25, random.nextDouble() * 0.15, (random.nextDouble() - 0.5) * 0.25);
                }
            }
        }
    }

    /** Les enregistrements du bus du mod : particules, rendus d'entites, HUD. */
    @EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        public static void onParticles(RegisterParticleProvidersEvent event) {
            GunParticles.register(event);
        }

        @SubscribeEvent
        public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(Jak3Registry.GUN_BLASTER_SHOT.get(), NoopRenderer::new);
            event.registerEntityRenderer(Jak3Registry.GUN_PEACE_BALL.get(), GunRenderers.Ball::new);
            event.registerEntityRenderer(Jak3Registry.GUN_ARC.get(), GunRenderers.Arc::new);
            event.registerEntityRenderer(Jak3Registry.GUN_ECO.get(), GunRenderers.Eco::new);
            event.registerEntityRenderer(Jak3Registry.GUN_SHOCKWAVE.get(), GunRenderers.Shockwave::new);
            event.registerEntityRenderer(Jak3Registry.GUN_GRENADE.get(), GunRenderers.Grenade::new);
            event.registerEntityRenderer(Jak3Registry.GUN_SAUCER.get(), GunRenderers.Saucer::new);
            event.registerEntityRenderer(Jak3Registry.GUN_GRAVITY_FIELD.get(), GunRenderers.GravityField::new);
            event.registerEntityRenderer(Jak3Registry.GUN_NUKE.get(), GunRenderers.Nuke::new);
        }

        @SubscribeEvent
        public static void onLayers(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.AIR_LEVEL,
                    ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "morph_gun_hud"), GunHud::render);
            // l'eclair de la Super Nova couvre tout, HUD compris
            event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "super_nova_flash"),
                    GunNovaClient::render);
        }
    }
}
