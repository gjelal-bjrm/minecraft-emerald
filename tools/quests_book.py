"""
Le Carnet dans le livre de FTB Quests.

« Quand je parlais de systeme de quete, c'etait ici » -- le livre d'ATM10, et
« on s'en fiche de tous les autres chapitres, sauf ceux des artefacts et de
l'amelioration des armes ». Cet outil ecrit notre chapitre « Mode Arcencium »
(dix quetes, une par succes cache que le mod accorde -- voir quest/Quests) et
taille le livre du profil : tout ce qui n'est pas Artefacts, Reliques ou
Enchantement d'Apotheose disparait, avec une sauvegarde zip dans dist/.

Le texte est ecrit EN LIGNE dans le chapitre et dans les tables de langue
(fr_fr, en_us) : FTB Quests 2101 lit d'abord les tables, mais le joueur joue
en fr_fr et l'on ne veut pas dependre de ce que la table prefere.

    python tools/quests_book.py              # regenere, puis installe dans le profil (jeu ferme)
    python tools/quests_book.py --dev        # installe dans run/config pour le banc
    python tools/quests_book.py --generate   # ne fait que regenerer modpack/config/ftbquests/
    python tools/quests_book.py --instance "<chemin d'instance>"
"""
import datetime
import hashlib
import os
import re
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
from deploy_jar import running_java  # noqa: E402  (la meme garde : jamais dans un jeu ouvert)

DEFAULT_INSTANCE = Path(os.environ.get("USERPROFILE", "")) / "curseforge" / "minecraft" \
    / "Instances" / "Mode Arcencium"
SOURCE = ROOT / "modpack" / "config" / "ftbquests" / "quests"
DEV = ROOT / "run" / "config" / "ftbquests" / "quests"

# Les chapitres d'ATM10 que le livre garde (noms de fichier, sans .snbt).
KEEP = {"arcencium", "artifacts", "relics", "apothic_enchanting"}
BOOK_ICON = "emeraldweapons:arcencium_ingot"


def ident(name):
    """Un identifiant FTB (16 hex), le meme a chaque generation : les
    progressions sauvegardees s'y rattachent."""
    # LE BIT HAUT DOIT ETRE A ZERO. FTB lit l'identifiant comme un long
    # signe : un hexa qui commence par 8 a F ne se lit pas, et le livre lui en
    # attribue un autre au chargement -- la quete perd ses dependances et sa
    # progression sauvegardee. Le banc a vu quatre quetes sur dix changer d'id.
    digest = int(hashlib.sha1(("emeraldweapons/carnet/" + name).encode()).hexdigest()[:16], 16)
    return "%016X" % (digest & 0x7FFFFFFFFFFFFFFF)


CHAPTER_ID = ident("chapter")
CHAPTER_TITLE = "Mode Arcencium"
CHAPTER_SUBTITLE = "Le carnet du mode : dix étapes, dans l'ordre d'une partie. Chacune se coche toute seule."

