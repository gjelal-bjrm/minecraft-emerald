# EmeraldWeapons

Mod NeoForge pour Minecraft 1.21.1 ajoutant un matériau custom (Arcencium), une épée légendaire aux effets spectaculaires, des entités hostiles/neutres, un système de quêtes et des structures procédurales — développé par Instinctif.

---

## Objectif

Ce mod enrichit l'expérience de jeu de fin de partie avec un matériau rare à miner (Arcencium), une épée de prestige (`emerald_sword`) dotée d'effets visuels et de combat avancés, ainsi qu'une couche narrative inspirée de l'univers Jak 3 (PNJ Wastelanders, robots KG, villes procédurales). L'intention est de proposer une progression post-Nétherite avec des mécaniques de combat uniques et un univers cohérent.

---

## Stack technique

| Élément | Valeur |
|---|---|
| Langage | Java 21 |
| Plateforme | Minecraft 1.21.1 |
| Mod loader | NeoForge 21.1.193 |
| Build tool | Gradle 8.14.3 (wrapper `gradlew.bat`) |
| Mappings | Parchment 2024.11.17 (minecraft 1.21.1) |
| Mod ID | `emeraldweapons` |
| Version | 1.1.0 |
| Groupe | `com.emerald.weapons` |
| Outil annexe | Python 3.x + Pillow (script `generate_fulgurite.py`) |

---

## Fonctionnalités principales

### Matériau Arcencium
- Minerai `arcencium_ore` généré naturellement entre Y -32 et Y +48, 4 veines/chunk, dans tous les biomes Overworld (taille veine 6, remplace stone et deepslate)
- Bloc de rangement `arcencium_block` (9 lingots ↔ 1 bloc)
- Fonderie et calcination (`arcencium_ingot`) depuis l'ore ou le raw, avec support Fortune et Silk Touch dans les loot tables

### Épée Légendaire — EmeraldWindblade (`emerald_sword`)
- **Tier custom** (`EmeraldTier`) : 2000 durabilité, +5.0 dégâts, vitesse -2.2, enchantabilité 22
- **Recette** : `E N E / A A A / _ N _` (Émeraude × 2, Nétherite_Sword × 2, Arcencium_Ingot × 3)
- **Effets de frappe aléatoires** (5 % par coup, cooldown 5 s) parmi 5 cristaux : Feu / Knockback / Cécité / Poison / Ralentissement — chacun avec particules colorées et son
- **Crystalline Aura** (8 %) : buff joueur turquoise (movement speed +10 %, attack speed +10 %, lifesteal 5 %)
- **Crystalline Thunder** (3 %) : foudre arc-en-ciel sur jusqu'à 6 ennemis dans un cône de 6 blocs, 15.0 dégâts magiques + 6 effets de debuff + onde de choc + zone électrifiée 3 s

### Arc Légendaire — Arcencium Bow (`arcencium_bow`)
- Classe `ArcenciumBowItem` (extends `BowItem`), 1500 durabilité, enchantabilité 22, tags `enchantable/bow` + `enchantable/durability` (datagen)
- **Recette** : `EA_ / AB_ / EA_` (Arcencium_Ingot × 3, Émeraude × 2, Bow × 1)
- **Tension Prismatique** : la charge allume les cristaux un à un selon le temps de bandage (8 / 18 / 28 / 38 / 50 ticks) :
  1. rouge → flèche enflammée · 2. orange → knockback · 3. bleu → gel temporaire (`setTicksFrozen` + Slowness VI 3 s) · 4. rose → poison II · 5. vert → **Flèche Prismatique**
