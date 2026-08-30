package com.emerald.game;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * La herse du sanctuaire : une vraie porte, qui s'ouvre et se ferme.
 *
 * Elle est faite de barreaux qu'on retire par le bas et qu'on remet par le
 * haut, ce qui donne l'impression qu'elle coulisse dans sa voute. Le mouvement
 * prend une seconde et sonne comme une chaine : une porte de chateau ne
 * clignote pas.
 *
 * Pourquoi notre propre mecanisme plutot que celui de Supplementaries, dont la
 * poulie sait pourtant deplacer des blocs : parce que la porte doit aussi
 * obeir au JEU -- se fermer quand le siege commence, se rouvrir quand il est
 * gagne -- et qu'un mecanisme a redstone n'aurait pas su faire les deux. La
 * poulie, la corde et la manivelle restent posees dessus, et c'est en tournant
 * la manivelle qu'on l'actionne a la main : le mecanisme se voit, il n'est
 * simplement pas celui qui compte.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class SanctuaryGate {

    private record Gate(int half, int height, int ax, int az,
                        Direction facing, boolean open) {
    }

    /** Volatil, comme les sieges : une porte se retrouve, elle ne se sauve pas. */
    private static final Map<BlockPos, Gate> gates = new HashMap<>();

    /** Distance a laquelle une manivelle commande une porte. */
    private static final double REACH = 10.0;

    private SanctuaryGate() {
    }

    public static void register(BlockPos centre, int half, int height,
                                int ax, int az, Direction facing) {
        gates.put(centre.immutable(), new Gate(half, height, ax, az, facing, true));
    }

    public static void clearAll() {
        gates.clear();
    }

    // -------------------------------------------------------- ouvrir, fermer

    public static void close(ServerLevel level, BlockPos centre) {
        setOpen(level, centre, false);
    }

    public static void open(ServerLevel level, BlockPos centre) {
        setOpen(level, centre, true);
    }

    /** Ferme la porte du sanctuaire le plus proche : le siege commence. */
    public static void closeNearest(ServerLevel level, BlockPos near) {
        BlockPos found = nearest(near);
        if (found != null) {
            close(level, found);
        }
    }

    /** Rouvre la porte du sanctuaire le plus proche : le siege est tenu. */
    public static void openNearest(ServerLevel level, BlockPos near) {
        BlockPos found = nearest(near);
        if (found != null) {
            open(level, found);
        }
    }

    private static BlockPos nearest(BlockPos near) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : gates.keySet()) {
            double dist = pos.distSqr(near);
            if (dist < bestDist) {
                bestDist = dist;
                best = pos;
            }
        }
        // au-dela de cent blocs, ce n'est plus « la porte de ce sanctuaire »
        return best != null && bestDist <= 100.0 * 100.0 ? best : null;
    }

    private static void setOpen(ServerLevel level, BlockPos centre, boolean open) {
        Gate gate = gates.get(centre);
        if (gate == null || gate.open() == open) {
            return;
        }
        gates.put(centre, new Gate(gate.half(), gate.height(), gate.ax(), gate.az(),
                gate.facing(), open));

        if (SealDoor.available()) {
            // La VRAIE porte : celle de Cataclysm, qu'on ouvre par sa propriete
            // plutot qu'en retirant des blocs. C'est son propre mecanisme qui
            // joue, et non une imitation.
            SealDoor.setOpen(level, centre.above(), gate.facing(), open);
        } else {
            for (int a = -gate.half(); a <= gate.half(); a++) {
                for (int dy = 1; dy <= gate.height(); dy++) {
                    BlockPos pos = centre.offset(gate.ax() * a, dy, gate.az() * a);
                    level.setBlock(pos, open ? Blocks.AIR.defaultBlockState()
                            : latticeAt(a, dy), 3);
                }
            }
        }
        level.playSound(null, centre, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 2.0F,
                open ? 1.2F : 0.7F);
        level.playSound(null, centre, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.5F,
                open ? 1.1F : 0.6F);
    }

    /**
     * Le treillis de la herse, maille par maille.
     *
     * Des barreaux de fer sur toute la surface donnaient une grille de prison.
     * Une vraie herse est un TREILLIS : des montants qui pendent, des traverses
     * horizontales qui les tiennent, et des noeuds massifs a leurs croisements.
     * Le bas se termine en pointes -- c'est ce qui la rend menacante quand elle
     * retombe.
     */
    private static BlockState latticeAt(int dx, int dy) {
        BlockState beam = dark("cataclysm:black_steel_wall",
                "minecraft:polished_deepslate_wall");
        boolean crossbar = dy % 3 == 1;        // les traverses, tous les trois blocs
        boolean upright = Math.floorMod(dx, 2) == 0;   // un montant sur deux
        if (crossbar && upright) {
            return dark("cataclysm:chiseled_obsidian_bricks",
                    "minecraft:polished_deepslate");   // le noeud
        }
        if (crossbar) {
            return beam;
        }
        if (upright) {
            return Blocks.CHAIN.defaultBlockState();
        }
        return Blocks.AIR.defaultBlockState();  // les vides du treillis
    }

    /** Le premier bloc sombre disponible : on s'enrichit du modpack sans en dependre. */
    private static BlockState dark(String... ids) {
        for (String id : ids) {
            var key = net.minecraft.resources.ResourceLocation.tryParse(id);
            if (key != null && BuiltInRegistries.BLOCK.containsKey(key)) {
                return BuiltInRegistries.BLOCK.get(key).defaultBlockState();
            }
        }
        return Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    }

    // ------------------------------------------------------ la manivelle

    /**
     * Tourner la manivelle -- ou toucher la poulie -- bascule la herse.
     *
     * On accepte aussi le levier vanilla : si Supplementaries manque, le
     * sanctuaire n'a plus de manivelle, et une porte qu'on ne peut plus ouvrir
     * serait un defaut bien pire que l'absence du decor.
     */
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(Level.OVERWORLD)
                || gates.isEmpty()) {
            return;
        }
        BlockState state = level.getBlockState(event.getPos());
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        boolean handle = id.equals("supplementaries:crank")
                || id.equals("supplementaries:pulley_block")
                || state.is(Blocks.LEVER);
        if (!handle) {
            return;
        }
        for (Map.Entry<BlockPos, Gate> entry : gates.entrySet()) {
            if (entry.getKey().distSqr(event.getPos()) > REACH * REACH) {
                continue;
            }
            setOpen(level, entry.getKey(), !entry.getValue().open());
            event.getEntity().displayClientMessage(Component.translatable(
                    entry.getValue().open()
                            ? "game.emeraldweapons.gate.closing"
                            : "game.emeraldweapons.gate.opening"), true);
            return;
        }
    }
}
