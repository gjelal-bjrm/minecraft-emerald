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


# LES ARTEFACTS MAJEURS : ceux qui changent une fin de partie.
#
# Il n'y avait aucune hierarchie -- les trente-deux tombaient a poids egal des
# le premier coffre du premier sanctuaire. On pouvait donc trouver les
# Jambieres de Maree ou le Filtre de Brume avant d'avoir vu une seule tempete,
# et il ne restait plus rien a esperer des deux sanctuaires suivants.
#
# Ceux-la sont donc ECARTES du palier un, ordinaires au palier deux, et TROIS
# FOIS plus probables au palier trois. Le choix n'est pas une note de
# puissance brute : ce sont ceux qui repondent a une menace de fin de partie
# (la Maree, les meteos, le coup fatal) ou qui multiplient une arme.
MAJOR = [
    "filtre_de_brume",        # les meteos agressives ne blessent plus
    "repere_d_echo",          # un artefact garanti par siege
    "plaque_de_gangue",       # survivre au coup fatal
    "coque_prismatique",      # l'onde de choc
    "jambieres_de_maree",     # rester dans la zone qui se ferme
    "bottes_de_retour",       # revenir a l'ancre
    "eclat_final",            # l'explosion a chaque mort
    "drain_de_cristal",       # le vol de vie
    "fleche_fourchue",        # trois fleches
    "ruee_en_chaine",         # trois bonds
]


def artifacts_for(tier):
    """Le tirage d'artefacts d'un palier : ce qu'on peut y trouver, et a quel poids."""
    if tier == 1:
        return [artifact(name) for name in ARTIFACTS if name not in MAJOR]
    if tier == 2:
        return [artifact(name) for name in ARTIFACTS]
    return [artifact(name, 3 if name in MAJOR else 1) for name in ARTIFACTS]


