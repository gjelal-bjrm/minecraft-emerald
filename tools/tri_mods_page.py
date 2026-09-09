"""Ecrit MODS.html : les 438 mods du profil, tries, avec le verdict de chacun."""
import json, io, os, html

S = os.path.dirname(os.path.abspath(__file__))
ROOT = r"C:/Users/Gjelal/Desktop/Documents/Perso/Gjelal/Dev/MinecraftMods/EmeraldWeapons"
r = json.load(io.open(f"{S}/tri.json", encoding="utf-8"))
struct = json.load(io.open(f"{S}/struct_ns.json", encoding="utf-8"))

# Les jars poses a la main, hors CurseForge : l'export les embarque tels quels.
MANUELS = [
    ("Distant Horizons", "la vue au loin"),
    ("Better Combat", "les animations d'attaque"),
    ("Entity Texture Features", "les textures d'entites"),
    ("Entity Model Features", "les modeles d'entites"),
    ("Not Enough Animations", "les gestes du joueur"),
    ("Player Animation Lib", "le socle des animations"),
    ("CC: Tweaked", "aussi present en add-on"),
]

POURQUOI = {
    "Apotheosis": "gemmes, affixes, et le socle de rarete que le mode etend",
    "Apothic Attributes": "les attributs que nos runes modifient",
    "Apothic Enchanting": "l'enchantement d'Apotheosis",
    "Apothic Spawners": "les spawners de nos sanctuaires",
    "L_Ender 's Cataclysm": "la pyramide des sanctuaires et plusieurs boss",
    "The Twilight Forest": "des creatures de la Battue et des sieges",
    "Supplementaries": "cordes, poulies, brasiers : le decor du village",
    "Iron's Spells 'n Spellbooks": "les lanceurs de sorts des vagues",
    "Deeper and Darker": "les creatures sculk des paliers hauts",
    "The Undergarden": "des creatures des paliers hauts",
    "Lootr (Forge & NeoForge)": "chaque joueur a son propre tirage dans un coffre",
    "Gateways to Eternity": "les portails d'invocation d'Apotheosis",
    "Actually Additions": "un seul objet, dans le lot de depart",
    "Sophisticated Backpacks": "un seul objet, dans le lot de depart",
}