# clef, titre, icone, description (une entree par ligne ; "" = ligne vide), recompenses
QUESTS = [
    ("arcencium", "Le premier Arcencium", "emeraldweapons:raw_arcencium", [
        "Descendez sous le niveau zéro, plus profond que le diamant. Une pioche en diamant en tire le brut ; une pioche en fer n'en tire que des éclats, et quatre éclats font un brut.",
        "",
        "Attendez l'&bAurore&r : elle montre les filons à travers la roche.",
        "",
        "&7Se coche avec un brut, un lingot ou quatre éclats dans le sac.",
    ], [("emeraldweapons:fate_shard", 2)]),
    ("lingot", "Trois lingots", "emeraldweapons:arcencium_ingot", [
        "Fondez le brut dans un four ordinaire, un pour un (le minerai donne deux bruts). Tout l'équipement du mode se fabrique en lingots d'Arcencium.",
        "",
        "&7Se coche avec trois lingots dans le sac.",
    ], [("emeraldweapons:forge_stone", 3)]),
    ("prisme", "Le bois de Prisme", "emeraldweapons:prism_branch", [
        "Chaque arme demande une &dbranche&r, chaque armure une &dfibre&r. L'Arbre de Prisme pousse en bosquets dans les plaines et les forêts ; la défense du village vous en a donné, et les coffres des sanctuaires en portent.",
        "",
        "Bûche → 4 planches de cristal · 2 planches → 4 branches · 3 feuilles → 1 fibre",
        "",
        "&7Se coche avec une branche ou une fibre dans le sac.",
    ], [("minecraft:emerald", 4)]),
    ("arme", "Votre première arme", "emeraldweapons:emerald_sword", [
        "Sur un établi ordinaire, avec les lingots, des émeraudes et du prisme. Quatre armes, chacune avec sa mécanique. Grilles lues ligne par ligne :",
        "",
        "&bÉpée d'émeraude&r   E N E / A A A / B . B",
        "&bGlaive&r   A E A / A F A / E B E",
        "&bSceptre&r   A E A / . A . / . B .",
        "&bArc&r   E A . / A R B / E A .",
        "",
        "&7A lingot d'Arcencium · E émeraude · B branche de Prisme · F fibre de Prisme · N épée en diamant · R arc ordinaire · . vide",
        "",
        "&7Se coche avec une arme du mode portée ou dans le sac.",
    ], [("emeraldweapons:forge_stone", 6), ("minecraft:iron_ingot", 8)]),
    ("forge", "La Forge : +1", "emeraldweapons:forge_stone", [
        "À la &6Forge d'Arcencium&r du village : posez la pièce, ayez une Pierre de Forge et le métal du cran (4 fer pour +1).",
        "",
        "Un échec ne fait jamais redescendre et vous rend le métal : il ne coûte que la Pierre.",
        "",
        "&7Se coche avec une pièce à +1 ou plus.",
    ], [("emeraldweapons:fate_shard", 3)]),
    ("rune", "Une rune gravée", "emeraldweapons:rune", [
        "À l'&6Établi à sertir&r : posez la pièce, posez une rune à côté, reprenez la pièce.",
        "",
        "Une rune d'arme va sur l'arme ou le casque ; une rune d'armure sur une armure. Son rang ne peut pas dépasser la rareté de la pièce.",
        "",
        "&7Se coche avec une pièce qui porte une rune.",
    ], [("emeraldweapons:arcencium_feather", 3)]),
    ("rarete", "Monter la rareté", "emeraldweapons:fate_shard", [
        "À l'&6Établi à sertir&r : posez la pièce, posez des Éclats du Destin à côté, reprenez la pièce. Le dé est lancé à la reprise.",
        "",
        "Plus d'Éclats, plus de chances ; et ce que vous avez déjà dépensé sur cette pièce vous reste acquis.",
        "",
        "&7Se coche avec une pièce de rang 2 ou plus.",
    ], [("emeraldweapons:arcencium_feather", 4)]),
    ("specialisation", "La spécialisation", "emeraldweapons:arcencium_feather", [
        "À l'&6Autel de spécialisation&r du village, avec des plumes d'Arcencium : +1 coûte trois plumes et réussit toujours.",
        "",
        "Vos ailes apparaissent, et la progression survit à la partie.",
        "",
        "&7Se coche au premier palier.",
    ], [("emeraldweapons:fate_shard", 2), ("minecraft:gold_ingot", 6)]),
    ("tombeau", "Éveiller un sceau", "emeraldweapons:tomb_seal", [
        "Dans un sanctuaire, cinq sceaux dorment dans la pyramide et les ailes. Cliquez-en un pour l'éveiller.",
        "",
        "Tous éveillés, l'ancre du sommet accepte l'Arcencium.",
        "",
        "&7Se coche au premier sceau éveillé, n'importe où.",
    ], [("emeraldweapons:arcencium_ingot", 8)]),
    ("ancre", "Tenir une ancre", "emeraldweapons:prismatic_anchor", [
        "Au sommet du sanctuaire, alimentez l'ancre en lingots d'Arcencium (8 pour la première) et tenez le siège.",
        "",
        "Trois ancres tenues lèvent l'&dArc-en-ciel&r et son boss.",
        "",
        "&7Se coche à la première ancre tenue.",
    ], [("emeraldweapons:forge_stone", 12)]),
]

# Une quete a part, hors chaine : l'atelier d'Apotheose, que le joueur voulait
# garder du livre d'ATM10 -- le chapitre des gemmes n'y est plus, on le dit ici.
GEMS = ("gemmes", "Les gemmes d'Apothéose", "apotheosis:gem", [
    "Nos armes et armures acceptent les &dgemmes&r et les affixes d'Apothéose comme n'importe quelle pièce.",
    "",
    "Dans l'atelier du village, à côté de la Forge : la &6Table de taille&r pour sertir une gemme, la &6Table de récupération&r pour la reprendre, la &6Table de reforge&r pour retirer les affixes.",
    "",
    "Les chapitres &eArtefacts&r, &eReliques&r et &eEnchantement d'Apothéose&r du livre d'ATM10 sont gardés plus bas.",
    "",
    "&7Cochez quand c'est lu.",
])


