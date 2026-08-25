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
}
