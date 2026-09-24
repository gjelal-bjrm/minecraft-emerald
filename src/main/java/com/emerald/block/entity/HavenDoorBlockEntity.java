package com.emerald.block.entity;

import com.emerald.block.HavenDoorBlock;
import com.emerald.haven.door.HavenDoorFrame;
import com.emerald.haven.door.HavenDoorKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Le controleur d'une porte de Jak 3 (cahier §95) : ou elle se tient, si elle s'ouvre, et ou
 * en sont ses battants.
 *
 * COMME DANS LE JEU (airlock.gc) : la porte s'ouvre toute seule quand un joueur arrive devant
 * ou derriere elle, a quelques blocs, et se referme une seconde apres le depart du dernier ;
 * elle se referme deux fois plus vite qu'elle ne s'ouvre. Le passage se libere a mesure que les
 * battants s'ecartent, cellule par cellule (HavenDoorKind) -- on ne traverse pas une porte qui
 * a l'air fermee -- et se bouche des qu'elle se referme. Ce que les battants ouverts couvrent
 * encore (les bords du sas) reste plein.
 *
 * Le serveur tient la cible (ouverte ou fermee) et l'envoie au client ; chacun fait avancer les
 * battants de son cote, a la meme vitesse.
 */
public class HavenDoorBlockEntity extends BlockEntity {

    /** Une seconde sans personne devant, et la porte se referme. */
    private static final int CLOSE_DELAY = 20;
    /** 2|16 : ni voisins prevenus, ni formes recalculees. */
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** Le centre au sol de la porte, relatif au coin du bloc-controleur, et son lacet. */
    private double cx = 0.5;
    private double cy = 0.0;
    private double cz = 0.5;
    private float yaw;
    /** Ouverte, ou qui s'ouvre ; sinon fermee, ou qui se ferme. */
    private boolean target;
    /** Ou en sont les battants, de 0 (fermee) a 1 (ouverte) ; et la derniere tique comptee, au client. */
    private float progress;
    private double lastTime = Double.NaN;
    private int idle;
    /** La course a laquelle les cellules ont ete ouvertes la derniere fois ; 0 : toutes fermees. */
    private float openedAt;

