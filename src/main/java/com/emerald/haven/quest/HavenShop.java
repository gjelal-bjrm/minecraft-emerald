package com.emerald.haven.quest;

import com.emerald.haven.Haven;
import com.emerald.haven.journey.HavenProgress;
import com.emerald.item.ModItems;
import com.emerald.jak.gun.GunForm;
import com.emerald.jak.gun.MorphGunData;
import com.emerald.jak.gun.MorphGunKeeper;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.network.HavenShopPayload;
import com.emerald.rune.RuneDrops;
import com.emerald.rune.RuneItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * LA BOUTIQUE DE TESS (lot 3, cahier §86) : « le shop de la map », dans la salle des armes.
 *
 * Les orbes gagnes aux quetes et ramasses dans la ville y achetent :
 *
 *  - LES ONZE ARMES du Morph Gun (le Pulverisator est au ratelier du QG), au prix de leur
 *    couleur -- rouge 40, jaune 80, bleue 120, sombre 200 (choix du joueur, §85.1) -- et
 *    dans l'ordre de la famille : la deuxieme apres la premiere ;
 *  - L'ECO ILLIMITE d'une couleur (150, 200, 250, 400) : la reserve de la famille reste
 *    pleine, dans la ville (le Morph Gun n'en sort pas) -- il faut une arme de la couleur ;
 *  - LES BONUS DU DEFI, qui s'accumulent et servent une fois : les PROVISIONS (40) et la
 *    RUNE GARANTIE (50), remises au depart ; le SCEAU DE FORGE (60), qui change un echec
 *    de la forge en reussite ; le SCEAU DE RARETE (60), qui fait monter d'un rang un eclat
 *    du destin qui n'aurait rien donne ; le SCEAU DE SPECIALISATION (60), qui fait reussir
 *    une tentative ratee. Les sceaux ne partent que quand ils servent.
 *
 * Le serveur decide de tout : la boutique ne s'ouvre et n'achete que pres de Tess, et
 * l'ecran montre ce que le serveur lui envoie.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenShop {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /** On achete a portee de voix de Tess. */
    public static final double REACH = 10.0;

    public static final String PROVISIONS = "provisions";
    public static final String RUNE = "rune";
    public static final String SEAL_FORGE = "sceau_forge";
    public static final String SEAL_RARITY = "sceau_rarete";
    public static final String SEAL_SPECIALIZATION = "sceau_specialisation";

    private static final List<String> BONUSES = List.of(PROVISIONS, RUNE, SEAL_FORGE, SEAL_RARITY, SEAL_SPECIALIZATION);
    private static final int BONUS_COLOR = 0xFFB98CFF;

    private HavenShop() {
    }

    // ================================================================ les prix

    public static int weaponPrice(GunForm.Family family) {
        return switch (family) {
            case RED -> 40;
            case YELLOW -> 80;
            case BLUE -> 120;
            case DARK -> 200;
        };
    }

    public static int unlimitedPrice(GunForm.Family family) {
        return switch (family) {
            case RED -> 150;
            case YELLOW -> 200;
            case BLUE -> 250;
            case DARK -> 400;
        };
    }

    public static int bonusPrice(String key) {
        return switch (key) {
            case PROVISIONS -> 40;
            case RUNE -> 50;
            default -> 60;
        };
    }

    public static String unlimitedKey(GunForm.Family family) {
        return "illimite_" + family.jak;
    }

    /** L'eco de cette couleur est-il illimite pour lui ? */
    public static boolean unlimited(UUID id, GunForm.Family family) {
        return HavenProgress.bonus(id, unlimitedKey(family)) > 0;
    }

    // ================================================================ le catalogue de ce joueur

    private static List<HavenShopPayload.Article> catalog(ServerPlayer player) {
        UUID id = player.getUUID();
        int forms = HavenProgress.forms(id);
        List<HavenShopPayload.Article> out = new ArrayList<>();
        for (GunForm form : GunForm.values()) {
            if (form == GunForm.RED_1) {
                continue;
            }
            int state;
            Component info;
            if ((forms & form.bit()) != 0) {
                state = HavenShopPayload.OWNED;
                info = Component.translatable("game.emeraldweapons.haven.boutique.arme.info", Component.translatable(form.family.translationKey()), form.rank);
            } else if (form.rank > 1 && (forms & GunForm.of(form.family, form.rank - 1).bit()) == 0) {
                // la deuxieme apres la premiere ; le Pulverisator ne se vend pas, il est au ratelier du QG
                GunForm first = GunForm.of(form.family, form.rank - 1);
                state = HavenShopPayload.LOCKED;
                info = first == GunForm.RED_1 ? Component.translatable("game.emeraldweapons.haven.boutique.verrou.ratelier")
                        : Component.translatable("game.emeraldweapons.haven.boutique.verrou.rang", Component.translatable(first.translationKey()));
            } else {
                state = HavenShopPayload.BUYABLE;
                info = Component.translatable("game.emeraldweapons.haven.boutique.arme.info", Component.translatable(form.family.translationKey()), form.rank);
            }
            out.add(new HavenShopPayload.Article("arme." + form.id, 0, Component.translatable(form.translationKey()), info,
                    weaponPrice(form.family), state, 0, form.family.color));
        }
        for (GunForm.Family family : GunForm.Family.values()) {
            String key = unlimitedKey(family);
            boolean owns = false;
            for (int rank = 1; rank <= 3; rank++) {
                owns |= (forms & GunForm.of(family, rank).bit()) != 0;
            }
            int state = unlimited(id, family) ? HavenShopPayload.OWNED : owns ? HavenShopPayload.BUYABLE : HavenShopPayload.LOCKED;
            Component info = state == HavenShopPayload.LOCKED
                    ? Component.translatable("game.emeraldweapons.haven.boutique.verrou.famille", Component.translatable(family.translationKey()))
                    : Component.translatable("game.emeraldweapons.haven.boutique." + key + ".info");
            out.add(new HavenShopPayload.Article(key, 1, Component.translatable("game.emeraldweapons.haven.boutique." + key), info,
                    unlimitedPrice(family), state, 0, family.color));
        }
        for (String key : BONUSES) {
            out.add(new HavenShopPayload.Article(key, 2, Component.translatable("game.emeraldweapons.haven.boutique." + key),
                    Component.translatable("game.emeraldweapons.haven.boutique." + key + ".info"), bonusPrice(key),
                    HavenShopPayload.BUYABLE, HavenProgress.bonus(id, key), BONUS_COLOR));
        }
        return out;
    }

    // ================================================================ ouvrir, acheter

    /** Pres de Tess ? */
    static boolean nearTess(ServerPlayer player) {
        if (!Haven.is(player.level())) {
            return false;
        }
        HavenNpcEntity tess = HavenNpcs.loaded((ServerLevel) player.level(), HavenHero.TESS);
        Vec3 at = tess != null ? tess.position() : HavenNpcs.spot(HavenHero.TESS);
        return at != null && player.position().distanceTo(at) <= REACH;
    }

    /** Le bouton « Boutique » de la carte de Tess. */
    public static void open(ServerPlayer player) {
        if (!nearTess(player)) {
            player.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.boutique.loin").withStyle(ChatFormatting.GRAY));
            return;
        }
        send(player, true);
    }

    /** Ferme l'ecran de la boutique chez ce joueur (catalogue vide). */
    public static void close(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new HavenShopPayload(false, HavenProgress.orbs(player.getUUID()), List.of()));
    }

    static void send(ServerPlayer player, boolean open) {
        PacketDistributor.sendToPlayer(player, new HavenShopPayload(open, HavenProgress.orbs(player.getUUID()), catalog(player)));
    }

    /** Un achat demande par l'ecran : tout est revalide ici. */
    public static void buy(ServerPlayer player, String articleId) {
        if (!nearTess(player)) {
            player.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.boutique.loin").withStyle(ChatFormatting.GRAY));
            return;
        }
        HavenShopPayload.Article article = article(player, articleId);
        if (article == null) {
            return;
        }
        UUID id = player.getUUID();
        Component refusal = null;
        if (article.state() == HavenShopPayload.OWNED) {
            refusal = Component.translatable("game.emeraldweapons.haven.boutique.possede", article.name());
        } else if (article.state() == HavenShopPayload.LOCKED) {
            refusal = article.info();
        } else if (!HavenProgress.spendOrbs(id, article.price())) {
            refusal = Component.translatable("game.emeraldweapons.haven.boutique.solde", article.price(), HavenProgress.orbs(id));
        }
        if (refusal != null) {
            player.displayClientMessage(refusal.copy().withStyle(ChatFormatting.RED), true);
            player.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6F, 1.3F);
            send(player, false);
            return;
        }
        boolean hadMastery = HavenProgress.mastery(id);
        deliver(player, article.id());
        player.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.boutique.achat", article.name(),
                article.price(), HavenProgress.orbs(id)).withStyle(ChatFormatting.LIGHT_PURPLE));
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.9F, 0.9F);
        player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
        com.emerald.haven.journey.HavenJourney.award(player, "haven_boutique");
        LOGGER.info("boutique de Tess : {} achete « {} » ({} orbes)", player.getGameProfile().getName(), article.id(), article.price());
        if (!hadMastery && HavenProgress.mastery(id)) {
            player.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.quete.maitrise")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            com.emerald.haven.journey.HavenJourney.award(player, "haven_maitrise");
        }
        HavenOrbs.sync(player);
        send(player, false);
    }

    private static void deliver(ServerPlayer player, String articleId) {
        UUID id = player.getUUID();
        if (articleId.startsWith("arme.")) {
            HavenProgress.grantForms(id, GunForm.byId(articleId.substring(5)).bit());
            MorphGunKeeper.guard(player);
        } else {
            // l'eco illimite (acquis une fois pour toutes) et les bonus du Defi (qui s'accumulent)
            HavenProgress.addBonus(id, articleId, 1);
        }
    }

    // ================================================================ ce que les bonus font

    /** Un sceau sert : il part, et on le dit. @return vrai s'il en avait un */
    public static boolean useSeal(ServerPlayer player, String key) {
        if (!HavenProgress.takeBonus(player.getUUID(), key)) {
            return false;
        }
        player.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.boutique.sceau.joue",
                Component.translatable("game.emeraldweapons.haven.boutique." + key),
                HavenProgress.bonus(player.getUUID(), key)).withStyle(ChatFormatting.LIGHT_PURPLE));
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 1.2F);
        return true;
    }

    /** Au depart du Defi (HavenArrival.toVillage, apres le kit) : provisions et rune achetees. */
    public static void onDeparture(ServerPlayer player) {
        UUID id = player.getUUID();
        if (HavenProgress.takeBonus(id, PROVISIONS)) {
            give(player, new ItemStack(Items.COOKED_BEEF, 16));
            give(player, new ItemStack(Items.GOLDEN_CARROT, 8));
            give(player, new ItemStack(Items.GOLDEN_APPLE, 2));
            player.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.boutique.provisions.remises")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (HavenProgress.takeBonus(id, RUNE)) {
            ServerLevel level = (ServerLevel) player.level();
            give(player, RuneItem.stack(RuneDrops.guaranteed(level, 120.0, level.random), ModItems.RUNE.get()));
            player.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.boutique.rune.remise")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** L'eco illimite : la reserve de la couleur reste pleine, dans la ville. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && Haven.is(player.level())) {
            keepFull(player);
        }
    }

    /** Les reserves achetees sans fond, remplies si elles ont baisse. */
    public static void keepFull(ServerPlayer player) {
        HavenProgress.Entry entry = HavenProgress.peek(player.getUUID());
        if (entry == null || entry.bonus.isEmpty()) {
            return;
        }
        ItemStack gun = null;
        for (GunForm.Family family : GunForm.Family.values()) {
            if (entry.bonus.getOrDefault(unlimitedKey(family), 0) <= 0) {
                continue;
            }
            if (gun == null) {
                gun = MorphGunKeeper.find(player);
                if (gun == null || gun.isEmpty()) {
                    return;
                }
            }
            MorphGunData data = MorphGunData.of(gun);
            if (data != null && data.eco(family) < family.capacity) {
                MorphGunData.refill(gun, family, family.capacity);
            }
        }
    }

    /** Pour le banc : le catalogue tel que le joueur le verrait. */
    public static List<HavenShopPayload.Article> catalogForTest(ServerPlayer player) {
        return catalog(player);
    }

    @Nullable
    private static HavenShopPayload.Article article(ServerPlayer player, String id) {
        for (HavenShopPayload.Article a : catalog(player)) {
            if (a.id().equals(id)) {
                return a;
            }
        }
        return null;
    }
}
