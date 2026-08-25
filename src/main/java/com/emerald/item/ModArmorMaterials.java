package com.emerald.item;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;

/**
 * Materiau d'armure Arcencium.
 *
 * Legerement au-dessus de la netherite sur tous les tableaux, sans la ridiculiser :
 *
 *   protection    22 contre 20      (3 / 9 / 7 / 3)
 *   tenacite      3,5 contre 3,0
 *   recul         0,15 contre 0,10
 *   durabilite    facteur 45 contre 37
 *   enchantement  22 contre 15
 *
 * Elle se distingue surtout par son bonus de panoplie
 * ({@link com.emerald.item.ArcenciumSetBonus}) et par ses emplacements d'artefact.
 */
public class ModArmorMaterials {

    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, EmeraldWeaponsMod.MODID);

    /** Multiplie la durabilite de base propre a chaque piece (netherite : 37). */
    private static final int DURABILITY_FACTOR = 45;      // netherite : 37

    public static final Holder<ArmorMaterial> ARCENCIUM = ARMOR_MATERIALS.register("arcencium",
            () -> new ArmorMaterial(
                    defense(),
                    22,                                     // meme valeur d'enchantement que l'EmeraldTier
                    SoundEvents.ARMOR_EQUIP_NETHERITE,
                    () -> Ingredient.of(ModItems.ARCENCIUM_INGOT.get()),
                    List.of(new ArmorMaterial.Layer(
                            ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "arcencium"))),
                    3.5F,                                   // tenacite, netherite : 3,0
                    0.15F                                   // recul, netherite : 0,10
            ));

    private static EnumMap<ArmorItem.Type, Integer> defense() {
        EnumMap<ArmorItem.Type, Integer> map = new EnumMap<>(ArmorItem.Type.class);
        map.put(ArmorItem.Type.HELMET, 3);        // netherite : 3
        map.put(ArmorItem.Type.CHESTPLATE, 9);    // netherite : 8
        map.put(ArmorItem.Type.LEGGINGS, 7);      // netherite : 6
        map.put(ArmorItem.Type.BOOTS, 3);         // netherite : 3
        map.put(ArmorItem.Type.BODY, 12);
        return map;
    }

    public static int durabilityFor(ArmorItem.Type type) {
        return type.getDurability(DURABILITY_FACTOR);
    }

    public static void register(IEventBus eventBus) {
        ARMOR_MATERIALS.register(eventBus);
    }
}
