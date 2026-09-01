package com.emerald.item;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

/**
 * Ce que la rarete ajoute aux chiffres.
 *
 * On passe par l'evenement plutot que par les proprietes de l'objet : celles-ci
 * sont figees a l'enregistrement, les memes pour toutes les piles, alors que la
 * rarete vit sur LA PILE. Deux epees identiques doivent pouvoir frapper
 * differemment.
 *
 * L'ecart reste faible a dessein. Quarante centiemes de degat par rang font
 * trois et deux dixiemes du normal au Phenomenal : de quoi rendre une montee
 * desirable sans qu'une piece de rang huit rende inutile tout ce qu'on
 * trouvera ensuite. Le mode dure une heure, et une arme qui double ses degats
 * la termine toute seule.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class RarityStats {

    private static final ResourceLocation DAMAGE_ID =
            ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "rarity_damage");
    private static final ResourceLocation ARMOR_ID =
            ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "rarity_armor");

    private RarityStats() {
    }

    @SubscribeEvent
    public static void onModifiers(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        GearRarity rarity = GearRarity.of(stack);
        if (rarity == GearRarity.NORMAL) {
            return;
        }
        int rank = rarity.rank();

        // On ne devine pas la nature de la piece : on lit ce qu'elle declare
        // deja. Ce qui porte des degats est une arme, ce qui porte de l'armure
        // est une armure -- et ce raisonnement vaut aussi pour les pieces des
        // autres mods, qu'on n'a aucune raison d'exclure.
        var existing = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                stack.getItem().getDefaultInstance()
                        .get(DataComponents.ATTRIBUTE_MODIFIERS));
        boolean weapon = false;
        boolean armour = false;
        if (existing != null) {
            for (var entry : existing.modifiers()) {
                if (entry.attribute().is(Attributes.ATTACK_DAMAGE.unwrapKey().orElseThrow())) {
                    weapon = true;
                }
                if (entry.attribute().is(Attributes.ARMOR.unwrapKey().orElseThrow())) {
                    armour = true;
                }
            }
        }
        if (weapon) {
            event.addModifier(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(DAMAGE_ID, rank * GearRarity.DAMAGE_STEP,
                            AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
        }
        if (armour) {
            // ARMOR sur TOUTE piece portee : le groupe exact n'importe pas,
            // l'attribut ne s'applique de toute facon que dans l'emplacement
            // ou la piece se trouve reellement
            event.addModifier(Attributes.ARMOR,
                    new AttributeModifier(ARMOR_ID, rank * GearRarity.ARMOR_STEP,
                            AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.ARMOR);
        }
    }
}
