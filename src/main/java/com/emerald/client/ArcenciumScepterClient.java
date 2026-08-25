package com.emerald.client;

import com.emerald.item.ModItems;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.network.ScepterFirePayload;
import com.emerald.weapons.ArcenciumScepterItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Cote client du Sceptre d'Arcencium.
 *
 * Deux roles. D'une part relayer le clic gauche au serveur : un clic dans le
 * vide ne genere aucun paquet vanilla, donc sans cela le sceptre ne tirerait
 * qu'en visant une entite. D'autre part exposer le predicat « charge », qui
 * permet au modele de choisir la texture correspondant au nombre d'eclats
 * allumes -- la jauge de rechargement se lit ainsi dans la main du joueur.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public class ArcenciumScepterClient {

    @EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT,
            bus = EventBusSubscriber.Bus.MOD)
    public static class Setup {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> ItemProperties.register(
                    ModItems.ARCENCIUM_SCEPTER.get(),
                    ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "charge"),
                    (stack, level, entity, seed) -> {
                        if (entity == null) {
                            return 1.0F;
                        }
                        // getCooldowns().getCooldownPercent rend 1 juste apres
                        // l'usage et 0 quand c'est pret : on inverse pour que
                        // la valeur croisse avec le nombre d'eclats allumes
                        float remaining = entity instanceof net.minecraft.world.entity.player.Player p
                                ? p.getCooldowns().getCooldownPercent(stack.getItem(), 0.0F)
                                : 0.0F;
                        return 1.0F - remaining;
                    }));
        }
    }

    /**
     * Le clic gauche declenche le tir. On filtre ici sur l'objet tenu pour ne
     * pas inonder le serveur d'un paquet a chaque coup d'epee.
     */
    @SubscribeEvent
    public static void onAttackInput(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (!(mc.player.getItemInHand(InteractionHand.MAIN_HAND).getItem()
                instanceof ArcenciumScepterItem)) {
            return;
        }
        PacketDistributor.sendToServer(new ScepterFirePayload());
        mc.player.swing(InteractionHand.MAIN_HAND);
        event.setSwingHand(false);
        event.setCanceled(true);
    }
}
