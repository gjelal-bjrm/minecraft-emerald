package com.emerald.haven.door;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Ou une porte se tient dans le monde (cahier §95) : son centre au sol, son lacet, sa sorte --
 * et de la, les cellules qu'elle occupe et la zone qui l'ouvre.
 *
 * LE REPERE DU MODELE : la largeur le long de +x, la hauteur le long de +y depuis le sol, la face
 * avant vers +z. Le lacet est celui de Minecraft (sud 0, ouest 90, nord 180, est 270) : la face
 * avant regarde (-sin, cos), la largeur court le long de (cos, sin). Une porte peut etre EN
 * BIAIS : celle du bar de Jak 3 fait 33,6 degres. Ses blocs de collision sont alors les
 * cellules dont le centre tombe dans la lame de la porte fermee, un escalier de blocs en
 * diagonale qui ferme le passage. La lame a l'epaisseur de la porte : un bloc, quatre pour le
 * sas (on ne passe pas a travers ses battants par le cote).
 *
 * Le bloc-controleur est celui qui contient le centre au sol (une largeur paire met le centre
 * sur une arete : il tombe dans le bloc du cote +x local).
 */
public record HavenDoorFrame(HavenDoorKind kind, double x, double y, double z, float yaw) {

    /** Le bloc-controleur : celui qui contient le centre au sol. */
    public BlockPos controller() {
        return BlockPos.containing(this.x + lateralX() * 1.0e-3, this.y + 1.0e-3, this.z + lateralZ() * 1.0e-3);
    }

    /** La composante x de la direction de la largeur (le +x du modele). */
    public double lateralX() {
        return Math.cos(Math.toRadians(this.yaw));
    }

    public double lateralZ() {
        return Math.sin(Math.toRadians(this.yaw));
    }

    /** La composante x de la direction de la face avant (le +z du modele). */
    public double frontX() {
        return -Math.sin(Math.toRadians(this.yaw));
    }

    public double frontZ() {
        return Math.cos(Math.toRadians(this.yaw));
    }

    /** Un point du monde dans le repere du modele : (le long de la largeur, la hauteur, devant). */
    public Vec3 local(double wx, double wy, double wz) {
        double dx = wx - this.x;
        double dz = wz - this.z;
        return new Vec3(dx * lateralX() + dz * lateralZ(), wy - this.y, dx * frontX() + dz * frontZ());
    }

    /** Toutes les cellules de la porte fermee, controleur compris. */
    public List<BlockPos> cells() {
        List<BlockPos> cells = new ArrayList<>();
        double half = this.kind.width / 2.0;
        double thick = this.kind.depth / 2.0;
        int reach = (int) Math.ceil(half + thick) + 1;
        int cx = (int) Math.floor(this.x);
        int cz = (int) Math.floor(this.z);
        int y0 = (int) Math.floor(this.y + 1.0e-3);
        for (int y = y0; y < y0 + this.kind.height; y++) {
            for (int gx = cx - reach; gx <= cx + reach; gx++) {
                for (int gz = cz - reach; gz <= cz + reach; gz++) {
                    Vec3 p = local(gx + 0.5, y, gz + 0.5);
                    if (Math.abs(p.x) < half - 1.0e-6 && Math.abs(p.z) < thick + 1.0e-6) {
                        cells.add(new BlockPos(gx, y, gz));
                    }
                }
            }
        }
        return cells;
    }

    /** Cette cellule de la porte laisse-t-elle passer un joueur, a cette course des battants ? */
    public boolean passable(BlockPos cell, float progress) {
        return this.kind.passable(local(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5).x, progress);
    }

    /** Cette cellule de la porte est-elle dans le passage libre, porte grande ouverte ? */
    public boolean clearWhenOpen(BlockPos cell) {
        return passable(cell, 1.0F);
    }

    /** Ce point (les pieds d'un joueur) est-il dans la zone qui ouvre la porte ? */
    public boolean triggers(double wx, double wy, double wz) {
        Vec3 p = local(wx, wy, wz);
        return Math.abs(p.x) <= this.kind.width / 2.0 + 1.0 && Math.abs(p.z) <= this.kind.trigger
                && p.y > -1.5 && p.y < this.kind.height + 1.0;
    }

    /** La porte deplacee d'un bloc entier (les cellules et le controleur suivent). */
    public HavenDoorFrame moved(int dx, int dy, int dz) {
        return new HavenDoorFrame(this.kind, this.x + dx, this.y + dy, this.z + dz, this.yaw);
    }
}
