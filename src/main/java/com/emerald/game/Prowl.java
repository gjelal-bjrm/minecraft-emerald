package com.emerald.game;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.List;

/**
 * LA TRAQUE : le monde reste peuple, meme en plein jour.
 *
 * Le joueur l'a dit apres une partie : « il n'y a pas assez de monstres, je
 * les cherchais ». La cause est structurelle et non un reglage : le mode se
 * joue A MIDI (WorldSetup fixe l'heure a 1000 et la meteo la retient), or
 * Minecraft ne fait apparaitre de monstres a ciel ouvert que dans le noir. Le
 * bestiaire vivait donc dans les grottes, pendant que la partie -- qui demande
 * de FARMER runes, plumes et Arcencium -- se joue en surface.
 *
 * ON PEUPLE, ON NE CHASSE PAS. La premiere version posait les monstres a
 * vingt-deux blocs et leur DONNAIT le joueur pour cible : il s'est fait
 * « harceler non-stop », et il a dit la regle qui manquait -- « c'est a moi
 * d'aller vers eux, pas a eux d'etre a cote de moi ». Trois choses ont donc
 * change, et elles font toute la difference :
 *
 *  - l'anneau s'ouvre a QUARANTE-HUIT a QUATRE-VINGT-SEIZE blocs : les monstres
 *    sont dans le PAYSAGE, pas dans le dos ;
 *  - on ne leur designe plus de cible. Ils vivent leur vie et ne remarquent le
 *    joueur que s'il approche, comme n'importe quel monstre du jeu ;
 *  - le compte se fait sur tout le rayon, si bien que le chiffre vise decrit
 *    une DENSITE de paysage et non une pression sur les epaules.
 *
 * On ne touche pas aux regles d'apparition du jeu : toutes les cinq secondes,
 * on compte les hostiles autour de chaque joueur et l'on en pose un ou deux
 * s'il en manque. Le vivier est celui des sieges (SiegeRoster), donc le modpack
 * fournit ses propres monstres quand il est la.
 *
 * TROIS GARDE-FOUS, demandes par le joueur ou mesures a l'usage :
 *
 *  - LE VILLAGE EST UNE ZONE SURE. Rien n'y apparait dans quarante-huit blocs
 *    -- mais la passe CONTINUE : les monstres se posent autour du village, pas
 *    dedans. La premiere version coupait tout des qu'on y entrait, et le
 *    paysage se vidait avec ;
 *  - les traques ne sont PAS persistants : ils s'effacent d'eux-memes, comme
 *    n'importe quel monstre naturel, et rien ne s'accumule ;
 *  - un CASQUE force sur la tete. Sans lui, la moitie du vivier vanilla brule
 *    au soleil en trois secondes -- on aurait peuple le monde de torches.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class Prowl {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** Marque un monstre de la traque : c'est aussi ce qui le fait compter. */
    public static final String TAG = "emeraldweapons_prowl";

    /** Une passe toutes les cinq secondes : assez pour suivre un joueur qui court. */
    private static final int INTERVAL = 100;
    /** Rayon dans lequel on compte les hostiles : un PAYSAGE, pas un rayon de garde. */
    private static final int RADIUS = 96;
    /**
     * L'anneau d'apparition. Quarante-huit blocs au moins : c'est la distance a
     * laquelle un monstre est une silhouette dans le decor et non une menace
     * dans le dos. Quatre-vingt-seize au plus : au-dela, le jeu l'effacerait
     * avant qu'on l'atteigne.
     */
    private static final int RING_MIN = 48;
    private static final int RING_MAX = 96;
    /** Au plus par passe et par joueur. */
    private static final int PER_PASS = 2;
    /** La ZONE SURE du village : rien n'y apparait. */
    private static final int VILLAGE_PEACE = 48;
    /** Combien de points on essaie avant d'abandonner une pose. */
    private static final int TRIES = 6;

    private Prowl() {
    }

    /** Combien d'hostiles la phase veut autour d'un joueur. */
    private static int target(GamePhase phase) {
        return switch (phase) {
            case LOBBY, PROLOGUE -> 0;          // le prologue a son siege, il suffit
            // sur un disque de quatre-vingt-seize blocs : de quoi voir des
            // silhouettes au loin, jamais une meute au coude
            case EXPLORATION -> 8;
            case MONTEE -> 12;
            case PRESSION -> 16;
            default -> 20;
        };
    }

    /** Le palier du vivier, qui suit la phase comme celui des sieges. */
    private static int tier(GamePhase phase) {
        return switch (phase) {
            case EXPLORATION -> 1;
            case MONTEE -> 2;
            default -> 3;
        };
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
                || level.getGameTime() % INTERVAL != 0
                || !ModeSwitch.enabled()) {
            return;
        }
        GameState state = GameState.get(level);
        if (state.status() != GameState.Status.RUNNING) {
            return;
        }
        GamePhase phase = state.phase(level);
        int want = target(phase);
        if (want <= 0) {
            return;
        }
        List<String> roster = SiegeRoster.forTier(tier(phase));
        if (roster.isEmpty()) {
            return;
        }
        BlockPos village = state.village();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.isCreative()) {
                continue;
            }
            AABB around = player.getBoundingBox().inflate(RADIUS);
            int nearby = level.getEntitiesOfClass(Mob.class, around,
                    mob -> mob instanceof Enemy && mob.isAlive()).size();
            int posed = 0;
            for (int i = 0; i < PER_PASS && nearby + i < want; i++) {
                if (spawnOne(level, player, roster, phase, village)) {
                    posed++;
                }
            }
            if (posed > 0) {
                LOGGER.info("Traque : {} pose(s) autour de {} (il y en avait {}, on en veut {})",
                        posed, player.getName().getString(), nearby, want);
            }
        }
    }

    private static boolean spawnOne(ServerLevel level, ServerPlayer player,
                                    List<String> roster, GamePhase phase, BlockPos village) {
        // UN ANNEAU COMPLET, ET PLUSIEURS ESSAIS. On ne vise plus le dos du
        // joueur : a cette distance, n'importe quelle direction convient. Les
        // essais servent a ecarter le village, les murs et les chunks non
        // charges sans abandonner la passe au premier refus.
        BlockPos at = null;
        for (int attempt = 0; attempt < TRIES && at == null; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double dist = RING_MIN + level.random.nextDouble() * (RING_MAX - RING_MIN);
            int x = (int) Math.round(player.getX() + Math.cos(angle) * dist);
            int z = (int) Math.round(player.getZ() + Math.sin(angle) * dist);
            BlockPos spot = new BlockPos(x, WorldSetup.surfaceY(level, x, z), z);
            if (village != null && spot.closerThan(village, VILLAGE_PEACE)) {
                continue;                       // LA ZONE SURE : jamais dans le village
            }
            if (!level.isLoaded(spot) || !level.getBlockState(spot).isAir()
                    || !level.getBlockState(spot.above()).isAir()
                    || !level.getBlockState(spot.below()).isSolid()) {
                continue;                       // jamais de generation forcee, jamais dans un mur
            }
            at = spot;
        }
        if (at == null) {
            return false;
        }
        EntityType<?> type = EntityType.byString(roster.get(level.random.nextInt(roster.size())))
                .orElse(null);
        if (type == null) {
            return false;
        }
        Entity entity = type.spawn(level, at, MobSpawnType.NATURAL);
        if (!(entity instanceof Mob mob)) {
            return false;
        }
        mob.addTag(TAG);
        // LE CASQUE, sans quoi la moitie du vivier brule au soleil. Vanilla
        // epargne un mort-vivant coiffe : c'est le casque qui prend le feu.
        if (mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        }
        MobGear.equip(mob, MobGear.stage(level, tier(phase)), level.random);
        // AUCUNE CIBLE DONNEE, et c'est la correction demandee : un monstre a
        // qui l'on designe le joueur traverse le paysage pour venir le mordre,
        // et le joueur se fait harceler sans avoir rien cherche. Celui-ci vit
        // sa vie ; il faut aller le chercher.
        //
        // PAS de setPersistenceRequired non plus : il s'efface de lui-meme.
        return true;
    }
}
