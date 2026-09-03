package com.emerald.client;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Prepare et greffe {@link ArcenciumArmorLayer}.
 *
 * Les deux modeles declares ici sont des coques LEGEREMENT PLUS LARGES que
 * l'armure : 1,15 contre 1,0 pour la couche exterieure, 0,65 contre 0,5 pour
 * l'interieure. Sans ce decalage, la coque lumineuse partage exactement la
 * geometrie de l'armure et perd le test de profondeur des qu'on tourne autour
 * du personnage -- les fissures ne s'allument alors que sous certains angles.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public class ArcenciumArmorClient {

    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "arcencium_glow");

    public static final ModelLayerLocation GLOW_OUTER = new ModelLayerLocation(MODEL, "outer");
    public static final ModelLayerLocation GLOW_INNER = new ModelLayerLocation(MODEL, "inner");
    /**
     * La coque de l'aura d'amelioration : un seul maillage, gonfle bien
     * au-dela de l'armure.
     *
     * Elle doit deborder de TOUTES les pieces -- l'armure exterieure est
     * gonflee d'un, les jambieres d'un demi -- pour que son bord ressorte
     * autour de la silhouette. C'est ce bord, et non sa face, qui fait
     * l'aura : vu de front, la coque ne teinte l'armure que faiblement ; vu a
     * la silhouette, on voit ses flancs, hors du corps, contre le decor.
     */
    public static final ModelLayerLocation AURA_SHELL = new ModelLayerLocation(MODEL, "aura");
    /**
     * LES TROIS COQUES DU LISERE. L'armure exterieure est gonflee d'un ; le
     * lisere ne montre que ce qui DEBORDE d'elle (voir ModRenderTypes.rim).
     * Le debord est donc la difference : un tiers, deux tiers, un et demi
     * -- un trait fin, un trait moyen, un voile large.
     */
    public static final ModelLayerLocation RIM_THIN = new ModelLayerLocation(MODEL, "rim_thin");
    public static final ModelLayerLocation RIM_MID = new ModelLayerLocation(MODEL, "rim_mid");
    public static final ModelLayerLocation RIM_WIDE = new ModelLayerLocation(MODEL, "rim_wide");

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(GLOW_OUTER, () -> LayerDefinition.create(
                HumanoidModel.createMesh(new CubeDeformation(1.15F), 0.0F), 64, 32));
        event.registerLayerDefinition(GLOW_INNER, () -> LayerDefinition.create(
                HumanoidModel.createMesh(new CubeDeformation(0.65F), 0.0F), 64, 32));
        event.registerLayerDefinition(AURA_SHELL, () -> LayerDefinition.create(
                HumanoidModel.createMesh(new CubeDeformation(1.75F), 0.0F), 64, 32));
        event.registerLayerDefinition(RIM_THIN, () -> LayerDefinition.create(
                HumanoidModel.createMesh(new CubeDeformation(1.35F), 0.0F), 64, 32));
        event.registerLayerDefinition(RIM_MID, () -> LayerDefinition.create(
                HumanoidModel.createMesh(new CubeDeformation(1.7F), 0.0F), 64, 32));
        event.registerLayerDefinition(RIM_WIDE, () -> LayerDefinition.create(
                HumanoidModel.createMesh(new CubeDeformation(2.5F), 0.0F), 64, 32));
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        HumanoidModel<AbstractClientPlayer> inner =
                new HumanoidModel<>(event.getEntityModels().bakeLayer(GLOW_INNER));
        HumanoidModel<AbstractClientPlayer> outer =
                new HumanoidModel<>(event.getEntityModels().bakeLayer(GLOW_OUTER));
        @SuppressWarnings("unchecked")
        HumanoidModel<AbstractClientPlayer>[] rims = new HumanoidModel[]{
                new HumanoidModel<>(event.getEntityModels().bakeLayer(RIM_THIN)),
                new HumanoidModel<>(event.getEntityModels().bakeLayer(RIM_MID)),
                new HumanoidModel<>(event.getEntityModels().bakeLayer(RIM_WIDE))};

        for (var skin : event.getSkins()) {
            LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer =
                    event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new ArcenciumArmorLayer(renderer, inner, outer));
                // L'AURA D'AMELIORATION a SA coque, plus large que tout. J'avais
                // d'abord reutilise les maillages des fissures, poses sur la piece :
                // en additif a cinquante-cinq pour cent, du blanc effacait l'armure
                // entiere. Une amelioration ajoute, elle ne remplace pas.
                renderer.addLayer(new UpgradeArmorLayer<>(renderer, rims));
                renderer.addLayer(new UpgradeHandLayer<>(renderer));
                // les ailes de specialisation, dans le dos
                renderer.addLayer(new WingsLayer<>(renderer));
            }
        }
        armMonsters(event);
    }

    /**
     * Le halo d'amelioration sur TOUT ce qui tient une arme, pas seulement le joueur.
     *
     * Il y avait auparavant une helice de particules pour signaler les
     * creatures armees ; elle etait fausse par construction -- le serveur ne
     * peut pas viser une main qui n'existe que dans le rendu -- et elle a
     * disparu. C'est ce calque qui la remplace, et il fait bien mieux : la
     * lueur epouse l'arme au lieu de flotter a cote.
     *
     * ON NE CITE AUCUNE CREATURE PAR SON NOM. On parcourt les rendus et l'on
     * greffe le calque partout ou le modele sait ou est la main -- c'est ce que
     * signifie ArmedModel. Les creatures du modpack en profitent donc sans
     * qu'on ait a les recenser, et une liste de noms serait perimee des le
     * premier mod ajoute.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void armMonsters(EntityRenderersEvent.AddLayers event) {
        for (net.minecraft.world.entity.EntityType<?> type : event.getEntityTypes()) {
            net.minecraft.client.renderer.entity.EntityRenderer<?> renderer;
            try {
                renderer = event.getRenderer((net.minecraft.world.entity.EntityType) type);
            } catch (RuntimeException ignored) {
                continue;               // un type sans rendu vivant : on passe
            }
            if (renderer instanceof LivingEntityRenderer<?, ?> living
                    && living.getModel() instanceof net.minecraft.client.model.ArmedModel) {
                ((LivingEntityRenderer) living).addLayer(
                        new UpgradeHandLayer((RenderLayerParent) living));
            }
        }
    }
}
