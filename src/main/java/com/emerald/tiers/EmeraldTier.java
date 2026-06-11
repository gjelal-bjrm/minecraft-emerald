package com.emerald.tiers;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Items;

public class EmeraldTier implements Tier {

    @Override
    public int getUses() {
        return 2000;
    }

    @Override
    public float getSpeed() {
        return 9.0F;
    }

    @Override
    public float getAttackDamageBonus() {
        return 5.0F;
    }

    @Override
    public int getEnchantmentValue() {
        return 22;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(Items.EMERALD);
    }


    public TagKey<Block> getTag() {
        return Tiers.NETHERITE.getTag();
    }

    @Override
    public TagKey<Block> getIncorrectBlocksForDrops() {
        return Tiers.NETHERITE.getIncorrectBlocksForDrops();
    }
}