CSS = """
:root{--paper:#F0F1F5;--surface:#FFFFFF;--surface-2:#E7E9EF;--ink:#1A1922;--ink-soft:#4A4857;
--muted:#6E6B7C;--line:#D6D8E0;--line-soft:#E3E5EC;--accent:#0E7355;--accent-bg:#E2F1EB;
--keep:#0E7355;--keep-bg:#E2F1EB;--drop:#A33A52;--drop-bg:#F7E6EA;--maybe:#8A6A12;--maybe-bg:#F7EFDA;
--shadow:0 1px 2px rgba(26,25,34,.06),0 8px 24px -16px rgba(26,25,34,.32);}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){
--paper:#121118;--surface:#1A1923;--surface-2:#23222E;--ink:#EDECF2;--ink-soft:#C2C0CE;
--muted:#918EA1;--line:#2E2C3A;--line-soft:#26242F;--accent:#56DFB2;--accent-bg:#16302A;
--keep:#56DFB2;--keep-bg:#16302A;--drop:#FF8AA3;--drop-bg:#33161E;--maybe:#F0C560;--maybe-bg:#2E2612;
--shadow:0 1px 2px rgba(0,0,0,.5),0 10px 30px -18px rgba(0,0,0,.9);}}
:root[data-theme="dark"]{
--paper:#121118;--surface:#1A1923;--surface-2:#23222E;--ink:#EDECF2;--ink-soft:#C2C0CE;
--muted:#918EA1;--line:#2E2C3A;--line-soft:#26242F;--accent:#56DFB2;--accent-bg:#16302A;
--keep:#56DFB2;--keep-bg:#16302A;--drop:#FF8AA3;--drop-bg:#33161E;--maybe:#F0C560;--maybe-bg:#2E2612;
--shadow:0 1px 2px rgba(0,0,0,.5),0 10px 30px -18px rgba(0,0,0,.9);}

*{box-sizing:border-box}
body{margin:0;background:var(--paper);color:var(--ink);
font-family:"Karla",ui-sans-serif,system-ui,sans-serif;font-size:16.5px;line-height:1.6;
-webkit-font-smoothing:antialiased}
.page{max-width:980px;margin:0 auto;padding:0 24px 96px}
h1,h2,h3{font-family:"Fraunces",Georgia,serif;font-variation-settings:"SOFT" 20,"WONK" 1;
text-wrap:balance;margin:0}
p{margin:0 0 1em}p:last-child{margin-bottom:0}
a{color:var(--accent)}
:focus-visible{outline:2px solid var(--accent);outline-offset:3px;border-radius:3px}

.cover{padding:68px 0 8px}
.eyebrow{font-family:"JetBrains Mono",ui-monospace,monospace;font-size:11px;letter-spacing:.14em;
text-transform:uppercase;color:var(--muted);margin:0 0 16px}
.cover h1{font-size:clamp(36px,6vw,58px);font-weight:600;line-height:1.05;
letter-spacing:-.02em;margin-bottom:18px}
.lede{font-size:19px;color:var(--ink-soft);max-width:60ch;line-height:1.5}

.tally{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:1px;
background:var(--line);border:1px solid var(--line);border-radius:10px;overflow:hidden;margin:34px 0 8px}
.tally div{background:var(--surface);padding:14px 16px}
.tally .v{font-family:"JetBrains Mono",monospace;font-size:26px;font-weight:700;
line-height:1.1;font-variant-numeric:tabular-nums}
.tally .k{font-size:12.5px;color:var(--muted);margin-top:3px}

section{padding-top:52px}
section>h2{font-size:26px;font-weight:600;letter-spacing:-.015em;line-height:1.2;margin-bottom:8px}
.tag{display:inline-block;font-family:"JetBrains Mono",monospace;font-size:10.5px;
letter-spacing:.1em;text-transform:uppercase;padding:3px 8px;border-radius:20px;margin-bottom:10px}
.tag.keep{color:var(--keep);background:var(--keep-bg)}
.tag.drop{color:var(--drop);background:var(--drop-bg)}
.tag.maybe{color:var(--maybe);background:var(--maybe-bg)}
section>h2+p{margin-top:12px}
.why{color:var(--ink-soft);max-width:66ch}

.mods{list-style:none;margin:20px 0 0;padding:0;display:grid;gap:8px;
grid-template-columns:repeat(auto-fill,minmax(280px,1fr))}
.mods li{background:var(--surface);border:1px solid var(--line);border-radius:8px;
padding:9px 12px;font-size:14.5px;line-height:1.35}
.mods li b{font-weight:700;display:block}
.mods li span{color:var(--muted);font-size:13px}
.flat{list-style:none;margin:18px 0 0;padding:0;display:grid;gap:2px 18px;
grid-template-columns:repeat(auto-fill,minmax(215px,1fr));font-size:14px;color:var(--ink-soft)}
.flat li{padding:2px 0;border-bottom:1px solid var(--line-soft)}
.count{font-family:"JetBrains Mono",monospace;font-size:12px;color:var(--muted);
margin-left:8px;font-weight:400}
.note{border-left:2px solid var(--accent);padding:2px 0 2px 16px;color:var(--ink-soft);
margin:22px 0;font-size:15.5px}
.scroll{overflow-x:auto;margin:20px 0}
table{border-collapse:collapse;width:100%;font-size:14.5px;font-variant-numeric:tabular-nums}
th,td{text-align:left;padding:8px 14px 8px 0;border-bottom:1px solid var(--line-soft);vertical-align:top}
th{font-family:"JetBrains Mono",monospace;font-size:10.5px;letter-spacing:.1em;text-transform:uppercase;
color:var(--muted);font-weight:400;border-bottom-color:var(--line);white-space:nowrap}
tbody tr:last-child td{border-bottom:none}
.n{font-family:"JetBrains Mono",monospace;white-space:nowrap}
footer{margin-top:70px;padding-top:20px;border-top:1px solid var(--line);
font-size:13.5px;color:var(--muted)}
"""


def li(names, why=None):
    out = []
    for n in names:
        w = (why or {}).get(n)
        extra = f"<span>{html.escape(w)}</span>" if w else ""
        out.append(f"<li><b>{html.escape(n)}</b>{extra}</li>")
    return "\n".join(out)


def flat(names):
    return "\n".join(f"<li>{html.escape(n)}</li>" for n in names)


par = r["par_role"]
deps = r["deps"]
fam = r["retire_par_famille"]
garde_total = r["garde"] + len(MANUELS) - 1

