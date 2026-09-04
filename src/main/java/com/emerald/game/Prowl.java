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
import net.minecraft.world.phys.Vec3;
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
 * On ne touche pas aux regles d'apparition du jeu : on ajoute une PRESSION.
 * Toutes les cinq secondes, on compte les hostiles autour de chaque joueur ;
 * s'il en manque, on en pose un a deux, hors de vue, sur un anneau de vingt-deux
 * a trente-huit blocs. Le nombre vise monte avec la phase -- la partie se
 * durcit d'elle-meme -- et le vivier est celui des sieges (SiegeRoster), donc
 * le modpack fournit ses propres monstres quand il est la.
 *
 * TROIS GARDE-FOUS, tous mesures a l'usage :
 *
 *  - le village est EPARGNE dans vingt blocs : on doit pouvoir forger et
 *    monter sa specialisation sans se battre ;
 *  - les traques ne sont PAS persistants : ils disparaissent d'eux-memes quand
 *    le joueur s'eloigne, comme n'importe quel monstre naturel, et rien ne
 *    s'accumule ;
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
    /** Rayon dans lequel on compte les hostiles. */
    private static final int RADIUS = 40;
    /** L'anneau d'apparition : au-dela de la vue immediate, en deca de la distance de simulation. */
    private static final int RING_MIN = 22;
    private static final int RING_MAX = 38;
    /** Au plus par passe et par joueur : la pression monte, elle ne submerge pas. */
    private static final int PER_PASS = 2;
    /** Le village qu'on epargne, en blocs. */
    private static final int VILLAGE_PEACE = 20;

    private Prowl() {
    }

    /** Combien d'hostiles la phase veut autour d'un joueur. */
    private static int target(GamePhase phase) {
        return switch (phase) {
            case LOBBY, PROLOGUE -> 0;          // le prologue a son siege, il suffit
            case EXPLORATION -> 10;
            case MONTEE -> 14;
            case PRESSION -> 18;
            default -> 22;
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
            if (village != null && player.blockPosition().closerThan(village, VILLAGE_PEACE)) {
                continue;                       // on forge en paix
            }
            AABB around = player.getBoundingBox().inflate(RADIUS);
            int nearby = level.getEntitiesOfClass(Mob.class, around,
                    mob -> mob instanceof Enemy && mob.isAlive()).size();
            int posed = 0;
            for (int i = 0; i < PER_PASS && nearby + i < want; i++) {
                if (spawnOne(level, player, roster, phase)) {
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
                                    List<String> roster, GamePhase phase) {
        // DERRIERE LE JOUEUR, de preference : un monstre qui se materialise dans
        // le champ de vision se lit comme une triche, meme quand il est loin.
        Vec3 look = player.getLookAngle();
        double away = Math.atan2(look.z, look.x) + Math.PI;
        double angle = away + (level.random.nextDouble() - 0.5) * Math.PI * 1.1;
        double dist = RING_MIN + level.random.nextDouble() * (RING_MAX - RING_MIN);
        int x = (int) Math.round(player.getX() + Math.cos(angle) * dist);
        int z = (int) Math.round(player.getZ() + Math.sin(angle) * dist);
        BlockPos at = new BlockPos(x, WorldSetup.surfaceY(level, x, z), z);
        if (!level.isLoaded(at) || !level.getBlockState(at).isAir()
                || !level.getBlockState(at.above()).isAir()
                || !level.getBlockState(at.below()).isSolid()) {
            return false;                       // jamais de generation forcee, jamais dans un mur
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
        mob.setTarget(player);
        // PAS de setPersistenceRequired : ils s'effacent quand on s'eloigne,
        // exactement comme un monstre naturel. Rien ne s'accumule.
        return true;
    }
}
