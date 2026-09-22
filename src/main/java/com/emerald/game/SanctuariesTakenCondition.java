package com.emerald.game;

import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * LE BUTIN SUIT L'AVANCEE (22 sept., cahier §83) : une condition de table de butin, vraie
 * quand l'avancee de la partie tombe entre {@code min} et {@code max}.
 *
 * « Les recompenses dans les coffres sont beaucoup trop genereuses en termes de materiaux
 * des le premier sanctuaire. » Le palier d'un coffre venait de l'ORDRE DE POSE des ancres,
 * pas de l'ordre de visite : le premier sanctuaire visite pouvait etre le plus riche. Le
 * coffre se tire desormais a l'ouverture (Lootr, un tirage par joueur) selon le nombre de
 * sanctuaires DEJA PRIS : le premier visite donne peu, le troisieme le plus
 * (data/emeraldweapons/loot_table/chests/sanctuary.json, ecrite par tools/sanctuary_loot.py).
 *
 * L'avancee compte aussi les cycles du monde ouvert : apres le boss, trois sanctuaires se
 * relevent ailleurs et les ancres repartent de zero, mais le personnage, lui, n'a pas
 * rajeuni -- un cycle vaut trois sanctuaires pris.
 */
public record SanctuariesTakenCondition(int min, int max) implements LootItemCondition {

    public static final MapCodec<SanctuariesTakenCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("min", 0).forGetter(SanctuariesTakenCondition::min),
            Codec.INT.optionalFieldOf("max", Integer.MAX_VALUE).forGetter(SanctuariesTakenCondition::max)
    ).apply(instance, SanctuariesTakenCondition::new));

    private static final DeferredRegister<LootItemConditionType> CONDITIONS =
            DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, EmeraldWeaponsMod.MODID);

    public static final DeferredHolder<LootItemConditionType, LootItemConditionType> TYPE =
            CONDITIONS.register("sanctuaries_taken", () -> new LootItemConditionType(CODEC));

    public static void register(IEventBus bus) {
        CONDITIONS.register(bus);
    }

    /** Pour le banc d'essai : une avancee imposee, ou null. */
    @javax.annotation.Nullable
    public static Integer forcedProgress;

    /** L'avancee de la partie : les sanctuaires pris, plus trois par cycle du monde ouvert deja fini. */
    public static int progress(GameState state) {
        if (forcedProgress != null) {
            return forcedProgress;
        }
        return state.anchorsActive() + 3 * Math.max(0, state.cycle() - 1);
    }

    @Override
    public LootItemConditionType getType() {
        return TYPE.get();
    }

    @Override
    public boolean test(LootContext context) {
        int taken = progress(GameState.get(context.getLevel()));
        return taken >= this.min && taken <= this.max;
    }
}