body = f"""<title>Le tri des 438 mods</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,400;9..144,600&family=Karla:wght@400;500;700&family=JetBrains+Mono:wght@400;700&display=swap">
<style>{CSS}</style>

<div class="page">
<header class="cover">
  <p class="eyebrow">Profil Mode Arcencium · 9 septembre 2026</p>
  <h1>Le tri des 438 mods</h1>
  <p class="lede">Ton profil CurseForge porte la liste de mods d'All the Mods 10.
    Voici ce que le Mode Arcencium en utilise vraiment, mesuré dans le code, dans
    les gabarits de structures et dans le graphe de dépendances — et ce qui peut partir.</p>
  <div class="tally">
    <div><div class="v">438</div><div class="k">mods installés</div></div>
    <div><div class="v" style="color:var(--keep)">{r['garde']}</div><div class="k">à garder</div></div>
    <div><div class="v" style="color:var(--drop)">{r['retire']}</div><div class="k">à retirer</div></div>
    <div><div class="v">14</div><div class="k">cités par notre code</div></div>
  </div>
</header>

<main>

<section>
  <span class="tag keep">Indispensable</span>
  <h2>Le noyau : ce que le mode appelle par son nom<span class="count">{len(par['noyau'])}</span></h2>
  <p class="why">Mesuré : ce sont les seuls espaces de noms étrangers que notre code Java et
    nos fichiers de données citent. Retirer l'un d'eux casse quelque chose de visible.</p>
  <ul class="mods">{li(par['noyau'], POURQUOI)}</ul>
  <p class="note">Deux d'entre eux ne servent qu'à <strong>un seul objet</strong> du lot de départ :
    Actually Additions et Sophisticated Backpacks. Remplace ces deux objets et ces deux mods
    partent aussi.</p>
</section>

<section>
  <span class="tag maybe">Otages du décor</span>
  <h2>Ceux dont nos structures posent les blocs<span class="count">{len(par['decor'])}</span></h2>
  <p class="why">Notre village, notre citadelle et notre cathédrale ont été capturés dans un monde
    qui avait ces mods : leurs blocs sont littéralement dans nos gabarits. Les retirer laisse
    des trous. Mais ce n'est pas une dépendance de jeu, c'est une dépendance de décor :
    refaire les gabarits avec des blocs du jeu de base les libère tous.</p>
  <div class="scroll"><table>
    <thead><tr><th>Mod</th><th>Blocs posés</th><th>Où</th></tr></thead><tbody>
    <tr><td><strong>When Dungeons Arise</strong></td><td class="n">458</td><td>les spawners de la citadelle et de la cathédrale</td></tr>
    <tr><td><strong>Create</strong></td><td class="n">380</td><td>le décor du village : cuves, engrenages, machines à vapeur</td></tr>
    <tr><td><strong>Farmer's Delight</strong></td><td class="n">37</td><td>les cuisines du village</td></tr>
    <tr><td><strong>PneumaticCraft</strong></td><td class="n">36</td><td>murs et briques du village</td></tr>
    <tr><td><strong>CC: Tweaked</strong></td><td class="n">19</td><td>des écrans posés en décor</td></tr>
    <tr><td><strong>ChoiceTheorem's Overhauled Village</strong></td><td class="n">19</td><td>le village d'origine, dont le nôtre est tiré</td></tr>
    <tr><td><strong>Immersive Engineering</strong></td><td class="n">12</td><td>quelques pièces de décor</td></tr>
    <tr><td><strong>Oh The Biomes We've Gone</strong></td><td class="n">10</td><td>des bois du village</td></tr>
    <tr><td><strong>Advanced Peripherals</strong></td><td class="n">8</td><td>avec les écrans</td></tr>
    <tr><td><strong>Iron's Gems 'n Jewelry</strong></td><td class="n">2</td><td>deux objets de décor</td></tr>
    <tr><td><strong>Sawmill</strong></td><td class="n">1</td><td>un bloc</td></tr>
    </tbody></table></div>
  <p class="note">Trente-six autres mods sont cités par les gabarits mais <strong>ne sont
    pas installés</strong> : leurs blocs manquent déjà aujourd'hui, et tu ne l'as jamais
    remarqué. C'est la meilleure preuve que ce décor est remplaçable.</p>
</section>

<section>
  <span class="tag keep">Le rendu</span>
  <h2>La vue : shaders, distance, animations<span class="count">{len(par['vue']) + len(MANUELS) - 1}</span></h2>
  <p class="why">Ce que tu as choisi de voir. Les six premiers ne sont pas des add-ons
    CurseForge : ce sont des jars posés à la main, que l'export embarque tels quels.</p>
  <ul class="mods">{li([m[0] for m in MANUELS[:-1]], dict(MANUELS))}</ul>
  <ul class="flat">{flat(par['vue'])}</ul>
</section>

<section>
  <span class="tag keep">Le socle</span>
  <h2>Les bibliothèques entraînées<span class="count">{len(deps)}</span></h2>
  <p class="why">Personne ne les choisit : elles sont exigées par les mods ci-dessus.
    Calculé sur le graphe de dépendances de CurseForge, pas deviné.</p>
  <ul class="flat">{flat(deps)}</ul>
</section>

<section>
  <span class="tag keep">Performance</span>
  <h2>Ce qui tient le jeu debout<span class="count">{len(par['perf'])}</span></h2>
  <p class="why">Ces mods n'ajoutent rien au jeu, ils l'empêchent de ramer ou de planter.
    Aucun n'est indispensable au mode, mais retirer un chargeur de chunks ou un correctif
    de fuite mémoire se paie tout de suite.</p>
  <ul class="flat">{flat(par['perf'])}</ul>
</section>

<section>
  <span class="tag maybe">Au choix</span>
  <h2>Le confort de jeu<span class="count">{len(par['confort'])}</span></h2>
  <p class="why">Gardés par choix, pas par nécessité : la carte, les recettes, l'infobulle,
    les points de téléportation. Le mode tourne sans eux. C'est la liste où tu peux tailler
    selon tes goûts.</p>
  <ul class="flat">{flat(par['confort'])}</ul>
</section>

<section>
  <span class="tag drop">À retirer</span>
  <h2>Ce que le mode ne touche jamais<span class="count">{r['retire']}</span></h2>
  <p class="why">Aucun de ces mods n'est cité par notre code, ne pose un bloc dans nos
    structures, ni n'est exigé par ceux qu'on garde. Une partie de quatre-vingt-dix minutes
    n'a le temps d'en ouvrir aucun.</p>
"""

