"""Ecrit les tables de butin des sanctuaires, un palier par ancre.

Pourquoi des TABLES et non un remplissage par code : Lootr donne a chaque
joueur son propre tirage, et pour cela il lui faut une table. Un coffre rempli
a l'avance serait partage, ce qui va contre tout l'interet du mod en
multijoueur.

Pourquoi des ETIQUETTES pour les objets d'autres mods : une table qui nomme un
objet absent ne se charge pas du tout. Les entrees d'etiquette, elles, se
declarent « non requises » et disparaissent en silence si le mod manque. Le
mode tourne donc sans Apotheosis, avec un butin plus maigre, sans jamais
casser.

L'echelle est celle du cahier : la premiere ancre paie moyennement, la
deuxieme bien, la troisieme doit armer pour le boss. Un coffre de palier trois
doit valoir le siege qu'on vient de tenir.
"""

import io
import re
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "emeraldweapons")

MOD = "emeraldweapons"

# Les artefacts, LUS DANS L'ENUMERATION JAVA.
#
# Ils y etaient recopies a la main. La liste avait deja pris du retard : quatre
# artefacts de Glaive existaient dans le jeu, avec leur texture et leur effet,
# et ne pouvaient tomber d'aucun coffre -- une recompense qui n'existe que dans
# le code creatif n'est pas une recompense.
#
# Une seule source, donc, et c'est le Java qui fait foi. Ajouter un artefact
# a l'enumeration suffit desormais a le faire entrer dans les coffres.
ARTIFACT_ENUM = os.path.join(ROOT, "src", "main", "java", "com", "emerald",
                             "artifact", "Artifact.java")


def read_artifacts():
    src = io.open(ARTIFACT_ENUM, encoding="utf-8").read()
    body = src[src.index("public enum Artifact"):src.index("public enum Socket")]
    names = re.findall(r"^\s{4}([A-Z][A-Z_0-9]*)\(Socket\.", body, re.M)
    if len(names) < 20:
        raise SystemExit("Enumeration illisible : %d artefacts trouves" % len(names))
    return [n.lower() for n in names]


ARTIFACTS = read_artifacts()

# Les materiaux d'Apotheosis, par rarete croissante. Facultatifs.
APOTH = {
    1: ["apotheosis:common_material", "apotheosis:uncommon_material",
        "apotheosis:gem_dust", "apotheosis:sigil_of_socketing"],
    2: ["apotheosis:uncommon_material", "apotheosis:rare_material",
        "apotheosis:gem_fused_slate", "apotheosis:sigil_of_socketing",
        "apotheosis:sigil_of_rebirth", "apotheosis:vial_of_extraction"],
    3: ["apotheosis:rare_material", "apotheosis:epic_material",
        "apotheosis:mythic_material", "apotheosis:sigil_of_enhancement",
        "apotheosis:superior_sigil_of_socketing", "apotheosis:sigil_of_rebirth",
        "apotheosis:lucky_foot", "apotheosis:boss_summoner"],
}


def tag(entries):
    """Une etiquette dont chaque entree est facultative."""
    return {"replace": False,
            "values": [{"id": e, "required": False} for e in entries]}


def item(name, weight=1, count=None, functions=None):
    entry = {"type": "minecraft:item", "name": name, "weight": weight}
    fns = list(functions or [])
    if count:
        fns.append({"function": "minecraft:set_count",
                    "count": {"min": count[0], "max": count[1]}})
    if fns:
        entry["functions"] = fns
    return entry


def tag_entry(name, weight=1, count=None):
    entry = {"type": "minecraft:tag", "name": name, "expand": True, "weight": weight}
    if count:
        entry["functions"] = [{"function": "minecraft:set_count",
                               "count": {"min": count[0], "max": count[1]}}]
    return entry


def artifact(name, weight=1):
    """Un artefact precis : sa composante porte son identite."""
    return item("%s:artifact" % MOD, weight, functions=[
        {"function": "minecraft:set_components",
         "components": {"%s:artifact" % MOD: name}}])


def pool(rolls, entries, bonus=None):
    p = {"rolls": rolls, "entries": entries}
    if bonus:
        p["bonus_rolls"] = bonus
    return p


