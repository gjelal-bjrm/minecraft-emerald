package com.emerald.quest;

import com.emerald.game.GameState;
import com.emerald.game.SanctuarySeals;
import com.emerald.item.GearRarity;
import com.emerald.item.ModItems;
import com.emerald.item.Upgrade;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.rune.RuneFamily;
import com.emerald.rune.Runes;
import com.emerald.specialization.Specialization;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.function.BiPredicate;

/**
 * LE CARNET : dix etapes qui apprennent le mode en le jouant.
 *
 * « Je ne sais pas du tout comment fabriquer les armes et je n'ai aucun moyen
 * de le savoir. Un joueur qui utilise notre forge ne saura jamais comment ca
 * fonctionne, surtout pour runer l'arme et monter sa rarete. » Le manuel
 * existe, mais personne ne lit trente pages en pleine partie. Le carnet dit
 * UNE chose a la fois, au moment ou elle sert, et la coche quand c'est fait.
 *
 * Chaque etape se verifie sur ce que le joueur PORTE ou A FAIT -- jamais sur
 * un clic dans une interface, qu'on rate ou qu'on ne fait pas dans l'ordre.
 * On relit toutes les deux secondes ; la memoire vit dans les donnees
 * persistantes du joueur, qui survivent a la mort (PlayerPersistence) et a la
 * session. Chaque etape franchie paie une petite chose utile a la suivante :
 * le carnet ne remplace pas le jeu, il l'ouvre.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class Quests {

    private static final String TAG_STEP = "ArcenciumQuest";
    private static final String TAG_TOLD = "ArcenciumQuestTold";
    private static final int EVERY = 40;

    private Quests() {
    }

    /**
     * Une etape : ce qu'on verifie, ce qu'on paie, et sa cle de texte.
     *
     * Les recompenses sont des FOURNISSEURS, pas des piles : cette classe est
     * un abonne d'evenements, elle se charge a la construction du mod, avant
     * l'enregistrement des objets, et `ModItems.X.get()` y levait « Trying to
     * access unbound value ». Le banc l'a attrape au premier lancement.
     */
    public record Step(String key, BiPredicate<ServerPlayer, ServerLevel> done,
                       List<java.util.function.Supplier<ItemStack>> reward) {
    }

    private static java.util.function.Supplier<ItemStack> of(
            java.util.function.Supplier<? extends Item> item, int count) {
        return () -> new ItemStack(item.get(), count);
    }

    private static java.util.function.Supplier<ItemStack> of(Item item, int count) {
        return () -> new ItemStack(item, count);
    }

    public static final List<Step> STEPS = List.of(
            new Step("arcencium", (p, l) -> count(p, ModItems.RAW_ARCENCIUM.get()) >= 1
                    || count(p, ModItems.ARCENCIUM_SHARD.get()) >= 4
                    || count(p, ModItems.ARCENCIUM_INGOT.get()) >= 1,
                    List.of(of(ModItems.FATE_SHARD, 2))),
            new Step("lingot", (p, l) -> count(p, ModItems.ARCENCIUM_INGOT.get()) >= 3,
                    List.of(of(ModItems.FORGE_STONE, 3))),
            new Step("prisme", (p, l) -> count(p, ModItems.PRISM_BRANCH.get()) >= 1
                    || count(p, ModItems.PRISM_FIBER.get()) >= 1,
                    List.of(of(net.minecraft.world.item.Items.EMERALD, 4))),
            new Step("arme", (p, l) -> anyGear(p, s -> RuneFamily.isOurs(s)),
                    List.of(of(ModItems.FORGE_STONE, 6),
                            of(net.minecraft.world.item.Items.IRON_INGOT, 8))),
            new Step("forge", (p, l) -> anyGear(p, s -> Upgrade.of(s) >= 1),
                    List.of(of(ModItems.FATE_SHARD, 3))),
            new Step("rune", (p, l) -> anyGear(p, s -> !Runes.on(s).isEmpty()),
                    List.of(of(ModItems.ARCENCIUM_FEATHER, 3))),
            new Step("rarete", (p, l) -> anyGear(p, s -> GearRarity.of(s).rank() >= 2),
                    List.of(of(ModItems.ARCENCIUM_FEATHER, 4))),
            new Step("specialisation", (p, l) -> Specialization.level(p) >= 1,
                    List.of(of(ModItems.FATE_SHARD, 2),
                            of(net.minecraft.world.item.Items.GOLD_INGOT, 6))),
            new Step("tombeau", (p, l) -> SanctuarySeals.anyLit(l),
                    List.of(of(ModItems.ARCENCIUM_INGOT, 8))),
            new Step("ancre", (p, l) -> GameState.get(l).anchorsActive() >= 1,
                    List.of(of(ModItems.FORGE_STONE, 12)))
    );

    // ------------------------------------------------------------ lecture

    public static int step(ServerPlayer player) {
        return Math.max(0, Math.min(STEPS.size(), player.getPersistentData().getInt(TAG_STEP)));
    }

    public static boolean finished(ServerPlayer player) {
        return step(player) >= STEPS.size();
    }

    private static int count(ServerPlayer player, Item item) {
        int n = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    /** Vrai si une piece portee ou dans le sac satisfait le test. */
    private static boolean anyGear(ServerPlayer player, java.util.function.Predicate<ItemStack> test) {
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && test.test(stack)) {
                return true;
            }
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack worn = player.getItemBySlot(slot);
            if (!worn.isEmpty() && test.test(worn)) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------- la tique

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || level.getGameTime() % EVERY != 0) {
            return;
        }
        GameState state = GameState.get(level);
        if (state.status() != GameState.Status.RUNNING) {
            return;                                 // le carnet s'ouvre avec la partie
        }
        int at = step(player);
        if (at >= STEPS.size()) {
            return;
        }
        CompoundTag tag = player.getPersistentData();
        if (!tag.getBoolean(TAG_TOLD)) {
            tell(player, at);                       // la premiere fois, on dit quoi faire
            tag.putBoolean(TAG_TOLD, true);
            sync(player);
        }
        Step step = STEPS.get(at);
        if (!step.done().test(player, level)) {
            return;
        }
        // FRANCHIE. On coche, on paie, et l'on dit la suivante tout de suite :
        // un carnet qui se tait apres une coche laisse le joueur sans cap.
        tag.putInt(TAG_STEP, at + 1);
        tag.putBoolean(TAG_TOLD, false);
        player.sendSystemMessage(Component.translatable("quest.emeraldweapons.done",
                        Component.translatable("quest.emeraldweapons." + step.key() + ".title"))
                .withStyle(ChatFormatting.GREEN));
        for (java.util.function.Supplier<ItemStack> reward : step.reward()) {
            ItemStack given = reward.get();
            // On lit le nom et le compte AVANT de ranger : `add` vide la pile
            // qu'on lui tend, et le banc a lu « + 0 x Air » cinq fois de suite.
            int count = given.getCount();
            Component name = given.getHoverName();
            if (!player.getInventory().add(given)) {
                player.drop(given, false);
            }
            player.sendSystemMessage(Component.translatable("quest.emeraldweapons.reward", count, name)
                    .withStyle(ChatFormatting.GRAY));
        }
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.3F);
        if (at + 1 >= STEPS.size()) {
            player.sendSystemMessage(Component.translatable("quest.emeraldweapons.finished")
                    .withStyle(style -> style.withColor(0xFFD24A).withBold(true)));
            tag.putBoolean(TAG_TOLD, true);
        }
        sync(player);
    }

    // ------------------------------------------------------------- le texte

    /** Dit l'etape en cours : son titre, quoi faire, et la recette s'il y en a une. */
    public static void tell(ServerPlayer player, int at) {
        if (at >= STEPS.size()) {
            player.sendSystemMessage(Component.translatable("quest.emeraldweapons.finished")
                    .withStyle(style -> style.withColor(0xFFD24A).withBold(true)));
            return;
        }
        Step step = STEPS.get(at);
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.translatable("quest.emeraldweapons.header",
                        at + 1, STEPS.size(),
                        Component.translatable("quest.emeraldweapons." + step.key() + ".title"))
                .withStyle(style -> style.withColor(0x9CE8FF).withBold(true)));
        player.sendSystemMessage(Component.translatable("quest.emeraldweapons." + step.key() + ".how")
                .withStyle(ChatFormatting.WHITE));
        for (Component line : recipeLines(step.key())) {
            player.sendSystemMessage(line);
        }
        player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /**
     * Les recettes, dessinees en lettres : une grille de trois par trois se lit
     * mieux que trois lignes de prose, et le jeu ne sait pas dessiner un
     * etabli dans le chat.
     */
    private static List<Component> recipeLines(String key) {
        return switch (key) {
            case "arme" -> List.of(
                    grid("quest.emeraldweapons.recipe.sword", "E N E", "A A A", "B . B"),
                    grid("quest.emeraldweapons.recipe.glaive", "A E A", "A F A", "E B E"),
                    grid("quest.emeraldweapons.recipe.scepter", "A E A", ". A .", ". B ."),
                    grid("quest.emeraldweapons.recipe.bow", "E A .", "A R B", "E A ."),
                    Component.translatable("quest.emeraldweapons.recipe.legend")
                            .withStyle(ChatFormatting.DARK_GRAY));
            case "lingot" -> List.of(
                    Component.translatable("quest.emeraldweapons.recipe.ingot")
                            .withStyle(ChatFormatting.GRAY));
            case "prisme" -> List.of(
                    Component.translatable("quest.emeraldweapons.recipe.prism")
                            .withStyle(ChatFormatting.GRAY));
            default -> List.of();
        };
    }

    private static Component grid(String nameKey, String r1, String r2, String r3) {
        MutableComponent line = Component.translatable(nameKey).withStyle(ChatFormatting.AQUA);
        line.append(Component.literal("  " + r1 + " / " + r2 + " / " + r3)
                .withStyle(ChatFormatting.GRAY));
        return line;
    }

    // ------------------------------------------------------------- le client

    private static void sync(ServerPlayer player) {
        int at = step(player);
        String key = at < STEPS.size() ? STEPS.get(at).key() : "";
        PacketDistributor.sendToPlayer(player, new com.emerald.network.QuestPayload(at, STEPS.size(), key));
    }

    /** A la connexion : le carnet se rouvre a la bonne page. */
    public static void onJoin(ServerPlayer player) {
        sync(player);
    }
}
