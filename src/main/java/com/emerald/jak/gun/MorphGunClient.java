package com.emerald.jak.gun;

import com.emerald.haven.Haven;
import com.emerald.item.ModItems;
import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
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
 * Le cote client du Morph Gun : la croix de Jak 3 sur les fleches, et le rendu en main.
 *
 * LES TOUCHES NE CHANGENT RIEN ELLES-MEMES. Une fleche envoie la famille ; le
 * serveur revalide et reecrit le composant, que la synchronisation de la pile
 * renvoie a tous. Haut rouge, bas jaune, gauche bleu, droite sombre, dans leur
 * propre categorie, reassignables. ATTENTION : Mahou Tsukai lie aussi haut et
 * bas (gunUp, gunDown) dans le profil du joueur, et NeoForge declenche toutes
 * les liaisons actives d'une touche (KeyMappingLookup.findKeybinds) : les deux
 * reagiront, un contexte de touche n'y peut rien.
 *
 * LE RENDU EN MAIN (IClientItemExtensions) :
 *  - getCustomRenderer : MorphGunItemRenderer, le modele cuit de Jak 3 ;
 *  - getArmPose : CROSSBOW_HOLD en main droite, la tenue a deux mains ;
 *  - applyForgeHandTransform : en premiere personne, le point de la main
 *    d'ItemInHandRenderer (0,56 ; -0,52 ; -0,72), sans le balancement d'attaque
 *    -- une arme ne se brandit pas comme une epee.
 */
public final class MorphGunClient {

    public static final String CATEGORY = "key.categories.emeraldweapons.morph_gun";

    /** Dans l'ordre de GunForm.Family : rouge, jaune, bleu, sombre. */
    public static final KeyMapping[] KEYS = {
            key("red", GLFW.GLFW_KEY_UP),
            key("yellow", GLFW.GLFW_KEY_DOWN),
            key("blue", GLFW.GLFW_KEY_LEFT),
            key("dark", GLFW.GLFW_KEY_RIGHT)
    };

    private MorphGunClient() {
    }

    private static KeyMapping key(String family, int code) {
        return new KeyMapping("key.emeraldweapons.morph_gun." + family, KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, code, CATEGORY);
    }

    static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        @Nullable
        private MorphGunItemRenderer renderer;

        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (this.renderer == null) {
                this.renderer = new MorphGunItemRenderer();
            }
            return this.renderer;
        }

        @Override
        @Nullable
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            return hand == InteractionHand.MAIN_HAND ? HumanoidModel.ArmPose.CROSSBOW_HOLD : null;
        }

        @Override
        public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm,
                                               ItemStack stack, float partialTick, float equipProcess,
                                               float swingProcess) {
            int side = arm == HumanoidArm.RIGHT ? 1 : -1;
            poseStack.translate(side * GunHold.FIRST_X, GunHold.FIRST_Y + equipProcess * GunHold.FIRST_EQUIP_DROP,
                    GunHold.FIRST_Z);
            return true;
        }
    };

    @EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            for (int family = 0; family < KEYS.length; family++) {
                while (KEYS[family].consumeClick()) {
                    if (mc.screen == null && mc.player != null && mc.level != null && Haven.is(mc.level)
                            && mc.player.getMainHandItem().is(ModItems.MORPH_GUN.get())) {
                        PacketDistributor.sendToServer(new GunSelectPayload(family));
                    }
                }
            }
        }
    }

    /**
     * Evenements du bus du mod (IModBusEvent). BUS = MOD EXPLICITE : 21.1.193 (dev) les
     * range seul d'apres leur type, 21.1.174 (profil du joueur) les refuse au demarrage
     * sur le bus du jeu (cahier §33).
     */
    @EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            for (KeyMapping key : KEYS) {
                event.register(key);
            }
        }

        @SubscribeEvent
        public static void onRegisterExtensions(RegisterClientExtensionsEvent event) {
            event.registerItem(EXTENSIONS, ModItems.MORPH_GUN.get());
        }
    }
}
