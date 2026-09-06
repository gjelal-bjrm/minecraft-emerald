package com.emerald.mine;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * UN JALON : une silhouette lumineuse dans la roche, pour un temps donne.
 *
 * C'est la technique des filons de l'Aurore, sortie pour servir a tous : la
 * lueur d'entite est dessinee A TRAVERS LES MURS, et rien d'autre dans le jeu
 * ne le fait pour un bloc. Un porte-armure petit, invisible, sans corps
 * (marqueur), luisant : le rendu ne dessine plus que son contour.
 *
 * Chaque jalon porte SA date de mort : il s'efface seul, sans qu'un systeme
 * ait a se souvenir de lui. Et l'on releve avant d'effacer -- la vue des
 * entites rend des nulls si on la modifie en la parcourant.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class Jalons {

    public static final String TAG = "emeraldweapons_jalon";
    private static final String DIE_AT = "ArcenciumJalonDieAt";

    private Jalons() {
    }

    public static void place(ServerLevel level, BlockPos pos, int ticks) {
        ArmorStand mark = new ArmorStand(level, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5);
        mark.setInvisible(true);
        mark.setNoGravity(true);
        mark.setSilent(true);
        mark.setInvulnerable(true);
        mark.setNoBasePlate(true);
        mark.setGlowingTag(true);
        mark.addTag(TAG);
        mark.getPersistentData().putLong(DIE_AT, level.getGameTime() + ticks);
        // `setSmall` et `setMarker` sont prives : on passe par la sauvegarde,
        // qui est publique et complete
        CompoundTag tag = new CompoundTag();
        mark.saveWithoutId(tag);
        tag.putBoolean("Marker", true);
        tag.putBoolean("Small", true);
        mark.load(tag);
        level.addFreshEntity(mark);
    }

    /**
     * UN BLOC ENTIER DETOURE, et non un point.
     *
     * Le porte-armure marqueur n'a pas de corps : son contour tient en un pixel,
     * et l'on ne voyait rien la ou l'on croyait signaler un etabli (capture a
     * l'appui). Un `block_display` porte, lui, la forme du bloc : agrandi d'un
     * centieme et pose dessus, il en dessine le contour exact -- a travers les
     * murs, comme toute lueur d'entite, sans collision et sans rien cacher.
     *
     * `glow_color_override` donne la couleur sans passer par une equipe de
     * tableau d'affichage : c'est le seul moyen d'avoir deux jalons de couleurs
     * differentes en meme temps.
     */
    public static void glow(ServerLevel level, BlockPos pos, BlockState state, int ticks, int colour) {
        Entity display = EntityType.BLOCK_DISPLAY.create(level);
        if (display == null) {
            return;
        }
        display.setPos(pos.getX(), pos.getY(), pos.getZ());
        CompoundTag tag = new CompoundTag();
        display.saveWithoutId(tag);
        tag.put("block_state", NbtUtils.writeBlockState(state));
        tag.putInt("glow_color_override", colour);
        tag.putFloat("view_range", 4.0F);           // visible de loin, pas seulement de pres
        CompoundTag shape = new CompoundTag();
        shape.put("translation", vec(-0.005, -0.005, -0.005));
        shape.put("scale", vec(1.01, 1.01, 1.01));
        shape.put("left_rotation", quat());
        shape.put("right_rotation", quat());
        tag.put("transformation", shape);
        display.load(tag);
        display.setGlowingTag(true);
        display.addTag(TAG);
        display.getPersistentData().putLong(DIE_AT, level.getGameTime() + ticks);
        level.addFreshEntity(display);
    }

    private static net.minecraft.nbt.ListTag vec(double x, double y, double z) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        list.add(net.minecraft.nbt.FloatTag.valueOf((float) x));
        list.add(net.minecraft.nbt.FloatTag.valueOf((float) y));
        list.add(net.minecraft.nbt.FloatTag.valueOf((float) z));
        return list;
    }

    private static net.minecraft.nbt.ListTag quat() {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (float f : new float[]{0F, 0F, 0F, 1F}) {
            list.add(net.minecraft.nbt.FloatTag.valueOf(f));
        }
        return list;
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 10 != 0
                || !level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        long now = level.getGameTime();
        List<Entity> dead = new ArrayList<>();
        for (Entity entity : level.getEntities().getAll()) {
            if (entity != null && entity.getTags().contains(TAG)
                    && entity.getPersistentData().getLong(DIE_AT) <= now) {
                dead.add(entity);
            }
        }
        for (Entity entity : dead) {
            entity.discard();
        }
    }
}
