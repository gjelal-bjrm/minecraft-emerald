package com.emerald.client;

import com.emerald.jak.vehicle.JakVehicleModel;
import com.emerald.main.EmeraldWeaponsMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Les portes de Jak 3 cuites par tools/jak_door.py (assets/emeraldweapons/jak_doors/) : les
 * triangles au format des voitures (JakVehicleModel), et l'animation d'ouverture echantillonnee
 * en pas -- pour chaque os qui bouge, la matrice qui l'amene de la porte fermee au pas voulu.
 * Relues a chaque rechargement des ressources (F3+T).
 */
public final class HavenDoorModels implements ResourceManagerReloadListener {

    public static final HavenDoorModels INSTANCE = new HavenDoorModels();
    public static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath(
            EmeraldWeaponsMod.MODID, "textures/entity/jak_doors/atlas.png");
    private static final String[] NAMES = {"hip_door_a", "com_airlock_outer"};
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Un modele et son animation : les pas de chaque os anime, seize flottants par pas (colonnes). */
    public record DoorModel(JakVehicleModel mesh, int steps, float[][][] boneFrames) {
    }

    private static volatile Map<String, DoorModel> models = Map.of();

    private HavenDoorModels() {
    }

    @Nullable
    public static DoorModel get(String name) {
        return models.get(name);
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        Map<String, DoorModel> loaded = new HashMap<>();
        for (String name : NAMES) {
            try {
                JakVehicleModel mesh = JakVehicleModel.parse(read(manager, name + ".bin"));
                JsonObject anim;
                try (Reader reader = new InputStreamReader(
                        new java.io.ByteArrayInputStream(read(manager, name + ".anim.json")), StandardCharsets.UTF_8)) {
                    anim = JsonParser.parseReader(reader).getAsJsonObject();
                }
                int steps = anim.get("steps").getAsInt();
                JsonObject bones = anim.getAsJsonObject("bones");
                // les pas de chaque os du modele, dans l'ordre de ses os ; null s'il ne bouge pas
                float[][][] frames = new float[mesh.boneNames.length][][];
                for (int b = 0; b < mesh.boneNames.length; b++) {
                    JsonArray list = bones.has(mesh.boneNames[b]) ? bones.getAsJsonArray(mesh.boneNames[b]) : null;
                    if (list == null) {
                        continue;
                    }
                    frames[b] = new float[list.size()][16];
                    for (int k = 0; k < list.size(); k++) {
                        JsonArray m = list.get(k).getAsJsonArray();
                        for (int i = 0; i < 16; i++) {
                            frames[b][k][i] = m.get(i).getAsFloat();
                        }
                    }
                }
                loaded.put(name, new DoorModel(mesh, steps, frames));
                LOGGER.info("Porte {} chargee : {} triangles, {} os, {} pas", name, mesh.triangles,
                        mesh.boneNames.length, steps);
            } catch (Exception e) {
                LOGGER.error("Porte {} illisible : {}", name, e.getMessage());
            }
        }
        models = Map.copyOf(loaded);
    }

    private static byte[] read(ResourceManager manager, String file) throws java.io.IOException {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "jak_doors/" + file);
        Optional<Resource> resource = manager.getResource(id);
        if (resource.isEmpty()) {
            throw new java.io.IOException(id + " introuvable");
        }
        try (InputStream in = resource.get().open()) {
            return in.readAllBytes();
        }
    }
}
