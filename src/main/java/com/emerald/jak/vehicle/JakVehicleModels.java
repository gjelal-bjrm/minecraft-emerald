package com.emerald.jak.vehicle;

import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Charge les voitures et les motos cuites (assets/emeraldweapons/jak_vehicles/<modele>.bin).
 *
 * Cote client seulement. Les fichiers sont relus a chaque rechargement des
 * ressources (F3+T) : on peut donc relancer tools/jak_vehicle.py, recopier les
 * ressources et voir le resultat sans redemarrer le jeu.
 */
public final class JakVehicleModels implements ResourceManagerReloadListener {

    public static final JakVehicleModels INSTANCE = new JakVehicleModels();

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Remplacee d'un bloc a chaque rechargement : le rendu ne voit jamais une table a moitie remplie. */
    private static volatile Map<String, JakVehicleModel> models = Map.of();
    /**
     * CE QUI SE DESSINE (cahier §110) : les cubes de pres, et de loin. Le modele de Jak (models) reste
     * celui du jeu -- la camera du conducteur y mesure son capot --, les cubes n'en sont que l'image.
     */
    private static volatile Map<String, JakVehicleModel> near = Map.of();
    private static volatile Map<String, JakVehicleModel> far = Map.of();

    private JakVehicleModels() {
    }

    /** Le modele de Jak 3 : sa geometrie sert au jeu (le capot devant les yeux du conducteur). */
    @Nullable
    public static JakVehicleModel get(String name) {
        return models.get(name);
    }

    /** Ce qui se dessine : en cubes s'il y est passe, de pres ou de loin ; sinon le modele de Jak. */
    @Nullable
    public static JakVehicleModel drawn(String name, boolean distant) {
        JakVehicleModel model = (distant ? far : near).get(name);
        return model != null ? model : models.get(name);
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        Map<String, JakVehicleModel> loaded = new HashMap<>();
        Map<String, JakVehicleModel> close = new HashMap<>();
        Map<String, JakVehicleModel> distant = new HashMap<>();
        for (String name : JakVehicleEntity.MODELS) {
            JakVehicleModel model = read(manager, name, "jak_vehicles/" + name + ".bin");
            if (model == null) {
                continue;
            }
            loaded.put(name, model);
            // en cubes, si le vehicule y est passe et que tools/jak_cubes.py l'a cuit (cahier §110)
            String cubed = com.emerald.jak.JakCubes.suffix(name);
            if (cubed != null) {
                JakVehicleModel cubes = read(manager, name, "jak_vehicles/cubes/" + name + cubed + ".bin");
                if (cubes != null) {
                    close.put(name, cubes);
                }
            }
            String farther = com.emerald.jak.JakCubes.farSuffix(name);
            if (farther != null && !farther.equals(cubed)) {
                JakVehicleModel cubes = read(manager, name, "jak_vehicles/cubes/" + name + farther + ".bin");
                if (cubes != null) {
                    distant.put(name, cubes);
                }
            } else if (close.containsKey(name)) {
                distant.put(name, close.get(name));
            }
        }
        models = Map.copyOf(loaded);
        near = Map.copyOf(close);
        far = Map.copyOf(distant);
    }

    @Nullable
    private static JakVehicleModel read(ResourceManager manager, String name, String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, path);
        Optional<Resource> resource = manager.getResource(id);
        if (resource.isEmpty()) {
            if (!path.contains("/cubes/")) {
                LOGGER.error("Voiture {} introuvable : {}", name, id);
            }
            return null;
        }
        try (InputStream in = resource.get().open()) {
            JakVehicleModel model = JakVehicleModel.parse(in.readAllBytes());
            LOGGER.info("Voiture {} chargee ({}) : {} triangles, {} os, atlas {} x {}",
                    name, path, model.triangles, model.boneNames.length, model.atlasWidth, model.atlasHeight);
            return model;
        } catch (IOException e) {
            // une voiture illisible ne se dessine pas, les autres si
            LOGGER.error("Voiture {} illisible ({}) : {}", name, id, e.getMessage());
            return null;
        }
    }
}
