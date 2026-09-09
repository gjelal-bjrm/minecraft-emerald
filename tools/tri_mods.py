"""Trie les 438 mods du profil selon ce que le Mode Arcencium en fait vraiment.

Trois sources de verite, toutes MESUREES et non devinees :
  1. les espaces de noms que notre code Java et nos donnees citent ;
  2. les blocs et entites que nos structures .nbt posent reellement ;
  3. le graphe de dependances que CurseForge tient dans minecraftinstance.json.

Le reste est classe par famille, a la main, d'apres ce que fait chaque mod.
"""
import json, io, os, collections

S = os.path.dirname(os.path.abspath(__file__))
rows = json.load(io.open(f"{S}/mods.json", encoding="utf-8"))
by_id = {r["id"]: r for r in rows}
by_name = {r["name"]: r for r in rows}

# ------------------------------------------------------------------ les graines
# 1. Cites par notre code (mesure : grep des espaces de noms dans src/main).
CODE = [
    "Apotheosis", "Apothic Attributes", "Apothic Enchanting", "Apothic Spawners",
    "L_Ender 's Cataclysm", "The Twilight Forest", "Supplementaries",
    "Iron's Spells 'n Spellbooks", "Deeper and Darker", "The Undergarden",
    "Sophisticated Backpacks", "Lootr",
    "Actually Additions", "Gateways to Eternity",
]
# 2. Poses par nos structures (mesure : lecture des 169 fichiers .nbt).
STRUCT = [
    "When Dungeons Arise", "Create", "Farmer's Delight",
    "PneumaticCraft: Repressurized", "ChoiceTheorem's Overhauled Village",
    "CC: Tweaked", "Immersive Engineering", "Oh The Biomes We've Gone",
    "Advanced Peripherals", "Iron's Gems 'n Jewelry", "Sawmill",
]
# 3. Le rendu voulu : shaders, distance de vue, animations, textures liees.
# Distant Horizons, Better Combat, les deux Entity Features, Not Enough
# Animations et Player Animation Lib ne sont PAS des add-ons CurseForge de ce
# profil : ce sont des jars poses a la main, que l'export embarque tels quels.
VUE = [
    "Iris Shaders", "Embeddium", "Euphoria Patches",
    "Fusion (Connected Textures)", "Athena", "ConnectedTexturesMod",
    "Connected Glass",
]
# 4. Ce qui tient la performance, sans rien ajouter au jeu.
PERF = [
    "FerriteCore ((Neo)Forge)", "ImmediatelyFast", "Connectivity",
    "AllTheLeaks (Memory Leak Fix)", "Cupboard", "AI Improvements: Performance Tuning",
    "FastFurnace", "FastSuite", "FastWorkbench", "Clean Swing Through Grass",
    "I'm Fast", "Crash Assistant", "Crash Utilities", "Better Compatibility Checker",
    "Bad Wither No Cookie - Reloaded", "Cobweb", "Framework", "Balm",
]
# 5. Le confort de jeu qu'on garde : cartes, recettes, inventaire, interface.
CONFORT = [
    "Just Enough Items (JEI)", "Jade", "JourneyMap",
    "AppleSkin", "Controlling", "Inventory Essentials", "Inventory Tweaks - ReFoxed",
    "Enchantment Descriptions", "Better Advancements", "Waystones", "Comforts",
    "Colorful Hearts", "Extreme sound muffler - (Neo)Forge", "Dark Mode Everywhere",
    "Sophisticated Core",
    "Harvest with ease", "Crafting Tweaks", "Akashic Tome", "Explorer's Compass",
    "Corail Tombstone", "Cosmetic Armor Reworked", "Curios API",
]

