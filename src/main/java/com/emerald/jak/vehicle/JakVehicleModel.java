package com.emerald.jak.vehicle;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Une voiture de Haven cuite par tools/jak_vehicle.py : des triangles prets a
 * dessiner, dans le repere du modele.
 *
 * Le format est documente en tete de l'outil. On le relit ici sans rien
 * recalculer : positions en metres (donc en blocs), avant en +z, UV deja dans
 * l'atlas, couleur de sommet deja doublee (convention PS2). Tout ce qui ne
 * colle pas a ce format -- magie, version, taille -- est refuse d'un bloc :
 * une voiture a moitie lue se dessinerait en eclats sans la moindre erreur.
 */
public final class JakVehicleModel {

    public static final int FLAG_BLEND = 1;
    /** Ce qui est devant un siege : a plus de tant de blocs devant lui... */
    private static final float AHEAD_FROM = 0.3F;
    /** ... et a moins de tant de cote. */
    private static final float AHEAD_HALF_WIDTH = 1.3F;

    private static final int MAGIC = 'J' | ('K' << 8) | ('V' << 16) | ('H' << 24);
    private static final int VERSION = 1;
    private static final int HEADER_BYTES = 48;
    private static final int BONE_BYTES = 40;
    private static final int TRIANGLE_BYTES = 112;

    public final int triangles;
    public final int atlasWidth;
    public final int atlasHeight;
    public final float minX, minY, minZ, maxX, maxY, maxZ;

    public final String[] boneNames;
    public final int[] boneParents;
    /** Pivots des os au repos, trois flottants par os. */
    public final float[] bonePivots;

    /** Os dominant de chaque triangle. */
    public final byte[] bones;
    public final byte[] flags;
    /** Trois sommets par triangle, trois flottants par sommet. */
    public final float[] positions;
    /** Trois sommets par triangle, deux flottants par sommet. */
    public final float[] uvs;
    public final float[] normals;
    /** Trois sommets par triangle, une couleur ARGB par sommet. */
    public final int[] colors;
    public final boolean hasBlend;

    private JakVehicleModel(ByteBuffer buf) throws IOException {
        if (buf.remaining() < HEADER_BYTES) {
            throw new IOException("fichier trop court (" + buf.remaining() + " octets)");
        }
        int magic = buf.getInt();
        int version = buf.getInt();
        if (magic != MAGIC || version != VERSION) {
            throw new IOException("magie " + Integer.toHexString(magic) + " version " + version);
        }
        this.triangles = buf.getInt();
        int boneCount = buf.getInt();
        this.atlasWidth = buf.getInt();
        this.atlasHeight = buf.getInt();
        this.minX = buf.getFloat();
        this.minY = buf.getFloat();
        this.minZ = buf.getFloat();
        this.maxX = buf.getFloat();
        this.maxY = buf.getFloat();
        this.maxZ = buf.getFloat();

        long expected = HEADER_BYTES + (long) boneCount * BONE_BYTES + (long) this.triangles * TRIANGLE_BYTES;
        if (this.triangles < 0 || boneCount < 0 || buf.capacity() != expected) {
            throw new IOException(buf.capacity() + " octets, " + expected + " attendus");
        }

        this.boneNames = new String[boneCount];
        this.boneParents = new int[boneCount];
        this.bonePivots = new float[boneCount * 3];
        byte[] name = new byte[24];
        for (int b = 0; b < boneCount; b++) {
            buf.get(name);
            int length = 0;
            while (length < name.length && name[length] != 0) {
                length++;
            }
            this.boneNames[b] = new String(name, 0, length, StandardCharsets.US_ASCII);
            this.boneParents[b] = buf.getShort();
            buf.getShort();
            this.bonePivots[b * 3] = buf.getFloat();
            this.bonePivots[b * 3 + 1] = buf.getFloat();
            this.bonePivots[b * 3 + 2] = buf.getFloat();
        }

        this.bones = new byte[this.triangles];
        this.flags = new byte[this.triangles];
        this.positions = new float[this.triangles * 9];
        this.uvs = new float[this.triangles * 6];
        this.normals = new float[this.triangles * 9];
        this.colors = new int[this.triangles * 3];
        boolean blend = false;
        for (int t = 0; t < this.triangles; t++) {
            this.bones[t] = buf.get();
            this.flags[t] = buf.get();
            buf.getShort();
            blend |= (this.flags[t] & FLAG_BLEND) != 0;
            for (int v = 0; v < 3; v++) {
                int p = (t * 3 + v) * 3;
                this.positions[p] = buf.getFloat();
                this.positions[p + 1] = buf.getFloat();
                this.positions[p + 2] = buf.getFloat();
                int u = (t * 3 + v) * 2;
                this.uvs[u] = buf.getFloat();
                this.uvs[u + 1] = buf.getFloat();
                this.normals[p] = buf.getFloat();
                this.normals[p + 1] = buf.getFloat();
                this.normals[p + 2] = buf.getFloat();
                int r = buf.get() & 0xFF;
                int g = buf.get() & 0xFF;
                int bl = buf.get() & 0xFF;
                int a = buf.get() & 0xFF;
                this.colors[t * 3 + v] = (a << 24) | (r << 16) | (g << 8) | bl;
            }
        }
        this.hasBlend = blend;
    }

    /**
     * L'oeil qu'il faut au-dessus d'un siege, repere du modele, pour que toute la carrosserie
     * devant lui reste sous le regard d'au moins l'angle dont c'est la tangente : un point a d
     * blocs devant et a y de haut veut un oeil a y + d x tangente. Moins l'infini s'il n'y a rien.
     */
    public float eyeClearing(double seatX, double seatZ, double tangent) {
        float eye = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < this.positions.length; i += 3) {
            double ahead = this.positions[i + 2] - seatZ;
            if (ahead > AHEAD_FROM && Math.abs(this.positions[i] - seatX) < AHEAD_HALF_WIDTH) {
                eye = Math.max(eye, (float) (this.positions[i + 1] + ahead * tangent));
            }
        }
        return eye;
    }

    public static JakVehicleModel parse(byte[] bytes) throws IOException {
        return new JakVehicleModel(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN));
    }
}