def table(tier):
    """Le contenu d'un coffre, pour un palier d'ancre."""
    pools = []

    # 1. PAS d'arcencium en coffre, ou presque.
    #
    # Il en tombait a chaque coffre, multiplie par les etages de quatre tours
    # et les quatre de la salle du tresor : on n'avait plus aucune raison de
    # miner, et l'activation des ancres -- qui est censee coute cher -- se
    # payait toute seule en visitant. Il n'en reste qu'un filet de minerai
    # BRUT, rare, qu'il faut encore fondre.
    # 2. La matiere premiere et le bois, pour fabriquer sur place
    pools.append(pool({"min": 1, "max": 2}, [
        # L'Eclat du Destin ne se fabrique pas : c'est la seule monnaie du
        # mode qu'on ne puisse pas produire a volonte, donc la seule qui garde
        # sa valeur. Sa quantite monte avec le palier, ce qui fait des
        # tentatives de haute rarete une recompense de fin de partie.
        item("%s:fate_shard" % MOD, 6, (1, 1 + tier * 2)),
        item("%s:raw_arcencium" % MOD, 1, (1, 3)),
        item("%s:prism_branch" % MOD, 4, (4, 10)),
        item("%s:prism_fiber" % MOD, 4, (4, 10)),
    ]))

    # 3. Le materiel d'Apotheosis : de quoi reforger et sertir
    pools.append(pool({"min": 2, "max": 3 + tier}, [
        tag_entry("%s:sanctuary/tier%d" % (MOD, tier), 10, (1, 1 + tier)),
    ]))

    # 4. La consommation qui sauve : plus le palier monte, plus elle est franche
    heal = {
        1: [item("minecraft:golden_apple", 6, (2, 4)),
            item("minecraft:cooked_beef", 4, (8, 16))],
        2: [item("minecraft:golden_apple", 6, (4, 8)),
            item("minecraft:enchanted_golden_apple", 2, (1, 1))],
        3: [item("minecraft:enchanted_golden_apple", 6, (2, 4)),
            item("minecraft:totem_of_undying", 3, (1, 1))],
    }[tier]
    pools.append(pool({"min": 1, "max": 2}, heal))

    # 5. L'equipement. Au palier trois, on trouve NOTRE armure : c'est le
    #    moment de la partie ou l'on doit pouvoir affronter le boss.
    gear = {
        1: [item("minecraft:diamond", 6, (4, 9)),
            item("minecraft:iron_block", 4, (1, 3)),
            item("minecraft:enchanted_book", 3),
            item("minecraft:experience_bottle", 5, (16, 28))],
        2: [item("minecraft:diamond", 6, (8, 16)),
            item("minecraft:netherite_scrap", 3, (2, 4)),
            item("minecraft:enchanted_book", 3),
            item("minecraft:experience_bottle", 5, (24, 48))],
        3: [item("minecraft:netherite_ingot", 5, (1, 3)),
            item("%s:arcencium_helmet" % MOD, 2),
            item("%s:arcencium_chestplate" % MOD, 2),
            item("%s:arcencium_leggings" % MOD, 2),
            item("%s:arcencium_boots" % MOD, 2),
            item("minecraft:experience_bottle", 4, (32, 64))],
    }[tier]
    pools.append(pool({"min": 1, "max": 1 + tier}, gear))

    # 6. Les artefacts, la vraie recompense.
    #
    # Le palier un n'en donnait AUCUN, ce qui etait une erreur de dosage : c'est
    # le premier donjon de la partie, celui qu'on fouille avec le plus d'espoir,
    # et un coffre qui ne rend jamais rien de memorable cesse d'etre ouvert.
    # Il en donne desormais un sur quatre environ -- assez rare pour rester une
    # trouvaille, assez frequent pour qu'on fouille la douzaine de coffres d'une
    # tour en esperant.
    if tier == 1:
        pools.append(pool(1, [{"type": "minecraft:empty", "weight": 3 * len(ARTIFACTS)}]
                          + [artifact(name) for name in ARTIFACTS]))
    else:
        pools.append(pool(1 if tier == 2 else {"min": 1, "max": 2},
                          [artifact(name) for name in ARTIFACTS]))

    return {"type": "minecraft:chest", "pools": pools}


def write(path, payload):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(payload, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    print("ecrit %s" % os.path.relpath(path, ROOT))


def main():
    for tier in (1, 2, 3):
        write(os.path.join(DATA, "tags", "item", "sanctuary", "tier%d.json" % tier),
              tag(APOTH[tier]))
        write(os.path.join(DATA, "loot_table", "chests",
                           "sanctuary_tier%d.json" % tier),
              table(tier))


if __name__ == "__main__":
    main()
