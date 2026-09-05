package com.emerald.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * LE PUITS D'AURORE : un bloc de lumiere qu'on traverse.
 *
 * Pendant l'Aurore, des colonnes de ces blocs se levent dans les grottes, du
 * sol au plafond (mine/AuroreCaves). Ce sont des BLOCS et non des particules :
 * on les voit de l'autre bout d'une caverne, pas seulement a trente-deux
 * blocs, et ils survivent a n'importe quel pack de shaders.
 *
 * Dedans, on ne tombe pas : ON MONTE, et l'on descend en s'accroupissant --
 * comme une colonne de bulles, dont c'est exactement la mecanique. La chute
 * est remise a zero a chaque tique : sauter dans une colonne depuis n'importe
 * quelle hauteur ne coute rien. Le mouvement est pose des DEUX cotes (le
 * client le predit, le serveur remet la chute a zero) : c'est ce que fait la
 * colonne de bulles, et c'est ce qui evite tout a-coup.
 *
 * Incassable, sans butin, sans forme de collision : ce n'est pas un bloc
 * qu'on possede, c'est un phenomene qui passe.
 */
public class AuroreLightBlock extends Block {

    /** Monter, ou descendre accroupi. Doux : c'est une lumiere, pas un ascenseur. */
    private static final double RISE = 0.22;
    private static final double SINK = -0.14;

    public AuroreLightBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return;
        }
        Vec3 motion = entity.getDeltaMovement();
        double vy = entity.isShiftKeyDown() ? SINK : RISE;
        entity.setDeltaMovement(motion.x * 0.85, vy, motion.z * 0.85);
        entity.resetFallDistance();
    }
}
