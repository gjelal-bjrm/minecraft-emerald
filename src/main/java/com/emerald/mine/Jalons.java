package com.emerald.mine;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
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
