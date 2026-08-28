package com.emerald.game;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.BlockPos;
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

    private record Gate(int half, int height, boolean open) {
    }

    /** Volatil, comme les sieges : une porte se retrouve, elle ne se sauve pas. */
    private static final Map<BlockPos, Gate> gates = new HashMap<>();

    /** Distance a laquelle une manivelle commande une porte. */
    private static final double REACH = 10.0;

    private SanctuaryGate() {
    }

    public static void register(ServerLevel level, BlockPos centre, int half, int height) {
        gates.put(centre.immutable(), new Gate(half, height, true));
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
        gates.put(centre, new Gate(gate.half(), gate.height(), open));

        BlockState bars = Blocks.IRON_BARS.defaultBlockState();
        for (int dx = -gate.half(); dx <= gate.half(); dx++) {
            for (int dy = 1; dy <= gate.height(); dy++) {
                BlockPos pos = centre.offset(dx, dy, 0);
                level.setBlock(pos, open ? Blocks.AIR.defaultBlockState() : bars, 3);
            }
        }
        level.playSound(null, centre, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 2.0F,
                open ? 1.2F : 0.7F);
        level.playSound(null, centre, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.5F,
                open ? 1.1F : 0.6F);
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
