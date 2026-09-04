package com.emerald.hero;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Ce qui fait monter le niveau Heros, et ce que la fiche change.
 *
 * L'experience vient du COMBAT et des objectifs, jamais du temps qui passe :
 * une progression qui monte toute seule recompense la patience, or ce mode dure
 * une heure et doit recompenser l'action.
 *
 * Les statistiques s'appliquent par des modificateurs d'attribut poses sur le
 * joueur et refaits quand la fiche change. On les REPOSE plutot que de les
 * ajuster : un modificateur porte un identifiant, le remplacer est sans risque,
 * et l'on evite ainsi la classe de bogues ou un bonus s'empile a chaque
 * connexion.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HeroEvents {

    private static final ResourceLocation ATTACK_ID =
            ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "hero_attack");
    private static final ResourceLocation ARMOR_ID =
            ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "hero_armor");
    private static final ResourceLocation HEALTH_ID =
            ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "hero_health");

    /** Tous les combien on verifie que la fiche est bien appliquee. */
    private static final int REFRESH = 40;

    private HeroEvents() {
    }

    // ------------------------------------------------------------ l'experience

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        // La valeur suit ce que la creature COUTE, non ce qu'elle est : ses
        // points de vie maximaux sont la seule mesure comparable d'un mod a
        // l'autre, et elle range d'elle-meme un boss au-dessus d'un zombie.
        int worth = 2 + (int) Math.round(victim.getMaxHealth() / 4.0);
        // LA BATTUE REND LA MOITIE EN PLUS : la fenetre de chasse doit se
        // sentir sur la fiche du personnage, pas seulement dans le sac.
        if (com.emerald.weather.WeatherManager.current()
                == com.emerald.weather.Weather.BATTUE) {
            worth = (int) Math.round(worth * 1.5);
        }
        award(player, Math.min(160, worth));
    }

    /** Une recompense franche, pour un objectif franc. */
    public static void award(ServerPlayer player, int amount) {
        int gained = HeroLevel.grant(player, amount);
        sync(player);                 // la jauge bouge a chaque coup, pas tous les deux secondes
        if (gained <= 0) {
            return;
        }
        int level = HeroLevel.level(player);
        player.sendSystemMessage(Component.translatable(
                        "hero.emeraldweapons.levelled", level, HeroLevel.free(player))
                .withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.7F, 1.2F);
    }

    /**
     * Le cadeau des ancres : des niveaux, annonces en grand.
     *
     * Il passe par un titre plein ecran et non par une ligne de tchat, parce
     * qu'il tombe au moment ou le joueur regarde son ancre s'allumer : une
     * ligne de tchat s'y perdrait au milieu des annonces du siege.
     */
    public static void awardLevels(ServerPlayer player, int levels) {
        int gained = HeroLevel.grantLevels(player, levels);
        if (gained <= 0) {
            return;
        }
        apply(player);
        sync(player);
        player.sendSystemMessage(Component.translatable(
                        "hero.emeraldweapons.levels_granted", gained,
                        HeroLevel.level(player), HeroLevel.free(player))
                .withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.9F);
    }

    // -------------------------------------------------------------- les effets

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.level().getGameTime() % REFRESH != 0) {
            return;
        }
        apply(player);
        sync(player);
    }

    @SubscribeEvent
    public static void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            apply(player);
            sync(player);
        }
    }

    /**
     * Envoie la fiche au client.
     *
     * Sans cet envoi, rien ne s'affiche : les points vivent dans les donnees
     * persistantes, qui ne traversent jamais le reseau. C'est la meme panne que
     * la jauge de Rage avait connue, et elle se repare de la meme facon --
     * l'etat descend explicitement.
     *
     * On envoie a chaque tick de rafraichissement plutot qu'aux seuls
     * changements : huit entiers toutes les deux secondes ne se mesurent pas,
     * et la moindre voie oubliee dans une liste de notifications produirait une
     * fiche mensongere, ce qui est bien pire.
     */
    public static void sync(ServerPlayer player) {
        int level = HeroLevel.level(player);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new com.emerald.network.HeroSyncPayload(
                        level, HeroLevel.xp(player), HeroLevel.needed(level),
                        HeroLevel.free(player),
                        HeroLevel.path(player, HeroStat.ATTAQUE),
                        HeroLevel.path(player, HeroStat.ELEMENT),
                        HeroLevel.path(player, HeroStat.DEFENSE),
                        HeroLevel.path(player, HeroStat.VITALITE),
                        HeroLevel.effective(player, HeroStat.ATTAQUE)
                                - HeroLevel.path(player, HeroStat.ATTAQUE),
                        HeroLevel.effective(player, HeroStat.ELEMENT)
                                - HeroLevel.path(player, HeroStat.ELEMENT),
                        HeroLevel.effective(player, HeroStat.DEFENSE)
                                - HeroLevel.path(player, HeroStat.DEFENSE),
                        HeroLevel.effective(player, HeroStat.VITALITE)
                                - HeroLevel.path(player, HeroStat.VITALITE)));
    }

    /**
     * Repose les trois modificateurs de la fiche.
     *
     * L'Element ne passe pas par un attribut : il n'existe aucun attribut
     * vanilla pour « la force de nos procs ». Il se lit donc directement par
     * les armes du mode, la ou elles calculent leurs effets -- voir
     * {@link #elementBonus}.
     */
    public static void apply(Player player) {
        int vitality = HeroLevel.effective(player, HeroStat.VITALITE);

        // LA VITALITE DEBORDE SUR LES DEUX AUTRES, comme la voie HP/MP de
        // NosTale, dont les paliers donnent de la puissance d'attaque et de la
        // defense. C'est ce qui l'empeche d'etre la voie qu'on prend faute de
        // mieux : elle ne fait rien mieux que les autres, elle fait un peu des
        // trois, et c'est un choix defendable plutot qu'un lot de consolation.
        set(player.getAttribute(Attributes.ATTACK_DAMAGE), ATTACK_ID,
                HeroStat.ATTAQUE.value(HeroLevel.effective(player, HeroStat.ATTAQUE))
                        + HeroStat.VITALITE.bonus(HeroBonus.ATTACK_FLAT, vitality));
        set(player.getAttribute(Attributes.ARMOR), ARMOR_ID,
                HeroStat.DEFENSE.value(HeroLevel.effective(player, HeroStat.DEFENSE))
                        + HeroStat.VITALITE.bonus(HeroBonus.ARMOR_FLAT, vitality));

        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        set(health, HEALTH_ID, HeroStat.VITALITE.value(vitality));
        if (health != null && player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static void set(AttributeInstance attribute, ResourceLocation id, double value) {
        if (attribute == null) {
            return;
        }
        AttributeModifier had = attribute.getModifier(id);
        if (had != null && had.amount() == value) {
            return;                       // rien a refaire : on ne remue pas pour rien
        }
        attribute.removeModifier(id);
        if (value > 0.0) {
            attribute.addPermanentModifier(new AttributeModifier(id, value,
                    AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /**
     * Le multiplicateur d'Element, a lire par les armes du mode.
     *
     * Rendu en fraction : 1,0 sans aucun point, 1,96 au plafond d'une voie.
     * C'est volontairement le gain le plus fort des quatre, parce que c'est le
     * seul qui ne serve qu'aux armes du mode -- il n'aide pas a porter une
     * epee en fer.
     */
    public static double elementBonus(Player player) {
        return 1.0 + HeroStat.ELEMENT.value(HeroLevel.effective(player, HeroStat.ELEMENT)) / 100.0;
    }
}