TITRES = {
    "La machinerie": "L'automatisation, le stockage et l'énergie. C'est le cœur d'All the Mods, "
                     "et c'est un jeu de plusieurs dizaines d'heures : l'opposé exact du nôtre.",
    "La magie et l'aventure d'ATM10": "D'autres systèmes de progression, qui entrent en concurrence "
                                      "avec le nôtre plutôt qu'ils ne le servent.",
    "La construction et la decoration": "Des milliers de blocs de décor qu'on ne pose jamais.",
    "La colonie et les quetes": "Minecolonies, FTB Quests et leurs satellites : une autre façon "
                               "de jouer, avec son propre livre de quêtes.",
    "Les bibliotheques": "Des bibliothèques qui ne servent qu'aux mods ci-dessus. Elles partent avec eux.",
    "Le reste": "Le solde : petits utilitaires, intégrations croisées, add-ons d'add-ons.",
}
for k, lst in fam.items():
    body += f"""
  <h3 style="font-size:18px;font-weight:600;margin:34px 0 6px">{html.escape(k)}<span class="count">{len(lst)}</span></h3>
  <p class="why" style="font-size:15px">{TITRES.get(k, '')}</p>
  <ul class="flat">{flat(lst)}</ul>
"""

body += """
</section>

<section>
  <span class="tag keep">La suite</span>
  <h2>Comment s'y prendre</h2>
  <p>Trois étapes, dans cet ordre, chacune vérifiable avant de passer à la suivante.</p>
  <div class="scroll"><table>
    <thead><tr><th></th><th>Étape</th><th>Ce que ça donne</th></tr></thead><tbody>
    <tr><td class="n">1</td><td>Retirer les 352 mods sans emploi</td><td>Le profil tombe à 86 mods. Rien de ce que le mode appelle ne bouge, mais les gabarits gardent leurs trous du décor.</td></tr>
    <tr><td class="n">2</td><td>Refaire les gabarits avec des blocs du jeu</td><td>Onze mods de plus peuvent partir, dont Create et When Dungeons Arise. Il reste environ 75 mods.</td></tr>
    <tr><td class="n">3</td><td>Trancher dans le confort</td><td>Ton choix : carte, recettes, téléportation. Un socle minimal tient sous 50 mods.</td></tr>
    </tbody></table></div>
  <p class="note">À faire sur une <strong>copie</strong> du profil, et en gardant la liste actuelle :
    un mod retiré par erreur ne se voit parfois qu'une demi-heure plus tard, quand un coffre
    est vide ou qu'un mur a un trou.</p>
</section>

</main>
<footer>Relevé du profil « Mode Arcencium » le 9 septembre 2026. Le noyau et le décor sont
mesurés dans les sources du mod ; les dépendances viennent du fichier d'instance de CurseForge.</footer>
</div>
"""

io.open(f"{ROOT}/MODS.html", "w", encoding="utf-8", newline="\n").write(body)
print("MODS.html ecrit :", len(body), "octets")
print("garde", r["garde"], "| retire", r["retire"])
