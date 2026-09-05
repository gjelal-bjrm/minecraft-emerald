package com.emerald.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * LA BRUME ETOILEE : un nuage d'etoiles qu'on traverse, et qui vous emporte.
 *
 * Elles viennent par PAIRES, pendant l'Aurore, dans les grottes -- souvent
 * l'une en profondeur, l'autre plus haut. Entrer dans l'une, c'est se
 * dissoudre en poussiere d'etoiles et etre porte, sur un arc de lumiere, a
 * travers la roche s'il le faut, jusqu'a l'autre (mine/AuroreCaves). Dans les
 * deux sens. Personne ne les pose : elles apparaissent, et elles s'en vont
 * avec la meteo.
 *
 * Le bloc ne fait qu'une chose : dire au sous-sol qu'un joueur est dedans.
 * Tout le reste -- le depart, l'arc, l'arrivee, le retour interdit tant qu'on
 * n'est pas ressorti -- est affaire du sous-sol, qui connait les paires.
 */
public class StarMistBlock extends Block {

    public StarMistBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level instanceof ServerLevel server && entity instanceof ServerPlayer player) {
            com.emerald.mine.AuroreCaves.touch(server, player, pos);
        }
    }
}
