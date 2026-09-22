package com.emerald.client;

import com.emerald.haven.quest.HavenHero;
import com.emerald.haven.quest.HavenNpcEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Les heros de Haven dessines « sur un corps de villageois » (choix du joueur, §71) : le corps
 * du villageois, la peau d'une region et la tenue d'un metier qui dit le role -- le Pecheur a
 * son chapeau de pecheur, Sig le bandeau de l'armurier, Keira le tablier de l'outilleur, Samos
 * la tenue du bibliothecaire (le sage), Tess la robe violette, Torn le tablier du forgeron
 * d'armures. Les vraies silhouettes de Jak 3 pourront venir plus tard (rendus montres avant).
 */
public class HavenNpcRenderer extends MobRenderer<HavenNpcEntity, VillagerModel<HavenNpcEntity>> {

    private static final ResourceLocation BASE = ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png");

    public HavenNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
        this.addLayer(new Outfit(this));
    }

    @Override
    public ResourceLocation getTextureLocation(HavenNpcEntity entity) {
        return BASE;
    }

    @Override
    protected void scale(HavenNpcEntity entity, PoseStack pose, float partial) {
        pose.scale(0.9375F, 0.9375F, 0.9375F);
    }

    /** La peau de la region, puis la tenue du metier, par-dessus le corps. */
    private static final class Outfit extends RenderLayer<HavenNpcEntity, VillagerModel<HavenNpcEntity>> {

        Outfit(RenderLayerParent<HavenNpcEntity, VillagerModel<HavenNpcEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, HavenNpcEntity entity, float limbSwing,
                           float limbSwingAmount, float partial, float age, float headYaw, float headPitch) {
            if (entity.isInvisible()) {
                return;
            }
            HavenHero hero = entity.hero();
            renderColoredCutoutModel(this.getParentModel(), type(hero), pose, buffers, light, entity, -1);
            renderColoredCutoutModel(this.getParentModel(), profession(hero), pose, buffers, light, entity, -1);
        }

        private static ResourceLocation type(HavenHero hero) {
            String type = switch (hero) {
                case TORN -> "snow";
                case TESS -> "plains";
                case SIG -> "desert";
                case KEIRA -> "taiga";
                case SAMOS -> "swamp";
                case PECHEUR -> "savanna";
            };
            return ResourceLocation.withDefaultNamespace("textures/entity/villager/type/" + type + ".png");
        }

        private static ResourceLocation profession(HavenHero hero) {
            String job = switch (hero) {
                case TORN -> "armorer";
                case TESS -> "cleric";
                case SIG -> "weaponsmith";
                case KEIRA -> "toolsmith";
                case SAMOS -> "librarian";
                case PECHEUR -> "fisherman";
            };
            return ResourceLocation.withDefaultNamespace("textures/entity/villager/profession/" + job + ".png");
        }
    }
}