def table(tier):
    """Le contenu d'un coffre, pour un palier d'ancre.

    LA REGLE DE L'ECHELLE, et c'est la refonte de septembre : un coffre ne
    donne pas ce qui est CHER, il donne ce qui SERT MAINTENANT.

    On sortait du premier sanctuaire avec neuf diamants par coffre et des
    piles de fioles d'experience -- une fortune qui ne servait a rien, puisque
    le diamant n'entre dans l'amelioration qu'a +7 et qu'on en etait a +2. Le
    butin etait riche et inutile, ce qui est la pire combinaison : il donne le
    sentiment d'avoir tout eu sans rien donner a faire.

    Chaque palier paie donc l'etape qu'on est en train de vivre :

      - palier 1 : le fer et l'or (+1 a +6), les plumes (specialisation), les
        Eclats du Destin (rarete). Du diamant, oui, mais un filet ;
      - palier 2 : le diamant pour de bon (+7, +8), plus de plumes, les
        premiers eclats de netherite ;
      - palier 3 : netherite et Arcencium (+9, +10), les artefacts majeurs, et
        une petite chance de repartir avec une piece de NOTRE equipement.

    Les fioles d'experience ont disparu des trois paliers : l'experience du
    mode se gagne en tuant, le niveau de Heros ne les lit meme pas, et par
    seize coffres elles rendaient l'enchantement gratuit.
    """
    pools = []

    # 1. LA MONNAIE DU MODE : ce qui ne se fabrique pas.
    #
    # L'Eclat du Destin (rarete) et la Plume d'Arcencium (specialisation) sont
    # les deux seules ressources qu'on ne peut pas produire a volonte. Elles
    # sont donc dans les trois paliers, en quantite croissante : c'est ce qui
    # fait qu'un sanctuaire fait AVANCER le personnage et pas seulement le sac.
    #
    # PAS d'arcencium en coffre, ou presque : il en tombait a chaque coffre,
    # multiplie par les etages de quatre tours, et l'activation des ancres --
    # qui est censee couter cher -- se payait toute seule en visitant.
    pools.append(pool({"min": 1, "max": 2}, [
        item("%s:fate_shard" % MOD, 6, (1, 1 + tier * 2)),
        item("%s:arcencium_feather" % MOD, 5, (1, tier + 1)),
        item("%s:raw_arcencium" % MOD, 1, (1, 3)),
        item("%s:prism_branch" % MOD, 3, (4, 10)),
        item("%s:prism_fiber" % MOD, 3, (4, 10)),
    ]))

    # 2. Le materiel d'Apotheosis : de quoi reforger et sertir
    pools.append(pool({"min": 1, "max": 1 + tier}, [
        tag_entry("%s:sanctuary/tier%d" % (MOD, tier), 10, (1, 1 + tier)),
    ]))

    # 3. La consommation qui sauve : plus le palier monte, plus elle est franche
    heal = {
        1: [item("minecraft:golden_apple", 6, (1, 3)),
            item("minecraft:cooked_beef", 4, (6, 12))],
        2: [item("minecraft:golden_apple", 6, (2, 4)),
            item("minecraft:enchanted_golden_apple", 2, (1, 1))],
        3: [item("minecraft:enchanted_golden_apple", 6, (1, 2)),
            item("minecraft:totem_of_undying", 3, (1, 1))],
    }[tier]
    pools.append(pool(1, heal))

    # 4. LA FORGE : exactement les materiaux du cran ou l'on est.
    #
    # L'echelle d'amelioration est fer (+1 a +3), or (+4 a +6), diamant (+7,
    # +8), netherite (+9), Arcencium (+10). Le coffre suit cette echelle a la
    # lettre -- c'est la seule facon qu'un butin ait l'air d'avoir ete pense
    # pour le moment ou on l'ouvre.
    forge = {
        1: [item("minecraft:iron_ingot", 7, (6, 12)),
            item("minecraft:gold_ingot", 6, (4, 9)),
            item("minecraft:iron_block", 4, (1, 2)),
            item("%s:arcencium_feather" % MOD, 4, (1, 2)),
            item("minecraft:diamond", 2, (1, 3)),
            item("minecraft:enchanted_book", 2)],
        2: [item("minecraft:diamond", 7, (4, 8)),
            item("minecraft:gold_ingot", 4, (6, 12)),
            item("%s:arcencium_feather" % MOD, 5, (2, 3)),
            item("minecraft:netherite_scrap", 3, (1, 3)),
            item("minecraft:iron_block", 3, (2, 4)),
            item("minecraft:enchanted_book", 2)],
        3: [item("minecraft:diamond", 4, (6, 12)),
            item("minecraft:netherite_ingot", 5, (1, 3)),
            item("%s:arcencium_ingot" % MOD, 5, (2, 5)),
            item("%s:arcencium_feather" % MOD, 5, (3, 5)),
            item("minecraft:enchanted_book", 2)],
    }[tier]
    pools.append(pool({"min": 1, "max": 1 + tier}, forge))

    # 5. LE TRESOR DU DERNIER SANCTUAIRE : nos armes, et rarement.
    #
    # Nos quatre pieces d'armure tombaient d'un tirage a quatre lancers : on
    # ressortait habille de neuf sans avoir rien forge, ce qui vide de son sens
    # la Forge d'Arcencium. Elles passent donc dans un tirage a part, avec les
    # trois armes, et une chance sur treize environ -- de quoi esperer une
    # piece par sanctuaire, jamais la panoplie.
    if tier == 3:
        pools.append(pool(1, [{"type": "minecraft:empty", "weight": 200}]
                          + [item("%s:arcencium_helmet" % MOD, 4),
                             item("%s:arcencium_chestplate" % MOD, 4),
                             item("%s:arcencium_leggings" % MOD, 4),
                             item("%s:arcencium_boots" % MOD, 4),
                             item("%s:arcencium_glaive" % MOD, 2),
                             item("%s:arcencium_bow" % MOD, 2),
                             item("%s:arcencium_scepter" % MOD, 2)]))

    # 6. Les artefacts, la vraie recompense.
    #
    # Le palier un n'en donnait AUCUN, ce qui etait une erreur de dosage : c'est
    # le premier donjon de la partie, celui qu'on fouille avec le plus d'espoir,
    # et un coffre qui ne rend jamais rien de memorable cesse d'etre ouvert.
    # Il en donne desormais un sur quatre environ -- assez rare pour rester une
    # trouvaille, assez frequent pour qu'on fouille la douzaine de coffres d'une
    # tour en esperant -- mais jamais un majeur.
    #
    # LE TIRAGE EST LE MEME PARTOUT, seule la chance monte : un sur cinq au
    # palier un, un sur trois au deux, un sur deux au trois. Ils etaient
    # GARANTIS aux paliers deux et trois, ce qui, par vingt coffres, donnait
    # cinquante artefacts en une partie -- pour six emplacements. On ne
    # choisissait plus, on rangeait.
    entries = artifacts_for(tier)
    blanks = {1: 4, 2: 2, 3: 1}[tier]
    total = sum(e.get("weight", 1) for e in entries)
    pools.append(pool(1, [{"type": "minecraft:empty", "weight": blanks * total}] + entries))

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
