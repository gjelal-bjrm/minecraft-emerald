package com.emerald.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * La Porte du Sceau de Cataclysm, posee bloc par bloc.
 *
 * C'est LA porte demandee depuis le debut -- la grande porte de forteresse du
 * Nether, celle qu'on ouvre par un mecanisme. Je l'ai contournee trois fois en
 * fabriquant des herses a la main, faute de savoir la monter : elle n'a aucun
 * etat visible dans son fichier de blocs, et rien n'indiquait ses dimensions.
 *
 * La reponse etait dans le NBT de la Prison Givree, ou elle figure une fois. En
 * voici le releve exact :
 *
 * <pre>
 *   cinq blocs de large, huit de haut, un seul d'epaisseur
 *
 *   y+7   end_right/7  side_right/7  center/7  side_left/7  end_left/7
 *    ...        ...          ...        ...        ...         ...
 *   y+0   end_right/0  side_right/0  PORTE      side_left/0  end_left/0
 * </pre>
 *
 * Le bloc du bas au centre est {@code door_of_seal}, la commande ; les
 * trente-neuf autres sont des {@code door_of_seal_part} portant leur rangee
 * dans {@code y_offset} et leur colonne dans {@code door_part}. Tous partagent
 * {@code facing} et {@code open}.
 *
 * Les proprietes sont posees PAR LEUR NOM, en interrogeant la definition
 * d'etats du bloc : Cataclysm n'est pas une dependance de compilation, et n'a
 * pas besoin de le devenir pour cela.
 */
public final class SealDoor {

    /** Largeur et hauteur, telles que relevees. */
    public static final int WIDTH = 5;
    public static final int HEIGHT = 8;

    private static final String DOOR = "cataclysm:door_of_seal";
    private static final String PART = "cataclysm:door_of_seal_part";

    /** Les colonnes, de la droite de la porte vers sa gauche. */
    private static final String[] COLUMNS = {
            "end_right", "side_right", "center", "side_left", "end_left",
    };

    private SealDoor() {
    }

    /** Vrai si Cataclysm fournit la porte : sinon, le sanctuaire se rabat. */
    public static boolean available() {
        return block(DOOR) != null && block(PART) != null;
    }

    private static Block block(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.BLOCK.containsKey(key)) {
            return null;
        }
        return BuiltInRegistries.BLOCK.get(key);
    }

    /**
     * Pose la porte, son bloc du bas au centre en {@code base}.
     *
     * @param facing la direction vers laquelle elle regarde -- vers le dehors
     */
    public static void place(ServerLevel level, BlockPos base, Direction facing, boolean open) {
        Block door = block(DOOR);
        Block part = block(PART);
        if (door == null || part == null) {
            return;
        }
        // Le releve donne end_right du cote ANTIHORAIRE du regard : pour une
        // porte tournee a l'est, il est au nord. On suit donc cet axe.
        Direction across = facing.getCounterClockWise();

        for (int row = 0; row < HEIGHT; row++) {
            for (int k = 2; k >= -2; k--) {
                BlockPos pos = base.above(row).relative(across, k);
                boolean isDoor = row == 0 && k == 0;
                BlockState state = isDoor ? door.defaultBlockState()
                        : part.defaultBlockState();
                state = with(state, "facing", facing.getSerializedName());
                state = with(state, "open", Boolean.toString(open));
                if (!isDoor) {
                    state = with(state, "y_offset", Integer.toString(row));
                    state = with(state, "door_part", COLUMNS[2 - k]);
                } else {
                    state = with(state, "lit", "false");
                }
                level.setBlock(pos, state, 2);
            }
        }
    }

    /** Ouvre ou ferme une porte deja posee, sans la reconstruire. */
    public static void setOpen(ServerLevel level, BlockPos base, Direction facing, boolean open) {
        Direction across = facing.getCounterClockWise();
        for (int row = 0; row < HEIGHT; row++) {
            for (int k = 2; k >= -2; k--) {
                BlockPos pos = base.above(row).relative(across, k);
                BlockState state = level.getBlockState(pos);
                String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                if (!id.equals(DOOR) && !id.equals(PART)) {
                    continue;                  // quelqu'un a casse un morceau
                }
                level.setBlock(pos, with(state, "open", Boolean.toString(open)), 3);
            }
        }
    }

    /**
     * Pose une propriete par son NOM.
     *
     * C'est ce qui evite de faire de Cataclysm une dependance de compilation :
     * on demande au bloc la liste de ses proprietes et on cherche celle qui
     * porte ce nom, au lieu de citer une constante de son code.
     */
    private static BlockState with(BlockState state, String name, String value) {
        for (Property<?> property : state.getBlock().getStateDefinition().getProperties()) {
            if (property.getName().equals(name)) {
                return apply(state, property, value);
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState apply(BlockState state,
                                                             Property<T> property,
                                                             String value) {
        return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
    }
}
