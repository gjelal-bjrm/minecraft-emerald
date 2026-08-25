package com.emerald.item;

import com.emerald.tiers.EmeraldTier;
import com.emerald.weapons.ArcenciumBowItem;
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
                                    .attributes(SwordItem.createAttributes(new EmeraldTier(), 4.0F, -2.2F))
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
     * La Lame du Serment.
     *
     * Une Epee d'Emeraude ceremonielle : elle montre des la premiere minute a
     * quoi ressemble l'equipement du mode, sans court-circuiter la progression,
     * puisqu'elle se dissout des le village tenu.
     */
    public static final DeferredItem<SwordItem> OATH_BLADE =
            ITEMS.register("oath_blade", () -> new SwordItem(new EmeraldTier(),
                    new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC)
                            .attributes(SwordItem.createAttributes(new EmeraldTier(), 3.0F, -2.2F))));

    /** Objet artefact : voir com.emerald.artifact.ArtifactItem. */
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
