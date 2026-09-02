package com.emerald.network;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.weapons.ArcenciumScepterItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ModNetwork {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(ScepterFirePayload.TYPE, ScepterFirePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        onFireRequest(player);
                    }
                }));

        registrar.playToClient(ProbeInfoPayload.TYPE, ProbeInfoPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.emerald.client.ProbeHudClient.accept(payload)));

        registrar.playToClient(GameSyncPayload.TYPE, GameSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.emerald.client.GameHudClient.accept(payload)));

        registrar.playToClient(WeatherSyncPayload.TYPE, WeatherSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.emerald.client.WeatherClient.accept(payload)));

        registrar.playToClient(WeatherPulsePayload.TYPE, WeatherPulsePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.emerald.client.WeatherAtmosphere.accept(payload)));

        registrar.playToClient(RiftSyncPayload.TYPE, RiftSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.emerald.client.RiftRenderer.accept(payload)));

        registrar.playToClient(FissureSyncPayload.TYPE, FissureSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.emerald.client.FissureRenderer.accept(payload)));

        registrar.playToClient(StormStrikePayload.TYPE, StormStrikePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.emerald.client.StormArcRenderer.accept(payload)));

        registrar.playToClient(WingsSyncPayload.TYPE, WingsSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.emerald.client.WingsClient.accept(payload)));

        registrar.playToClient(AnchorPulsePayload.TYPE, AnchorPulsePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.emerald.client.AnchorPulseRenderer.accept(payload)));

        registrar.playToClient(HeroSyncPayload.TYPE, HeroSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.emerald.client.HeroHudClient.accept(payload)));

        registrar.playToClient(DamagePopPayload.TYPE, DamagePopPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.emerald.client.DamagePopClient.accept(payload)));

        registrar.playToServer(HeroSpendPayload.TYPE, HeroSpendPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        onSpendRequest(player, payload);
                    }
                }));

        registrar.playToServer(ArtifactActionPayload.TYPE, ArtifactActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        switch (payload.action()) {
                            case DOUBLE_JUMP -> com.emerald.artifact.ArtifactActions.doubleJump(player);
                            case RETURN -> com.emerald.artifact.ArtifactActions.returnHome(player);
                        }
                    }
                }));
    }

    /**
     * Le serveur revalide tout : l'objet tenu, puis la cadence et la fatigue,
     * qui sont gerees par l'objet lui-meme. Un client modifie qui enverrait le
     * paquet en rafale n'obtiendrait rien de plus qu'un joueur normal.
     */
    private static void onFireRequest(ServerPlayer player) {
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (stack.getItem() instanceof ArcenciumScepterItem) {
            ArcenciumScepterItem.tryFire(player, stack);
        }
    }

    /**
     * Le serveur place les points, ou refuse.
     *
     * Il revalide TOUT : le numero de voie, la quantite, les points libres et
     * le plafond. La fiche repart ensuite dans tous les cas, y compris quand
     * rien n'a ete place -- c'est ce qui remet l'ecran d'accord avec la verite
     * apres un clic refuse.
     */
    private static void onSpendRequest(ServerPlayer player,
                                       com.emerald.network.HeroSpendPayload payload) {
        com.emerald.hero.HeroStat[] all = com.emerald.hero.HeroStat.values();
        int which = payload.stat();
        if (which < 0 || which >= all.length) {
            return;
        }
        int amount = Math.max(1, Math.min(com.emerald.hero.HeroStat.MAX_PATH,
                payload.amount()));
        if (com.emerald.hero.HeroLevel.spend(player, all[which], amount) > 0) {
            com.emerald.hero.HeroEvents.apply(player);
        }
        com.emerald.hero.HeroEvents.sync(player);
    }
}
