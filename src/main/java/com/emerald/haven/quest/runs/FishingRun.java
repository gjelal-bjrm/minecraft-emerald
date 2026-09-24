package com.emerald.haven.quest.runs;

import com.emerald.haven.quest.HavenQuest;
import com.emerald.haven.quest.QuestRun;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * LA PECHE du Pecheur (§85.1) : deux cents livres de poisson, peches dans l'eau de la ville.
 *
 * Chaque espece a SON POIDS, tire a chaque prise entre deux bornes (plus souvent pres de la
 * petite) : la morue quelques livres, le saumon un peu plus, le thon d'Aquaculture de quoi
 * finir d'un coup. Les poids d'Aquaculture sont coupes dans le profil : ceux-ci sont les
 * notres. Ce qui n'est pas un poisson (bottes, bouteilles, tresors) ne pese rien ; un
 * poisson inconnu d'un autre mod pese comme un petit poisson.
 *
 * Chaque membre recoit la CANNE DU PECHEUR (Appat III, Chance de la mer I) s'il ne l'a pas
 * deja ; elle reste a lui. Le poids est commun a l'equipe.
 */
public class FishingRun extends QuestRun {

    public static final double GOAL = 200.0;
    /** La marque de la canne dans ses donnees. */
    public static final String ROD_MARK = "emeraldweapons_canne_pecheur";

    private record Range(double min, double max) {
    }

    private static final Map<String, Range> WEIGHTS = Map.ofEntries(
            Map.entry("minecraft:cod", new Range(5, 16)),
            Map.entry("minecraft:salmon", new Range(8, 24)),
            Map.entry("minecraft:tropical_fish", new Range(0.3, 1.5)),
            Map.entry("minecraft:pufferfish", new Range(1, 5)),
            Map.entry("aquaculture:atlantic_cod", new Range(8, 30)),
            Map.entry("aquaculture:blackfish", new Range(2, 8)),
            Map.entry("aquaculture:pacific_halibut", new Range(20, 80)),
            Map.entry("aquaculture:atlantic_halibut", new Range(25, 90)),
            Map.entry("aquaculture:atlantic_herring", new Range(0.5, 2)),
            Map.entry("aquaculture:pink_salmon", new Range(3, 10)),
            Map.entry("aquaculture:pollock", new Range(3, 15)),
            Map.entry("aquaculture:rainbow_trout", new Range(2, 9)),
            Map.entry("aquaculture:bayad", new Range(5, 25)),
            Map.entry("aquaculture:boulti", new Range(1, 5)),
            Map.entry("aquaculture:capitaine", new Range(20, 90)),
            Map.entry("aquaculture:synodontis", new Range(0.5, 3)),
            Map.entry("aquaculture:smallmouth_bass", new Range(1, 6)),
            Map.entry("aquaculture:bluegill", new Range(0.3, 1.5)),
            Map.entry("aquaculture:brown_trout", new Range(2, 12)),
            Map.entry("aquaculture:carp", new Range(5, 35)),
            Map.entry("aquaculture:catfish", new Range(10, 60)),
            Map.entry("aquaculture:gar", new Range(5, 30)),
            Map.entry("aquaculture:minnow", new Range(0.1, 0.4)),
            Map.entry("aquaculture:muskellunge", new Range(10, 40)),
            Map.entry("aquaculture:perch", new Range(0.5, 3)),
            Map.entry("aquaculture:arapaima", new Range(60, 200)),
            Map.entry("aquaculture:piranha", new Range(1, 4)),
            Map.entry("aquaculture:tambaqui", new Range(10, 45)),
            Map.entry("aquaculture:red_grouper", new Range(5, 30)),
            Map.entry("aquaculture:tuna", new Range(60, 250)),
            Map.entry("aquaculture:jellyfish", new Range(0.5, 3)));
    private static final Range UNKNOWN_FISH = new Range(1, 8);
    private static final TagKey<Item> RAW_FISH = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "foods/raw_fish"));

    private double total;
    private final Set<UUID> equipped = new HashSet<>();

    public FishingRun(HavenQuest quest, ServerLevel level) {
        super(quest, level);
    }

    @Override
    public void begin() {
    }

    @Override
    public void tick(long now) {
        if (now % 20 != 0) {
            return;
        }
        for (ServerPlayer player : members()) {
            if (this.equipped.add(player.getUUID()) && !hasRod(player)) {
                ItemStack rod = rod(this.level);
                if (!player.getInventory().add(rod)) {
                    player.drop(rod, false);
                }
                player.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.quete.peche.canne")
                        .withStyle(ChatFormatting.DARK_AQUA));
            }
        }
    }

    /** La canne du Pecheur : Appat III, Chance de la mer I, a son nom. */
    public static ItemStack rod(ServerLevel level) {
        ItemStack rod = new ItemStack(Items.FISHING_ROD);
        var enchantments = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        rod.enchant(enchantments.getHolderOrThrow(Enchantments.LURE), 3);
        rod.enchant(enchantments.getHolderOrThrow(Enchantments.LUCK_OF_THE_SEA), 1);
        rod.set(DataComponents.CUSTOM_NAME, Component.translatable("item.emeraldweapons.canne_pecheur")
                .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.DARK_AQUA)));
        CompoundTag mark = new CompoundTag();
        mark.putBoolean(ROD_MARK, true);
        rod.set(DataComponents.CUSTOM_DATA, CustomData.of(mark));
        return rod;
    }

    private static boolean hasRod(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data != null && data.copyTag().getBoolean(ROD_MARK)) {
                return true;
            }
        }
        CustomData offhand = player.getOffhandItem().get(DataComponents.CUSTOM_DATA);
        return offhand != null && offhand.copyTag().getBoolean(ROD_MARK);
    }

    /** Le poids d'une prise, en livres ; 0 si ce n'est pas un poisson. */
    public static double weigh(ItemStack stack, double roll) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        Range range = WEIGHTS.get(id);
        if (range == null) {
            if (!stack.is(ItemTags.FISHES) && !stack.is(RAW_FISH)) {
                return 0.0;
            }
            range = UNKNOWN_FISH;
        }
        // plus souvent pres de la petite borne
        return range.min() + (range.max() - range.min()) * roll * roll;
    }

    @Override
    public void onFish(ServerPlayer member, List<ItemStack> drops) {
        for (ItemStack stack : drops) {
            double weight = weigh(stack, this.level.random.nextDouble()) * stack.getCount();
            if (weight <= 0.0) {
                continue;
            }
            this.total += weight;
            for (ServerPlayer player : members()) {
                Locale locale = player.clientInformation().language().startsWith("fr") ? Locale.FRENCH : Locale.ROOT;
                player.displayClientMessage(Component.translatable("game.emeraldweapons.haven.quete.peche.prise",
                        stack.getHoverName(), String.format(locale, "%.1f", weight),
                        String.format(locale, "%.1f", Math.min(this.total, GOAL)), (int) GOAL).withStyle(ChatFormatting.AQUA), true);
                player.playNotifySound(SoundEvents.FISH_SWIM, SoundSource.PLAYERS, 0.8F, 1.2F);
            }
        }
        if (this.total >= GOAL) {
            succeed();
        }
    }

    @Override
    public Component objective() {
        return Component.translatable("game.emeraldweapons.haven.quete.peche.objectif", (int) Math.floor(this.total), (int) GOAL);
    }

    @Override
    public float progress() {
        return (float) Math.min(1.0, this.total / GOAL);
    }

    @Override
    public BossEvent.BossBarColor color() {
        return BossEvent.BossBarColor.BLUE;
    }
}
