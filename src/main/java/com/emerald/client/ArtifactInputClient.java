package com.emerald.client;

import com.emerald.artifact.Artifact;
import com.emerald.artifact.Artifacts;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.network.ArtifactActionPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.glfw.GLFW;

/**
 * Detecte les activations d'artefact cote client.
 *
 * Ni la touche de saut pressee en l'air ni une touche personnalisee ne
 * remontent au serveur : ce relais est donc indispensable. Il ne fait
 * qu'exprimer une intention -- le serveur revalide tout.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public class ArtifactInputClient {

    public static final KeyMapping RETURN_KEY = new KeyMapping(
            "key.emeraldweapons.artifact_return",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.emeraldweapons");

    /** Vrai tant que le second saut n'a pas ete consomme depuis le dernier appui au sol. */
    private static boolean jumpAvailable = true;
    private static boolean jumpWasDown;

    @EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT,
            bus = EventBusSubscriber.Bus.MOD)
    public static class Setup {
        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(RETURN_KEY);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null) {
            return;
        }

        while (RETURN_KEY.consumeClick()) {
            if (Artifacts.wearing(player, Artifact.BOTTES_DE_RETOUR)) {
                PacketDistributor.sendToServer(new ArtifactActionPayload(
                        ArtifactActionPayload.Action.RETURN));
            }
        }

        boolean jumpDown = mc.options.keyJump.isDown();
        // en vol creatif, la touche de saut sert deja a monter : s'y greffer
        // rendrait le double saut invisible et le vol saccade
        if (player.getAbilities().flying) {
            jumpWasDown = jumpDown;
            return;
        }
        if (player.onGround() || player.isInWater() || player.onClimbable()) {
            jumpAvailable = true;
        } else if (jumpDown && !jumpWasDown && jumpAvailable
                && Artifacts.wearing(player, Artifact.BOTTES_D_ECLAIR)) {
            // un seul saut supplementaire par passage en l'air : sans ce verrou,
            // maintenir la touche ferait voler
            jumpAvailable = false;
            PacketDistributor.sendToServer(new ArtifactActionPayload(
                    ArtifactActionPayload.Action.DOUBLE_JUMP));
        }
        jumpWasDown = jumpDown;
    }
}
