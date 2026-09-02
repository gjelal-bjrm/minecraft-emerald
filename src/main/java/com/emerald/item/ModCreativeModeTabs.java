package com.emerald.item;

import com.emerald.block.ModBlocks;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MOD_TAB =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EmeraldWeaponsMod.MODID);

    public static final Supplier<CreativeModeTab> ARCENCIUM_ITEMS_TAB = CREATIVE_MOD_TAB.register(
            "arcencium_items_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModItems.ARCENCIUM_INGOT.get()))
                    .title(Component.translatable("creativetab.emeraldweaponsmod.arcencium_items"))
                    .displayItems((itemDisplayParameters, output) -> {
                        output.accept(ModItems.RAW_ARCENCIUM);
                        output.accept(ModItems.ARCENCIUM_INGOT);
                        output.accept(ModItems.PRISM_BRANCH);
                        output.accept(ModItems.PRISM_FIBER);
                        output.accept(ModItems.EMERALD_SWORD);
                        output.accept(ModItems.ARCENCIUM_BOW);
                        output.accept(ModItems.ARCENCIUM_SCEPTER);
                        output.accept(ModItems.ARCENCIUM_GLAIVE);
                        output.accept(ModItems.FATE_SHARD);
                        output.accept(ModItems.SANCTUARY_PROBE);
                        output.accept(ModItems.ARCENCIUM_HELMET);
                        output.accept(ModItems.ARCENCIUM_CHESTPLATE);
                        output.accept(ModItems.ARCENCIUM_LEGGINGS);
                        output.accept(ModItems.ARCENCIUM_BOOTS);
                        // un exemplaire de chaque artefact, deja serti dans l'objet
                        for (var artifact : com.emerald.artifact.Artifact.values()) {
                            output.accept(com.emerald.artifact.ArtifactItem.stack(
                                    artifact, ModItems.ARTIFACT.get()));
                        }
                        output.accept(ModItems.FORGE_STONE);
                        // les quatre cristaux elementaires
                        for (com.emerald.element.Element element
                                : com.emerald.element.Element.values()) {
                            if (element != com.emerald.element.Element.NEUTRE) {
                                output.accept(com.emerald.element.ElementStoneItem.stack(
                                        element, ModItems.ELEMENT_STONE.get(), 1));
                            }
                        }
                        // une rune de chaque famille, a chaque rang.
                        //
                        // Leurs options sont tirees AU HASARD, a chaque
                        // construction de l'onglet. J'avais d'abord fixe la
                        // graine pour que les runes de test soient les memes
                        // d'une session a l'autre -- et le joueur, voyant deux
                        // fois la meme rune, a conclu que RIEN n'etait
                        // aleatoire. Un banc d'essai qui fait douter du jeu
                        // qu'il teste ne sert a rien : l'onglet tire donc
                        // comme les monstres tirent.
                        for (com.emerald.rune.RuneFamily family
                                : com.emerald.rune.RuneFamily.values()) {
                            for (int rank = 1;
                                 rank < com.emerald.item.GearRarity.values().length; rank++) {
                                output.accept(com.emerald.rune.RuneItem.stack(
                                        com.emerald.rune.RuneMark.roll(family, rank,
                                                net.minecraft.util.RandomSource.create()),
                                        ModItems.RUNE.get()));
                            }
                        }
                    }).build()
    );

    public static final Supplier<CreativeModeTab> ARCENCIUM_BLOCK_TAB = CREATIVE_MOD_TAB.register(
            "arcencium_blocks_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModBlocks.ARCENCIUM_ORE.get()))
                    .withTabsBefore(ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "arcencium_items_tab"))
                    .title(Component.translatable("creativetab.emeraldweaponsmod.arcencium_blocks"))
                    .displayItems((itemDisplayParameters, output) -> {
                        output.accept(ModBlocks.ARCENCIUM_ORE);
                        output.accept(ModBlocks.ARCENCIUM_BLOCK);
                        output.accept(ModBlocks.ARCENCIUM_CHEST);
                        output.accept(ModBlocks.SOCKET_BENCH);
                        // toute la palette du village, dans l'ordre de declaration
                        for (var block : ModBlocks.VILLAGE_BLOCKS) {
                            output.accept(block.get());
                        }
                    }).build()
    );

    public static void register(IEventBus eventBus){
        CREATIVE_MOD_TAB.register(eventBus);
    }
}
