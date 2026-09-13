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

    private JakVehicleModels() {
    }

    @Nullable
    public static JakVehicleModel get(String name) {
        return models.get(name);
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        Map<String, JakVehicleModel> loaded = new HashMap<>();
        for (String name : JakVehicleEntity.MODELS) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID,
                    "jak_vehicles/" + name + ".bin");
            Optional<Resource> resource = manager.getResource(id);
            if (resource.isEmpty()) {
                LOGGER.error("Voiture {} introuvable : {}", name, id);
                continue;
            }
            try (InputStream in = resource.get().open()) {
                JakVehicleModel model = JakVehicleModel.parse(in.readAllBytes());
                loaded.put(name, model);
                LOGGER.info("Voiture {} chargee : {} triangles, {} os, atlas {} x {}",
                        name, model.triangles, model.boneNames.length, model.atlasWidth, model.atlasHeight);
            } catch (IOException e) {
                // une voiture illisible ne se dessine pas, les autres si
                LOGGER.error("Voiture {} illisible ({}) : {}", name, id, e.getMessage());
            }
        }
        models = Map.copyOf(loaded);
    }
}