def q(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def chapter_snbt():
    out = ["{",
           "\tdefault_hide_dependency_lines: false",
           '\tdefault_quest_shape: ""',
           '\tfilename: "arcencium"',
           '\tgroup: ""',
           "\ticon: {",
           "\t\tid: %s" % q(BOOK_ICON),
           "\t}",
           "\tid: %s" % q(CHAPTER_ID),
           "\timages: [ ]",
           "\torder_index: 0",
           '\tprogression_mode: "flexible"',
           "\tquest_links: [ ]",
           "\tquests: ["]
    prev = None
    for i, (key, title, icon, desc, rewards) in enumerate(QUESTS):
        qid = ident(key)
        out.append("\t\t{")
        if prev:
            out.append("\t\t\tdependencies: [%s]" % q(prev))
        out.append("\t\t\tdescription: [")
        for line in desc:
            out.append("\t\t\t\t%s" % q(line))
        out.append("\t\t\t]")
        out.append("\t\t\ticon: {")
        out.append("\t\t\t\tid: %s" % q(icon))
        out.append("\t\t\t}")
        out.append("\t\t\tid: %s" % q(qid))
        out.append("\t\t\trewards: [")
        for j, (item, count) in enumerate(rewards):
            out.append("\t\t\t\t{")
            out.append("\t\t\t\t\tcount: %d" % count)
            out.append("\t\t\t\t\tid: %s" % q(ident(key + "/reward" + str(j))))
            out.append("\t\t\t\t\titem: {")
            out.append("\t\t\t\t\t\tcount: 1")
            out.append("\t\t\t\t\t\tid: %s" % q(item))
            out.append("\t\t\t\t\t}")
            out.append('\t\t\t\t\ttype: "item"')
            out.append("\t\t\t\t}")
        out.append("\t\t\t]")
        out.append("\t\t\tsize: 1.25d")
        out.append("\t\t\ttasks: [{")
        out.append("\t\t\t\tadvancement: %s" % q("emeraldweapons:carnet/" + key))
        out.append('\t\t\t\tcriterion: ""')
        out.append("\t\t\t\tid: %s" % q(ident(key + "/task")))
        out.append('\t\t\t\ttype: "advancement"')
        out.append("\t\t\t}]")
        out.append("\t\t\ttitle: %s" % q(title))
        out.append("\t\t\tx: %.1fd" % (i * 2.0))
        out.append("\t\t\ty: 0.0d")
        out.append("\t\t}")
        prev = qid
    key, title, icon, desc = GEMS
    out += ["\t\t{",
            "\t\t\tdescription: ["] + ["\t\t\t\t%s" % q(l) for l in desc] + [
            "\t\t\t]",
            "\t\t\ticon: {", "\t\t\t\tid: %s" % q(icon), "\t\t\t}",
            "\t\t\tid: %s" % q(ident(key)),
            "\t\t\toptional: true",
            '\t\t\tshape: "rsquare"',
            "\t\t\ttasks: [{",
            "\t\t\t\tid: %s" % q(ident(key + "/task")),
            '\t\t\t\ttype: "checkmark"',
            "\t\t\t}]",
            "\t\t\ttitle: %s" % q(title),
            "\t\t\tx: 0.0d",
            "\t\t\ty: 3.0d",
            "\t\t}"]
    out += ["\t]",
            "\tsubtitle: [%s]" % q(CHAPTER_SUBTITLE),
            "\ttitle: %s" % q(CHAPTER_TITLE),
            "}", ""]
    return "\n".join(out)


def lang_entries():
    lines = ["\tchapter.%s.title: %s" % (CHAPTER_ID, q(CHAPTER_TITLE)),
             "\tchapter.%s.chapter_subtitle: [%s]" % (CHAPTER_ID, q(CHAPTER_SUBTITLE))]
    for key, title, icon, desc in [(k, t, i, d) for k, t, i, d, r in QUESTS] + [GEMS]:
        qid = ident(key)
        lines.append("\tquest.%s.title: %s" % (qid, q(title)))
        lines.append("\tquest.%s.quest_desc: [%s]" % (qid, ", ".join(q(l) for l in desc)))
    return lines


def merge_lang(path):
    """Ajoute nos clefs a une table de langue FTB, en remplacant les notres."""
    ours = {CHAPTER_ID} | {ident(k[0]) for k in QUESTS} | {ident(GEMS[0])}
    body = []
    if path.exists():
        # FTB reecrit les tables a sa facon, les listes sur plusieurs lignes :
        # quand on retire une de nos entrees, on retire aussi ses lignes de
        # suite, jusqu'a ce que les crochets se referment. Sinon il reste des
        # lignes orphelines et la table entiere ne se lit plus.
        skip = 0
        for line in path.read_text(encoding="utf-8").splitlines():
            if skip > 0:
                skip += line.count("[") - line.count("]")
                continue
            m = re.match(r"\s*(chapter|quest)\.([0-9A-F]{16})\.", line)
            if m and m.group(2) in ours:
                skip = line.count("[") - line.count("]")
                continue
            if line.strip() in ("{", "}"):
                continue
            body.append(line)
    text = "{\n" + "\n".join(body + lang_entries()) + "\n}\n"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8", newline="\n")


def generate():
    (SOURCE / "chapters").mkdir(parents=True, exist_ok=True)
    (SOURCE / "chapters" / "arcencium.snbt").write_text(chapter_snbt(), encoding="utf-8", newline="\n")
    (SOURCE / "lang").mkdir(exist_ok=True)
    for loc in ("fr_fr", "en_us"):
        p = SOURCE / "lang" / (loc + ".snbt")
        if p.exists():
            p.unlink()
        merge_lang(p)
    print("genere : %s" % (SOURCE / "chapters" / "arcencium.snbt"))


def install(quests: Path, prune: bool):
    quests.mkdir(parents=True, exist_ok=True)
    (quests / "chapters").mkdir(exist_ok=True)
    (quests / "reward_tables").mkdir(exist_ok=True)
    shutil.copy(SOURCE / "chapters" / "arcencium.snbt", quests / "chapters" / "arcencium.snbt")
    for loc in ("fr_fr", "en_us"):
        merge_lang(quests / "lang" / (loc + ".snbt"))
    data = quests / "data.snbt"
    if data.exists():
        text = re.sub(r'(icon: \{\s*id: )"[^"]*"', r'\1"%s"' % BOOK_ICON, data.read_text(encoding="utf-8"))
        data.write_text(text, encoding="utf-8", newline="\n")
    else:
        data.write_text("{\n\tdefault_autoclaim_rewards: \"disabled\"\n\tdefault_consume_items: false\n"
                        "\tdefault_quest_shape: \"circle\"\n\tdetection_delay: 20\n"
                        "\ticon: {\n\t\tid: \"%s\"\n\t}\n\tprogression_mode: \"flexible\"\n"
                        "\tversion: 13\n}\n" % BOOK_ICON, encoding="utf-8", newline="\n")
    removed = 0
    if prune:
        keep_groups = set()
        for f in sorted((quests / "chapters").glob("*.snbt")):
            if f.stem not in KEEP:
                f.unlink()
                removed += 1
                continue
            m = re.search(r'^\s*group: "([0-9A-F]{16})"', f.read_text(encoding="utf-8"), re.M)
            if m:
                keep_groups.add(m.group(1))
        groups = quests / "chapter_groups.snbt"
        if groups.exists():
            ids = re.findall(r'\{ id: "([0-9A-F]{16})" \}', groups.read_text(encoding="utf-8"))
            kept = [i for i in ids if i in keep_groups]
            groups.write_text("{\n\tchapter_groups: [\n" + "".join('\t\t{ id: "%s" }\n' % i for i in kept)
                              + "\t]\n}\n", encoding="utf-8", newline="\n")
    if not (quests / "chapter_groups.snbt").exists():
        (quests / "chapter_groups.snbt").write_text("{\n\tchapter_groups: [ ]\n}\n", encoding="utf-8", newline="\n")
    left = sorted(f.stem for f in (quests / "chapters").glob("*.snbt"))
    print("  %s : %d chapitre(s) retire(s), restent %s" % (quests, removed, ", ".join(left)))


def main():
    args = sys.argv[1:]
    generate()
    if "--generate" in args:
        return
    if "--dev" in args:
        install(DEV, prune=False)
        return
    instance = DEFAULT_INSTANCE
    if "--instance" in args:
        instance = Path(args[args.index("--instance") + 1])
    quests = instance / "config" / "ftbquests" / "quests"
    if not quests.exists():
        sys.exit("pas de livre FTB Quests dans %s" % instance)
    wanted = str(instance).lower().replace("\\", "/")
    busy = [l for l in running_java() if wanted in l.lower().replace("\\", "/")]
    if busy:
        sys.exit("REFUS : un java tourne sur %s. On n'ecrit jamais dans une instance ouverte." % instance.name)
    stamp = datetime.datetime.now().strftime("%Y%m%d")
    backup = ROOT / "dist" / ("ftbquests_atm10_%s.zip" % stamp)
    if not backup.exists():
        backup.parent.mkdir(exist_ok=True)
        with zipfile.ZipFile(backup, "w", zipfile.ZIP_DEFLATED) as z:
            for f in quests.rglob("*"):
                if f.is_file():
                    z.write(f, f.relative_to(quests))
        print("  sauvegarde du livre ATM10 : %s" % backup)
    install(quests, prune=True)


if __name__ == "__main__":
    main()
