package com.emerald.item;

import com.emerald.tiers.EmeraldTier;
import com.emerald.weapons.ArcenciumBowItem;
import com.emerald.weapons.ArcenciumGlaiveItem;
import com.emerald.weapons.ArcenciumScepterItem;
import com.emerald.weapons.EmeraldWindblade;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(EmeraldWeaponsMod.MODID);

    public static final DeferredItem<Item> ARCENCIUM_INGOT = ITEMS.register("arcencium_ingot",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> RAW_ARCENCIUM = ITEMS.register("raw_arcencium",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<SwordItem> EMERALD_SWORD =
            ITEMS.register("emerald_sword", () ->
                    new EmeraldWindblade(
                            new EmeraldTier(),
                            new Item.Properties()
                                    // Le coup ORDINAIRE le plus lourd des quatre :
                                    // c'est ce que l'epeiste gagne en echange
                                    // d'un critique rare et faible.
                                    .attributes(SwordItem.createAttributes(new EmeraldTier(), 4.5F, -2.2F))
                    )
            );

    /** Arcencium Bow -- Tension Prismatique (voir ArcenciumBowItem). */
    public static final DeferredItem<ArcenciumBowItem> ARCENCIUM_BOW =
            ITEMS.register("arcencium_bow", () ->
                    new ArcenciumBowItem(new Item.Properties().durability(1500)));

    /** Sceptre d'Arcencium -- la Concorde (voir ArcenciumScepterItem). */
    public static final DeferredItem<ArcenciumScepterItem> ARCENCIUM_SCEPTER =
            ITEMS.register("arcencium_scepter", () ->
                    new ArcenciumScepterItem(new Item.Properties().durability(900)));

    /**
     * Glaive d'Arcencium -- la Rage (voir ArcenciumGlaiveItem).
     *
     * Plus lent que l'epee et moins tranchant a froid : -2,4 contre -2,2, et
     * deux points de degats de base en moins. Tout ce qui lui manque au repos,
     * la Rage le lui rend -- a condition de ne pas reculer.
     */
    public static final DeferredItem<ArcenciumGlaiveItem> ARCENCIUM_GLAIVE =
            ITEMS.register("arcencium_glaive", () ->
                    new ArcenciumGlaiveItem(new Item.Properties()
                            .durability(1250)
                            .rarity(net.minecraft.world.item.Rarity.EPIC)
                            .attributes(SwordItem.createAttributes(
                                    new EmeraldTier(), 2.0F, -2.4F))));

    /**
     * Eclat du Destin -- la matiere des tentatives de rarete.
     *
     * Il ne se fabrique pas : il tombe des monstres et dort dans les coffres,
     * ce qui en fait la seule monnaie du mode qu'on ne puisse pas produire a
     * volonte. Plus on en met dans l'etabli, plus la loi du tirage penche vers
     * le haut -- jamais assez pour promettre, toujours assez pour tenter.
     */
    public static final DeferredItem<Item> FATE_SHARD =
            ITEMS.register("fate_shard", () -> new Item(new Item.Properties()
                    .rarity(net.minecraft.world.item.Rarity.UNCOMMON)));

    /**
     * Sonde du Sanctuaire -- l'outil de designation.
     *
     * Elle n'a rien d'une arme et ne sert qu'a construire : tenue en main, elle
     * affiche ce que l'on vise -- le bloc, sa place dans le monde, le chantier
     * qui l'a pose et son adresse dans la structure -- et son clic droit
     * retient des blocs ou releve les corrections faites a la main.
     *
     * Elle existe parce que sept allers-retours ont ete perdus a corriger le
     * mauvais escalier, faute d'un langage commun entre ce qui se voit et ce
     * qui s'ecrit. Voir {@link SanctuaryProbeItem}.
     */
    public static final DeferredItem<SanctuaryProbeItem> SANCTUARY_PROBE =
            ITEMS.register("sanctuary_probe", () ->
                    new SanctuaryProbeItem(new Item.Properties().stacksTo(1)));

    /**
     * La Lame du Serment.
     *
     * Une Epee d'Emeraude ceremonielle, avec TOUS ses effets : Fureur
     * Cristalline, procs elementaires, foudre. C'est tout l'interet -- elle
     * montre des la premiere minute ce que le mode reserve, et donne envie de
     * la reconquerir apres sa dissolution.
     *
     * Un cran en dessous de la vraie lame en degats, et un seul emplacement
     * d'artefact lui est refuse : elle ne doit pas rendre la progression
     * inutile, seulement l'annoncer.
     */
    public static final DeferredItem<EmeraldWindblade> OATH_BLADE =
            ITEMS.register("oath_blade", () -> new EmeraldWindblade(new EmeraldTier(),
                    new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC)
                            .attributes(SwordItem.createAttributes(new EmeraldTier(), 3.0F, -2.2F))));

    /** Objet artefact : voir com.emerald.artifact.ArtifactItem. */
    /**
     * L'objet rune, tous types confondus.
     *
     * Un seul enregistrement pour les douze runes : ce qu'elle est, son rang et
     * la valeur qu'elle a tiree sont des composants de la pile. C'est le meme
     * choix que pour les artefacts, et pour la meme raison -- douze objets
     * enregistres demanderaient douze modeles et douze traductions de plus sans
     * rien apporter.
     */
    /**
     * Cristal elementaire -- ce avec quoi on accorde une arme.
     *
     * Il s'empile, contrairement aux runes et aux artefacts : accorder n'est
     * pas un objet unique qu'on serti mais une matiere qu'on consomme, et l'on
     * en ramasse beaucoup.
     */
    /**
     * Pierre de Forge -- ce qu'il faut EN PLUS du metal pour ameliorer.
     *
     * Le metal seul ne suffirait pas : il se ramasse en creusant, et un systeme
     * qu'on alimente en creusant recompense le temps passe plutot que le jeu
     * joue. La Pierre, elle, ne tombe que des creatures -- elle borne donc le
     * rythme des ameliorations sur le combat, comme les runes.
     */
    public static final DeferredItem<Item> FORGE_STONE =
            ITEMS.register("forge_stone", () -> new Item(new Item.Properties()));

    /** La Plume d'Arcencium : le materiau de la specialisation du personnage (voir Specialization). */
    public static final DeferredItem<ArcenciumFeatherItem> ARCENCIUM_FEATHER =
            ITEMS.register("arcencium_feather", () -> new ArcenciumFeatherItem(new Item.Properties()));

    /** La Plume d'apparence : une apparence d'ailes, pour des ailes a +15 ou plus. */
    public static final DeferredItem<SkinFeatherItem> SKIN_FEATHER =
            ITEMS.register("skin_feather", () -> new SkinFeatherItem(new Item.Properties()));

    public static final DeferredItem<com.emerald.element.ElementStoneItem> ELEMENT_STONE =
            ITEMS.register("element_stone", () ->
                    new com.emerald.element.ElementStoneItem(new Item.Properties()));

    public static final DeferredItem<com.emerald.rune.RuneItem> RUNE =
            ITEMS.register("rune", () ->
                    new com.emerald.rune.RuneItem(new Item.Properties()));

    public static final DeferredItem<com.emerald.artifact.ArtifactItem> ARTIFACT =
            ITEMS.register("artifact", () ->
                    new com.emerald.artifact.ArtifactItem(new Item.Properties()));

    // ------------------------------------------------- derives de l'Arbre de Prisme
    // Aucune piece d'Arcencium n'est fabricable sans passer par l'arbre : c'est
    // ce qui rend le bucheronnage aussi necessaire que le minage.

    /** Le manche : epee, arc, sceptre. */
    public static final DeferredItem<Item> PRISM_BRANCH = ITEMS.register("prism_branch",
            () -> new Item(new Item.Properties()));

    /** La doublure : les quatre pieces d'armure. */
    public static final DeferredItem<Item> PRISM_FIBER = ITEMS.register("prism_fiber",
            () -> new Item(new Item.Properties()));

    // ------------------------------------------------------- armure d'Arcencium

    public static final DeferredItem<ArmorItem> ARCENCIUM_HELMET =
            ITEMS.register("arcencium_helmet", () -> armor(ArmorItem.Type.HELMET));

    public static final DeferredItem<ArmorItem> ARCENCIUM_CHESTPLATE =
            ITEMS.register("arcencium_chestplate", () -> armor(ArmorItem.Type.CHESTPLATE));

    public static final DeferredItem<ArmorItem> ARCENCIUM_LEGGINGS =
            ITEMS.register("arcencium_leggings", () -> armor(ArmorItem.Type.LEGGINGS));

    public static final DeferredItem<ArmorItem> ARCENCIUM_BOOTS =
            ITEMS.register("arcencium_boots", () -> armor(ArmorItem.Type.BOOTS));

    private static ArmorItem armor(ArmorItem.Type type) {
        return new ArmorItem(ModArmorMaterials.ARCENCIUM, type,
                new Item.Properties().durability(ModArmorMaterials.durabilityFor(type)));
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

    /*public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, "emeraldweapons");*/
    /*public static final DeferredHolder<Item, Item> EMERALD_SWORD =
            ITEMS.register("emerald_sword", () -> {
                EmeraldTier tier = new EmeraldTier();

                return new EmeraldWindblade(tier, new Item.Properties()
                        .attributes(SwordItem.createAttributes(tier, 9.0F, -2.0F))
                );
            });*/


    /*public static final DeferredHolder<Item, Item> EMERALD_SWORD =
            ITEMS.register("emerald_sword", () -> {
                Tier tier = new EmeraldTier();
                float baseDamage = 9.0F;
                float attackSpeed = -2.0F;

                ItemAttributeModifiers attributes = ItemAttributeModifiers.builder()
                        .add(Attributes.ATTACK_DAMAGE,
                                new AttributeModifier(SwordItem.BASE_ATTACK_DAMAGE_ID,
                                        baseDamage + tier.getAttackDamageBonus(),
                                        Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .add(Attributes.ATTACK_SPEED,
                                new AttributeModifier(SwordItem.BASE_ATTACK_SPEED_ID,
                                        attackSpeed,
                                        Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .build();

                Tool tool = new Tool(
                        List.of(
                                Rule.minesAndDrops(BlockTags.SWORD_EFFICIENT, tier.getSpeed())
                        ),
                        1.0F,
                        1
                );

                return new EmeraldWindblade(tier, new Item.Properties()
                        .component(DataComponents.ATTRIBUTE_MODIFIERS, attributes));

            });*/

    // ModItems.java

    /*public static final DeferredHolder<Item, Item> EMERALD_SWORD =
            ITEMS.register("emerald_sword", () -> {
                EmeraldTier tier = new EmeraldTier();

                // Utilise les méthodes de SwordItem pour éviter d'oublier des composants
                ItemAttributeModifiers attributes = SwordItem.createAttributes(tier, 9.0F, -2.0F);
                Tool tool = tier.createToolProperties(tier.getTag());

                return new SwordItem(tier,
                        new Item.Properties()
                                .component(DataComponents.ATTRIBUTE_MODIFIERS, attributes)
                                .component(DataComponents.TOOL, tool)
                                .component(DataComponents.MAX_DAMAGE, tier.getUses())
                );
            });*/


}