FAMILLES = [
    ("La machinerie", [
        "Applied Energistics", "AE2", "AdvancedAE", "ExtendedAE", "Applied Flux",
        "Applied Mekanistics", "Immersive Energistics", "Mekanism", "Modern Industrialization",
        "Extended Industrialization", "Powah", "Extreme Reactors", "Industrial Foregoing",
        "Ender IO", "Flux Networks", "Integrated ", "RFTools", "rftools", "Refined",
        "Functional Storage", "ExtraStorage", "Extra Disks", "EnderDrives", "Ender Storage",
        "DimStorage", "Compact Machines", "Hyperbox", "Entangled", "Cable Tiers",
        "Generator Galore", "Iron Jetpacks", "Iron Furnaces", "Steve's Carts", "Railcraft",
        "Quarryplus", "Additional Enchanted Miner", "Mining Gadgets", "Building Gadgets",
        "Charging Gadgets", "Laser", "Hostile Neural Networks", "Xycraft", "Bigger Reactors",
        "Pipez", "Modular", "Immersive Petroleum", "Immersive Aircraft",
        "Gravitational Modulating", "AEInfinityBooster", "Interdimensional Wireless",
        "Cobblegen", "Item Collectors", "Fuel Goes Here", "Crafting on a stick",
        "Bridging Mod", "Construction Sticks", "Accelerated Decay", "Time In A Bottle",
        "Common Capabilities", "Cyclops Core", "CodeChicken", "Glodium", "Cucumber",
        "Almost Unified", "Solar", "Energy", "Storage Drawers", "Sophisticated Storage",
    ]),
    ("La magie et l'aventure d'ATM10", [
        "Ars ", "All The Arcanist", "All the Wizard", "Mystical Agriculture",
        "Mystical Agradditions", "Occultism", "EvilCraft", "Theurgy", "Forbidden and Arcanus",
        "Eternal Starlight", "Blue Skies", "Twilight", "Undergarden", "Deeper and Darker",
        "Artifacts", "Reliquary", "Gateways", "Apotheosis", "Cataclysm", "Aquaculture",
        "Alex's", "Creeper Overhaul", "Enderman Overhaul", "Friends and Foes",
        "Illager Warship", "Dungeon Crawl", "Explorify", "Formations", "Auroras",
        "Bibliobiomes", "Naturalist", "Born in Chaos", "Ice and Fire", "Mowzie",
        "Hardened Armadillos", "Baubley Heart Canisters", "Blue Flame Burning",
        "Everything is Copper", "Cat Jammies", "simplycats", "Incubation",
    ]),
    ("La construction et la decoration", [
        "Chipped", "Chisel", "Framed", "Domum Ornamentum", "Handcrafted", "Amendments",
        "Bibliocraft", "Bibliowoods", "Factory Blocks", "Cloud Glass", "Glassential",
        "Dyenamics", "Camol", "Additional Lights", "Byzantine Styles", "Crystalix",
        "Beautify", "Macaw", "Rechiseled", "Decorative",
    ]),
    ("La colonie et les quetes", [
        "Minecolonies", "MineColonies", "BlockUI", "Structurize", "Multi-Piston",
        "FTB Quests", "FTB Chunks", "FTB Teams", "FTB Ranks", "FTB Essentials",
        "FTB Library", "FTB Ultimine", "FTB Filter", "FTB JEI", "FTB XMod",
        "Cooking for Blockheads", "Farming for Blockheads", "Easy Villagers",
        "Cristel Lib",
    ]),
    ("Les bibliotheques", [
        "API", "Lib", "lib", "Core", "Bookshelf", "GeckoLib", "Balm", "Athena",
        "Architectury", "Cloth Config", "Curios", "Caelus", "Iceberg", "CorgiLib",
        "Placebo", "Puzzles", "Moonlight", "Kotlin", "Fzzy Config", "Cobweb",
        "Framework", "Almanac", "Atlas", "Deimos", "EdivadLib", "Glodium",
        "ExperienceLib", "Cristel", "Supermartijn", "Patchouli", "TerraBlender",
        "Resourceful", "Team Reborn", "Silent Lib", "Zeta", "Collective",
        "Prickle", "Sinytra", "MixinExtras", "Searchables", "YUNG's API",
    ]),
]


def famille(name):
    for label, keys in FAMILLES:
        for k in keys:
            if k.lower() in name.lower():
                return label
    return "Le reste"


# --------------------------------------------------------------- la fermeture
# NOM EXACT, ET NON UN MORCEAU DE NOM. « Create » attrapait Create Crafts &
# Additions, Dragons Plus et Enchantment Industry ; « Sophisticated » attrapait
# les deux integrations Create. On garde ce qu'on a nomme, rien de plus.
GROUPES = {"noyau": CODE, "decor": STRUCT, "vue": VUE, "perf": PERF, "confort": CONFORT}
origine, seed_ids, introuvables = {}, set(), []
for role, wants in GROUPES.items():
    for want in wants:
        hit = [r for r in rows if r["name"].lower().startswith(want.lower())]
        if not hit:
            hit = [r for r in rows if want.lower() in r["name"].lower()]
        if hit:
            r = min(hit, key=lambda r: len(r["name"]))
            seed_ids.add(r["id"])
            origine.setdefault(r["id"], role)
        else:
            introuvables.append(want)

garde = set(seed_ids)
frontier = list(seed_ids)
while frontier:
    cur = frontier.pop()
    for dep in by_id.get(cur, {}).get("deps", []):
        if dep in by_id and dep not in garde:
            garde.add(dep)
            frontier.append(dep)

seuls_deps = garde - seed_ids
retire = [r for r in rows if r["id"] not in garde]

rapport = {
    "total": len(rows),
    "garde": len(garde),
    "retire": len(retire),
    "introuvables": introuvables,
    "seed": sorted(by_id[i]["name"] for i in seed_ids),
    "par_role": {role: sorted(by_id[i]["name"] for i in seed_ids if origine.get(i) == role)
                 for role in GROUPES},
    "deps": sorted(by_id[i]["name"] for i in seuls_deps),
    "retire_par_famille": {},
}
fam = collections.defaultdict(list)
for r in retire:
    fam[famille(r["name"])].append(r["name"])
for k in sorted(fam, key=lambda k: -len(fam[k])):
    rapport["retire_par_famille"][k] = sorted(fam[k])

json.dump(rapport, io.open(f"{S}/tri.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(f"total {rapport['total']} | garde {rapport['garde']} | retire {rapport['retire']}")
print("noms cherches et introuvables :", introuvables)
for k, v in rapport["retire_par_famille"].items():
    print(f"  {k:38} {len(v)}")
