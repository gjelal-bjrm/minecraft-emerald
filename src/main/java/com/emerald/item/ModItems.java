package com.emerald.item;

import com.emerald.tiers.EmeraldTier;
import com.emerald.weapons.EmeraldWindblade;
import com.emerald.main.EmeraldWeaponsMod;
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
