package com.emerald.jak.gun;

import com.emerald.main.EmeraldWeaponsMod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Un modele JKGN cuit par tools/jak_gun.py : le Morph Gun (os, 13 poses, 13
 * transformations) ou une munition d'eco a ramasser (os seuls).
 *
 * LU DEPUIS LE CLASSPATH DU MOD, pas par le ResourceManager du client : le
 * serveur n'a pas les assets dans son gestionnaire de ressources, et le banc
 * d'essai verifie les poses cote serveur, par ce meme lecteur. Un pack de
 * ressources ne peut donc pas le remplacer ; c'est voulu.
 *
 * LE FORMAT est documente en tete de l'outil. On le relit sans rien recalculer,
 * et tout ce qui ne colle pas -- magie, version, taille, parent range apres son
 * os -- est refuse d'un bloc : une arme a moitie lue se dessinerait en eclats.
 *
 * Les quaternions sont rendus TELS QUELS (norme 0,99994 a 1,00002, mesuree par
 * l'outil) : c'est GunPose qui les renormalise, partout (lecon 7 de l'outil).
 */
public final class JakGunModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /** Le dossier des modeles dans le jar : assets/emeraldweapons/jak_gun/. */
    public static final String FOLDER = "/assets/" + EmeraldWeaponsMod.MODID + "/jak_gun/";
    public static final String GUN = "morph_gun";

    public static final int FLAG_BLEND = 1;
    public static final int FLAG_ENVMAP = 2;

    private static final int MAGIC = 'J' | ('K' << 8) | ('G' << 16) | ('N' << 24);
    private static final int VERSION = 1;
    private static final int HEADER_BYTES = 64;
    private static final int BONE_BYTES = 124;
    private static final int TRIANGLE_BYTES = 124;
    private static final int NAME_BYTES = 32;
    private static final int TRS = 10;

    private static final Map<String, JakGunModel> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> FAILED = new ConcurrentHashMap<>();

    public final String name;
    public final int triangles;
    public final int bones;
    public final int atlasWidth;
    public final int atlasHeight;
    /** Indice de l'os `main`, ou -1. */
    public final int main;
    /** Un triangle est cache quand l'echelle de son os est sous ratio x celle de main. */
    public final float ratio;
    public final float minX, minY, minZ, maxX, maxY, maxZ;

    public final String[] boneNames;
    public final int[] boneParents;
    /** TRS de repos : t (3), q x y z w (4), s (3), par os. */
    public final float[] restTrs;
    /** Inverse de la matrice monde de repos, 3 x 4 rangee par colonnes, par os. */
    public final float[] inverseBind;

    /** Os du triangle (majoritaire) : decide du masquage. */
    public final byte[] triangleBone;
    public final byte[] flags;
    /** Os de chaque sommet (trois par triangle) : c'est lui qui le deplace. */
    public final byte[] vertexBone;
    public final float[] positions;
    public final float[] uvs;
    public final float[] normals;
    /** Couleur ARGB de chaque sommet, deja doublee. */
    public final int[] colors;
    public final boolean hasBlend;

    public final String[] poseNames;
    /** poses x os x 10. */
    public final float[] poses;
    public final String[] animNames;
    public final float[] animDurations;
    public final float[][] animTimes;
    /** Par animation : clefs x os x 10. */
    public final float[][] animKeys;

    private JakGunModel(String name, ByteBuffer buf) throws IOException {
        this.name = name;
        int length = buf.capacity();
        if (length < HEADER_BYTES) {
            throw new IOException("fichier trop court (" + length + " octets)");
        }
        int magic = buf.getInt();
        int version = buf.getInt();
        if (magic != MAGIC || version != VERSION) {
            throw new IOException("magie " + Integer.toHexString(magic) + " version " + version);
        }
        this.triangles = buf.getInt();
        this.bones = buf.getInt();
        int poseCount = buf.getInt();
        int animCount = buf.getInt();
        this.atlasWidth = buf.getInt();
        this.atlasHeight = buf.getInt();
        this.main = buf.getInt();
        this.ratio = buf.getFloat();
        this.minX = buf.getFloat();
        this.minY = buf.getFloat();
        this.minZ = buf.getFloat();
        this.maxX = buf.getFloat();
        this.maxY = buf.getFloat();
        this.maxZ = buf.getFloat();
        if (this.triangles < 0 || this.bones <= 0 || this.bones > 255 || poseCount < 0 || animCount < 0
                || this.main >= this.bones) {
            throw new IOException("en-tete incoherent : " + this.triangles + " triangles, " + this.bones + " os, "
                    + poseCount + " poses, " + animCount + " transformations, main " + this.main);
        }
        long fixed = HEADER_BYTES + (long) this.bones * BONE_BYTES + (long) this.triangles * TRIANGLE_BYTES
                + (long) poseCount * (NAME_BYTES + 4L * TRS * this.bones);
        if (fixed > length) {
            throw new IOException(length + " octets, au moins " + fixed + " attendus");
        }

        this.boneNames = new String[this.bones];
        this.boneParents = new int[this.bones];
        this.restTrs = new float[this.bones * TRS];
        this.inverseBind = new float[this.bones * 12];
        for (int b = 0; b < this.bones; b++) {
            this.boneNames[b] = name(buf);
            this.boneParents[b] = buf.getShort();
            buf.getShort();
            if (this.boneParents[b] >= b) {
                throw new IOException("l'os " + b + " a pour parent " + this.boneParents[b] + ", range apres lui");
            }
            for (int k = 0; k < TRS; k++) {
                this.restTrs[b * TRS + k] = buf.getFloat();
            }
            for (int k = 0; k < 12; k++) {
                this.inverseBind[b * 12 + k] = buf.getFloat();
            }
        }

        this.triangleBone = new byte[this.triangles];
        this.flags = new byte[this.triangles];
        this.vertexBone = new byte[this.triangles * 3];
        this.positions = new float[this.triangles * 9];
        this.uvs = new float[this.triangles * 6];
        this.normals = new float[this.triangles * 9];
        this.colors = new int[this.triangles * 3];
        boolean blend = false;
        for (int t = 0; t < this.triangles; t++) {
            this.triangleBone[t] = buf.get();
            this.flags[t] = buf.get();
            buf.getShort();
            if ((this.triangleBone[t] & 0xFF) >= this.bones) {
                throw new IOException("triangle " + t + " : os " + (this.triangleBone[t] & 0xFF) + " absent");
            }
            blend |= (this.flags[t] & FLAG_BLEND) != 0;
            for (int v = 0; v < 3; v++) {
                int s = t * 3 + v;
                this.vertexBone[s] = buf.get();
                if ((this.vertexBone[s] & 0xFF) >= this.bones) {
                    throw new IOException("sommet " + s + " : os " + (this.vertexBone[s] & 0xFF) + " absent");
                }
                buf.get();
                buf.get();
                buf.get();
                this.positions[s * 3] = buf.getFloat();
                this.positions[s * 3 + 1] = buf.getFloat();
                this.positions[s * 3 + 2] = buf.getFloat();
                this.uvs[s * 2] = buf.getFloat();
                this.uvs[s * 2 + 1] = buf.getFloat();
                this.normals[s * 3] = buf.getFloat();
                this.normals[s * 3 + 1] = buf.getFloat();
                this.normals[s * 3 + 2] = buf.getFloat();
                int r = buf.get() & 0xFF;
                int g = buf.get() & 0xFF;
                int bl = buf.get() & 0xFF;
                int a = buf.get() & 0xFF;
                this.colors[s] = (a << 24) | (r << 16) | (g << 8) | bl;
            }
        }
        this.hasBlend = blend;

        this.poseNames = new String[poseCount];
        this.poses = new float[poseCount * this.bones * TRS];
        for (int p = 0; p < poseCount; p++) {
            this.poseNames[p] = name(buf);
            for (int k = 0; k < this.bones * TRS; k++) {
                this.poses[p * this.bones * TRS + k] = buf.getFloat();
            }
        }

        this.animNames = new String[animCount];
        this.animDurations = new float[animCount];
        this.animTimes = new float[animCount][];
        this.animKeys = new float[animCount][];
        for (int a = 0; a < animCount; a++) {
            if (buf.remaining() < NAME_BYTES + 8) {
                throw new IOException("transformation " + a + " tronquee");
            }
            this.animNames[a] = name(buf);
            int keys = buf.getInt();
            this.animDurations[a] = buf.getFloat();
            if (keys <= 0 || buf.remaining() < 4L * keys + 4L * keys * this.bones * TRS) {
                throw new IOException("transformation " + this.animNames[a] + " : " + keys + " clefs, fichier trop court");
            }
            this.animTimes[a] = new float[keys];
            for (int k = 0; k < keys; k++) {
                this.animTimes[a][k] = buf.getFloat();
                if (k > 0 && this.animTimes[a][k] < this.animTimes[a][k - 1]) {
                    throw new IOException("transformation " + this.animNames[a] + " : instants decroissants");
                }
            }
            this.animKeys[a] = new float[keys * this.bones * TRS];
            for (int k = 0; k < this.animKeys[a].length; k++) {
                this.animKeys[a][k] = buf.getFloat();
            }
        }
        if (buf.position() != length) {
            throw new IOException(buf.position() + " octets lus sur " + length);
        }
    }

    private static String name(ByteBuffer buf) {
        byte[] raw = new byte[NAME_BYTES];
        buf.get(raw);
        int n = 0;
        while (n < raw.length && raw[n] != 0) {
            n++;
        }
        return new String(raw, 0, n, StandardCharsets.US_ASCII);
    }

    public static JakGunModel parse(String name, byte[] bytes) throws IOException {
        return new JakGunModel(name, ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN));
    }

    /** Lit un .bin du dossier jak_gun, sans cache. */
    public static JakGunModel load(String name) throws IOException {
        String path = FOLDER + name + ".bin";
        try (InputStream in = JakGunModel.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException(path + " introuvable dans le jar du mod");
            }
            return parse(name, in.readAllBytes());
        }
    }

    /**
     * Le modele, lu une fois ; null (et une seule erreur au journal) s'il ne se lit pas.
     *
     * @param name morph_gun, gun_ammo_red, gun_ammo_yellow, gun_ammo_blue ou gun_ammo_dark
     */
    @Nullable
    public static JakGunModel get(String name) {
        JakGunModel cached = CACHE.get(name);
        if (cached != null || FAILED.containsKey(name)) {
            return cached;
        }
        try {
            JakGunModel model = load(name);
            CACHE.put(name, model);
            return model;
        } catch (IOException | RuntimeException e) {
            FAILED.put(name, Boolean.TRUE);
            LOGGER.error("Morph Gun : modele {} illisible : {}", name, e.toString());
            return null;
        }
    }

    @Nullable
    public static JakGunModel gun() {
        return get(GUN);
    }

    /** Le modele de la munition d'eco d'une famille (gun-ammo-*-lod0). */
    @Nullable
    public static JakGunModel ammo(GunForm.Family family) {
        return get("gun_ammo_" + family.jak);
    }

    public int poseIndex(String pose) {
        for (int i = 0; i < this.poseNames.length; i++) {
            if (this.poseNames[i].equals(pose)) {
                return i;
            }
        }
        return -1;
    }

    public int animIndex(String anim) {
        for (int i = 0; i < this.animNames.length; i++) {
            if (this.animNames[i].equals(anim)) {
                return i;
            }
        }
        return -1;
    }

    public int boneIndex(String bone) {
        for (int i = 0; i < this.boneNames.length; i++) {
            if (this.boneNames[i].equals(bone)) {
                return i;
            }
        }
        return -1;
    }
}