- **Flèche Prismatique** (pleine tension) : critique, puis à l'impact 3 éclats frappent jusqu'à 3 ennemis dans 5 blocs (4 dégâts magiques chacun) avec effet aléatoire parmi 5 — l'éclat 1 porte toujours un effet, les autres une fois sur deux
- **Portée indépendante de la charge** : vitesse 2.7 → 3.0 quel que soit le cran, seuls dégâts (base 2.0 → 6.0) et effets montent
- **Marque Prismatique** (`prismatic_mark`, 8 s, + Glowing) posée par la Flèche Prismatique : les procs de l'épée sur une cible marquée sont doublés (`EmeraldWindblade.procMultiplier`)
- Impacts gérés par `ArcenciumBowEvents` via `ProjectileImpactEvent` (stade stocké dans les persistent data de la flèche, pas d'entité custom)
- Client : `ArcenciumBowClient` enregistre les prédicats `pull` (normalisé sur 50 ticks) / `pulling` ; le modèle a **5 overrides** de tension (un par cristal) au lieu des 3 vanilla
- Textures générées par `tools/bow_designer.py --install` : repos + 5 états de tension, chacun animé 12 frames (corde prismatique, pulsation, particules)

### Palette du village d'Arcencium (47 blocs)
Règle directrice : ~85 % de matière sourde, ~15 % d'accents colorés. Tout est enregistré dans `ModBlocks.VILLAGE_BLOCKS` (onglet créatif « Arcencium Blocks »), blockstates/modèles/loot tables/recettes/tags générés par datagen.
- **Gangue** (la roche stérile de la mine, matériau de masse, gris-beige) : `gangue_stone`, `gangue_bricks`, `polished_gangue`, `veined_stone` — chacun avec escaliers / dalle / mur (sauf polie : pas de mur). Recette : 8 cobblestone + 1 arcencium brut → 8 ; tailleur de pierre depuis la gangue brute
- **Arcencium** (le noble, réservé aux édifices importants) : `arcencium_bricks` (+ escaliers/dalle/mur, **texture animée** : veines organiques aux couleurs qui vibrent, 8 frames), `chiseled_arcencium` (animé, lumière 6), `corrupted_bricks` (+ famille, palette violette pour le village hostile). Recettes : 8 gangue taillée + 1 lingot → 8 ; corrompue = 8 briques + œil d'araignée fermenté
- **Verre & lumière** : `prismatic_glass` (+ vitre `prismatic_glass_pane`), `arcencium_lantern` (lumière 14, posée/suspendue)
- **Végétal** : `prismatic_grass_block`, `prism_bloom` et `prism_tuft` (classe `GlowingPlantBlock` : lumière faible constante + particules colorées **uniquement la nuit**), **Arbre de Prisme** : `prism_log` (`PrismLogBlock`, animé, lumière 5, étincelles nocturnes), `prism_leaves` (`PrismLeavesBlock`, motes multicolores qui tombent), `prism_sapling` (TreeGrower → configured feature `prism_tree` : tronc courbe + feuillage azalée), `crystal_planks` (+ escaliers/dalle/barrière)
- **Textile** (teintures poussiéreuses, laine vanilla trop saturée) : `verdigris_wool`, `ochre_wool`, `old_rose_wool`, `slate_blue_wool`, `ecru_wool` + les 5 tapis. Recette : 4 laines blanches + teinture + lingot → 4
- **Particule** `prism_mote` (`ModClient.PrismMoteParticle`) : point lumineux plein feu, teinte aléatoire parmi les 5 cristaux, tombe ou s'élève selon la vitesse initiale
- **Outils** : `tools/block_designer.py` (textures tuilables, veines par marcheurs aléatoires, `--install`, `--check` pour le tiling), `tools/iso_render.py` (rendu isométrique d'une scène de blocs — maison test dans `tools/preview/iso/`)

### Village d'Arcencium (génération Jigsaw)
- Structure `emeraldweapons:arcencium_village_taiga` (`minecraft:jigsaw`, start pool `village/taiga/town_centers`, size 6, `beard_thin`, biomes taïga via tag `has_structure/arcencium_village`), structure set `arcencium_villages` (random_spread, spacing 28 / separation 10)
- **145 structures NBT** dans `data/emeraldweapons/structure/` : le village taïga de CTOV (mod du modpack ATM10, **usage privé uniquement**) rehabillé bloc à bloc avec notre palette par `tools/reskin.py` (~120 correspondances ; mobilier fonctionnel laissé vanilla ; propriétés de blockstate filtrées selon le bloc cible ; pools et `final_state` des blocs jigsaw redirigés vers nos pools)
- **23 template pools** (`village/taiga/*`, `village/common/*`) copiés de CTOV et réécrits (namespace, structures, processeurs → `minecraft:empty`)
- Pipeline : `tools/nbt_structure.py` (lecture/écriture NBT, jars vanilla + mods), `tools/build_view.py` (rendu iso 4 angles + coupe + plans, formes réelles), `tools/import_village.py`, `tools/make_village.py` (datapack complet + validation des références), `tools/preview/builds/commandes.txt` (`/place template …` pour visiter)
- En jeu : `/locate structure emeraldweapons:arcencium_village_taiga`
- Textures des bûches de Prisme : issues des **images de référence de l'auteur** (`tools/refs/`, 64×64, animées par rotation de teinte des éclairs — `tools/log_from_refs.py`), non régénérées par `block_designer.py`

### Effet de statut custom
- `crystalline_aura` (catégorie BENEFICIAL, couleur 0x80FFDA) avec modificateurs d'attributs transitoires
- `prismatic_mark` (HARMFUL, 0xE478FF) — drapeau passif de synergie arc → épée

### Système de particules (6 types)
- `crystalline_fissure` (3 frames d'animation), `crystal_red/orange/yellow/green/pink` — oscillation circulaire ou pulsation sinusoïdale, rendu client dédié

### Rendu client
- Animation de la main en première personne (`RenderHandEvent`)
- 5 cristaux en orbite autour du joueur (`RenderLevelStageEvent`) — rayon 0.3, rotation 1.5°/tick
- `CrystalSwordRenderer` pour l'item tenu

### Entités custom
- **WastelanderEntity** (PNJ neutre, 30 HP) : 4 rôles (Habitant / Marchand / Sentinelle / Guide), dialogues aléatoires par rôle, fuit le joueur, riposte si frappé
- **KGDeathbotEntity** (robot hostile, 40–80 HP) : 3 variantes (Standard / Elite / Commander), scan radar (Glowing), aura Commander qui buffe les KG alliés, explosion à la mort

### Système de quêtes
- 4 quêtes chaînées (tuer Metalheads, collecter de l'iron, atteindre un lieu, tuer des KG Deathbots)
- Conditions : `KILL_MOB`, `COLLECT_ITEM`, `REACH_LOCATION`
- Récompenses en XP + items vanilla
- Persistance par joueur via NBT (`Jak3Quests`), chargement/sauvegarde sur login/logout

### Structures procédurales (partielles)
- `SpargusCityStructure` et `HavenCityStructure` enregistrées et configurées (max_distance 64, step `surface_structures`), génération procédurale à compléter

### Creative Tabs
- Deux onglets : *Arcencium Items* et *Arcencium Blocks*

---

## Architecture & structure

```
EmeraldWeapons/
├── src/
│   ├── main/
│   │   ├── java/com/emerald/
│   │   │   ├── main/            # Point d'entrée (EmeraldWeaponsMod.java)
│   │   │   ├── item/            # ModItems.java, ModCreativeModeTabs.java
│   │   │   ├── block/           # ModBlocks.java
│   │   │   ├── tiers/           # EmeraldTier.java (Tier NeoForge)
│   │   │   ├── weapons/         # EmeraldWindblade.java (logique complète de l'épée)
│   │   │   ├── effect/          # CrystallineAuraEffect.java, ModEffects.java
│   │   │   ├── particle/        # ModParticles.java, FissureParticle, CrystalParticle
│   │   │   ├── entity/          # WastelanderEntity.java, KGDeathbotEntity.java, ModEntities.java
│   │   │   ├── quests/          # QuestManager.java, PlayerQuestData.java
│   │   │   ├── structures/      # SpargusCityStructure.java, HavenCityStructure.java, Jak3Registry.java
│   │   │   ├── events/          # Jak3EventHandlers.java (quêtes, worldgen hooks)
│   │   │   ├── datagen/         # ModDataGenerators.java, ModWorldGenProvider.java,
│   │   │   │                    # ModItemModelProvider.java, BlockTagProvider, ItemTagProvider
│   │   │   └── client/          # ModClient.java, ClientEvents.java,
│   │   │                        # Jak3ClientEvents.java, CrystalSwordRenderer.java
│   │   └── resources/
│   │       ├── META-INF/        # neoforge.mods.toml
│   │       ├── assets/emeraldweapons/
│   │       │   ├── textures/item|block|particle|mob_effect/
│   │       │   ├── models/item|block/
│   │       │   ├── blockstates/
│   │       │   ├── particles/
│   │       │   └── lang/en_us.json
│   │       └── data/emeraldweapons/
│   │           └── recipe/      # 7 recettes JSON manuelles
│   └── generated/resources/     # Sortie datagen (tags, worldgen, models, biome modifiers)
├── build/libs/                  # JAR produit par gradlew build
├── generate_fulgurite.py        # Script Python utilitaire (animation texture HSV)
├── build_mod.bat                # Script de build Windows
├── gradlew.bat                  # Wrapper Gradle
└── gradle.properties            # Versions et propriétés du mod
```

**Patterns notables :**
- Registrations via `DeferredRegister` / `DeferredItem` / `DeferredBlock` (pattern NeoForge standard)
- Datagen par `GatherDataEvent` dans `ModDataGenerators` — génère tags, modèles, worldgen JSON dans `src/generated/`
- Worldgen via `DatapackBuiltinEntriesProvider` + `RegistrySetBuilder` (ConfiguredFeature → PlacedFeature → BiomeModifier)
- Persistence joueur via NBT player tags (à migrer vers `IAttachment` NeoForge)
- Effets visuels client découplés via `@EventBusSubscriber(Dist.CLIENT)`

---

## Démarrage & build

### Prérequis
- JDK 21
- Connexion internet (premier lancement télécharge NeoForge + dépendances)

### Lancer en développement
```bat
gradlew runClient
```

### Générer les ressources (datagen)
```bat
gradlew runData
```
Produit les fichiers dans `src/generated/resources/`.

### Build pour serveur
```bat
build_mod.bat
```
Ou directement :
```bat
gradlew clean build
```
Le JAR est produit dans `build/libs/emeraldweapons-1.1.0.jar`.
Copier ce JAR dans le dossier `/mods` du serveur (supprimer l'éventuel JAR 1.0.0).

### Générer la texture animée Fulgurite (outil Python)
```bat
python generate_fulgurite.py
```
Nécessite Pillow (`pip install Pillow`). Lit `fulgurite.png`, écrit 12 frames empilées.

---

## État d'avancement

### Terminé / fonctionnel
- Arcencium (minerai, bloc, lingot, génération de monde, recettes, loot tables)
- EmeraldWindblade avec tous ses effets de combat (Crystalline Aura, Thunder, 5 cristaux)
- CrystallineAuraEffect (modificateurs d'attributs, rendu couleur)
- Système de particules (6 types, renderers client dédiés)
- WastelanderEntity (IA, rôles, dialogues)
- KGDeathbotEntity (3 variantes, scan, aura Commander)
- Système de quêtes (4 quêtes, 3 types de conditions, persistance NBT)
- Datagen complet (tags, worldgen, modèles)
- Creative tabs personnalisés
- Rendu client (orbites, animation main, item renderer)

### En cours / incomplet
- `SpargusCityStructure` : enregistrée mais méthode `postProcess()` vide
- `HavenCityStructure` : enregistrée, génération procédurale partiellement écrite (~50 %)
- Textures des entités : utilisent des modèles vanilla (HumanoidModel + textures zombie/iron_golem) — pas de modèles custom
- Épée Fulgurite : script Python et `.mcmeta` prêts, item non enregistré dans `ModItems`

### Non commencé (prévu)
- Autres armes Arcencium (axe, pioche, pelle — code commenté dans les fichiers)
- Intégration complète Fulgurite (item, mécaniques Fureur Cristalline, recette)
- Trading Marchand (rôle 1 du Wastelander non câblé)

---

## Détails notables

- **Probabilité Thunder corrigée** : la valeur était `0.93` (93 %) au lieu de `0.03` (3 %) — corrigée dans `EmeraldWindblade.java`
- **Thunder très coûteux** : génère ~200 particules par déclenchement ; acceptable en solo, à surveiller sur serveur multi
- **WeakHashMap** pour les cooldowns et zones électrifiées (`colorEffectCooldowns`, `electrifiedZones`) — évite les fuites mémoire sur entités déchargées
- **Cache quêtes** : `PlayerQuestData` utilise un `HashMap` statique non nettoyé — risque de fuite sur serveur avec rotation de joueurs importante
- **Texture Fulgurite** : le fichier `fulgurite.png` actuel contient une texture 1254×15048 (12 frames × 1254 px) générée par `generate_fulgurite.py` avec `BASE_HUE_SHIFT = 0.73` (thème vert émeraude) ; le `.mcmeta` est en place (`frametime: 2`, `interpolate: true`)
- **Localisation** : uniquement `en_us.json` (13 entrées) — pas de `fr_fr.json`
- **Licence** : All Rights Reserved (pas de redistribution)

---

## Pistes / à faire

- **Finaliser les structures** `SpargusCityStructure` et `HavenCityStructure` (implémenter `postProcess()`)
- **Enregistrer la Fulgurite** dans `ModItems`, créer sa classe d'item avec la mécanique *Fureur Cristalline* (accumulation de charges sur kills, proc ultime à 5 charges)
- **Migrer la persistance des quêtes** vers `IAttachment` NeoForge (API prévue pour ça, plus robuste que NBT manuel)
- **Ajouter `fr_fr.json`** pour la localisation française
- **Modèles custom** pour WastelanderEntity et KGDeathbotEntity (remplacer HumanoidModel vanilla)
- **Nettoyer les textures** `src/main/resources/assets/emeraldweapons/textures/item/` (10+ versions alternatives de l'épée accumulées durant le développement)
- **Optimiser le Thunder** : limiter le nombre de particules selon la distance au joueur ou la qualité graphique