    public HavenDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HAVEN_DOOR.get(), pos, state);
    }

    public HavenDoorKind kind() {
        return this.getBlockState().getValue(HavenDoorBlock.KIND);
    }

    /** La porte dans le monde, d'apres son controleur. */
    public HavenDoorFrame frame() {
        BlockPos p = this.getBlockPos();
        return new HavenDoorFrame(kind(), p.getX() + this.cx, p.getY() + this.cy, p.getZ() + this.cz, this.yaw);
    }

    /** Pose la porte : son centre au sol et son lacet (a la pose seulement). */
    public void place(HavenDoorFrame frame) {
        BlockPos p = this.getBlockPos();
        this.cx = frame.x() - p.getX();
        this.cy = frame.y() - p.getY();
        this.cz = frame.z() - p.getZ();
        this.yaw = frame.yaw();
        this.setChanged();
    }

    public double centerX() {
        return this.cx;
    }

    public double centerY() {
        return this.cy;
    }

    public double centerZ() {
        return this.cz;
    }

    public float yaw() {
        return this.yaw;
    }

    /** Ou en sont les battants a l'image, de 0 a 1 : ils avancent a chaque appel, d'apres le temps du jeu. */
    public float shown(float partialTick) {
        if (this.level == null) {
            return this.progress;
        }
        double now = this.level.getGameTime() + partialTick;
        if (!Double.isNaN(this.lastTime)) {
            double dt = Math.max(0.0, Math.min(10.0, now - this.lastTime));
            this.progress = step(this.progress, this.target, dt, kind());
        }
        this.lastTime = now;
        return this.progress;
    }

    private static float step(float progress, boolean target, double ticks, HavenDoorKind kind) {
        double speed = (target ? 1.0 : 2.0) / kind.duration;
        double next = progress + (target ? speed : -speed) * ticks;
        return (float) Math.max(0.0, Math.min(1.0, next));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HavenDoorBlockEntity door) {
        HavenDoorFrame frame = door.frame();
        HavenDoorKind kind = frame.kind();
        double reach = kind.width / 2.0 + kind.trigger + 1.0;
        AABB zone = new AABB(frame.x() - reach, frame.y() - 2.0, frame.z() - reach,
                frame.x() + reach, frame.y() + kind.height + 1.0, frame.z() + reach);
        boolean someone = false;
        for (Player player : level.getEntitiesOfClass(Player.class, zone)) {
            if (!player.isSpectator() && frame.triggers(player.getX(), player.getY(), player.getZ())) {
                someone = true;
                break;
            }
        }
        if (someone) {
            door.trigger(level);
        } else if (door.target && ++door.idle > CLOSE_DELAY) {
            door.setTarget(level, frame, false);
        }
        door.progress = step(door.progress, door.target, 1.0, kind);
        // le passage se libere a mesure que les battants s'ecartent, et se bouche des que la
        // porte se referme
        if (door.target && door.progress > door.openedAt) {
            door.openCells(level, frame, door.progress);
        } else if (!door.target && door.openedAt > 0.0F) {
            door.closeCells(level, frame);
        }
    }

    /** Quelqu'un est devant la porte a cette tique : elle s'ouvre, ou reste ouverte une seconde de plus. */
    public void trigger(Level level) {
        this.idle = 0;
        if (!this.target) {
            setTarget(level, frame(), true);
        }
    }

    private void setTarget(Level level, HavenDoorFrame frame, boolean open) {
        this.target = open;
        HavenDoorKind kind = frame.kind();
        level.playSound(null, frame.x(), frame.y() + 1.0, frame.z(), open ? kind.openSound : kind.closeSound,
                SoundSource.BLOCKS, kind == HavenDoorKind.SAS ? 2.0F : 1.0F, kind == HavenDoorKind.SAS ? 0.6F : 0.9F);
        this.setChanged();
        level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Ouvre les cellules que les battants ont degagees a cette course. */
    private void openCells(Level level, HavenDoorFrame frame, float progress) {
        this.openedAt = progress;
        for (BlockPos cell : frame.cells()) {
            BlockState state = level.getBlockState(cell);
            if (state.getBlock() instanceof HavenDoorBlock && !state.getValue(HavenDoorBlock.OPEN)
                    && frame.passable(cell, progress)) {
                level.setBlock(cell, state.setValue(HavenDoorBlock.OPEN, true), QUIET);
            }
        }
    }

    private void closeCells(Level level, HavenDoorFrame frame) {
        this.openedAt = 0.0F;
        for (BlockPos cell : frame.cells()) {
            BlockState state = level.getBlockState(cell);
            if (state.getBlock() instanceof HavenDoorBlock && state.getValue(HavenDoorBlock.OPEN)) {
                level.setBlock(cell, state.setValue(HavenDoorBlock.OPEN, false), QUIET);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("CentreX", this.cx);
        tag.putDouble("CentreY", this.cy);
        tag.putDouble("CentreZ", this.cz);
        tag.putFloat("Lacet", this.yaw);
        tag.putBoolean("Ouverte", this.target);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("CentreX")) {
            this.cx = tag.getDouble("CentreX");
            this.cy = tag.getDouble("CentreY");
            this.cz = tag.getDouble("CentreZ");
            this.yaw = tag.getFloat("Lacet");
        }
        this.target = tag.getBoolean("Ouverte");
        // au rechargement la porte est la ou le serveur la veut, et la tique suivante ouvre tout
        // son passage (elle a pu etre sauvee a mi-course) ; au client elle glisse depuis la
        if (this.level == null || !this.level.isClientSide) {
            this.progress = this.target ? 1.0F : 0.0F;
            this.openedAt = 0.0F;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Pour le banc : la cible et la course, cote serveur. */
    public boolean target() {
        return this.target;
    }

    public float progress() {
        return this.progress;
    }

    /** Les cellules de collision sont-elles ouvertes, ou en train de s'ouvrir ? (cote serveur) */
    public boolean cellsOpen() {
        return this.openedAt > 0.0F;
    }
}
