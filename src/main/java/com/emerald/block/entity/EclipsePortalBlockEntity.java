package com.emerald.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Un portail de l'Eclipse (EclipsePortalBlock). Il ne garde rien : c'est weather/Eclipse
 * qui sait quels portails sont ouverts. Des que l'Eclipse ne le reconnait plus -- elle
 * est finie, ou le serveur a redemarre en pleine Eclipse et a tout oublie --, il s'efface
 * de lui-meme.
 *
 * Cote client, les portails charges sont retenus dans {@link #CLIENT_OPEN} : le dessin
 * (client/EclipsePortalRenderer) y oublie l'ouverture des portails deja refermes.
 */
public class EclipsePortalBlockEntity extends BlockEntity {

    /** Les portails charges chez le client. */
    public static final Set<BlockPos> CLIENT_OPEN = ConcurrentHashMap.newKeySet();

    private static final int CHECK = 20;

    public EclipsePortalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ECLIPSE_PORTAL.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, EclipsePortalBlockEntity portal) {
        if (level.getGameTime() % CHECK == 0 && level instanceof ServerLevel server
                && !com.emerald.weather.Eclipse.isRift(pos)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            server.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 2.0,
                    pos.getZ() + 0.5, 16, 0.6, 1.2, 0.2, 0.01);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && this.level.isClientSide) {
            CLIENT_OPEN.add(this.worldPosition.immutable());
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && this.level.isClientSide) {
            CLIENT_OPEN.remove(this.worldPosition);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (this.level != null && this.level.isClientSide) {
            CLIENT_OPEN.remove(this.worldPosition);
        }
    }
}
