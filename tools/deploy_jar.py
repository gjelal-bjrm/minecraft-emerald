"""Pose le jar du mode dans les instances CurseForge -- JAMAIS sous un jeu ouvert.

POURQUOI CE SCRIPT EXISTE.

Le 4 septembre a 18h40, j'ai recopie le jar dans les deux instances pendant que
le client du joueur DEMARRAIT (18h38). Minecraft lisait le fichier au moment ou
il etait reecrit : le flux zip s'est retrouve coupe en deux, et le jeu a leve

    java.util.zip.ZipException: invalid distance too far back

sur chaque modele et chaque structure du mod. Resultat a l'ecran : TOUT notre
contenu en damier violet et noir, blocs, objets, onglet creatif et village
compris. Rien n'etait perdu -- le jar sur le disque etait juste, et un
redemarrage a tout retabli -- mais l'alerte etait legitime et la peur aussi.

La regle etait deja ecrite pour les lancements automatiques (« ne jamais fermer
la session du joueur ») ; elle vaut tout autant pour l'ECRITURE. Ce script la
rend mecanique : il refuse de toucher a une instance dont un java est en train
de tourner, et il verifie le zip apres copie.

    python tools/deploy_jar.py            # les instances connues
    python tools/deploy_jar.py --force    # seulement si l'on SAIT que le jeu est ferme
"""

import glob
import hashlib
import os
import shutil
import subprocess
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
INSTANCES = os.path.join(os.path.expanduser("~"), "curseforge", "minecraft", "Instances")
DEFAULTS = ["All the Mods 10 - CUSTOM", "Mode Arcencium"]


def running_java():
    """Les lignes de commande des java/javaw en cours. Vide si l'on ne sait pas lire."""
    try:
        out = subprocess.run(
            ["powershell", "-NoProfile", "-Command",
             "Get-CimInstance Win32_Process -Filter \"name='javaw.exe' or name='java.exe'\""
             " | ForEach-Object { $_.CommandLine }"],
            capture_output=True, text=True, timeout=60)
        return [line for line in out.stdout.splitlines() if line.strip()]
    except Exception as ex:                       # pas de PowerShell : on ne devine pas
        print("  (impossible de lister les processus : %s)" % ex)
        return []


def sound(path):
    try:
        return zipfile.ZipFile(path).testzip() is None
    except Exception:
        return False


def main():
    force = "--force" in sys.argv
    jars = sorted(glob.glob(os.path.join(ROOT, "build", "libs", "emeraldweapons-*.jar")))
    if not jars:
        raise SystemExit("Aucun jar dans build/libs : lancer ./gradlew build d'abord")
    jar = jars[-1]
    if not sound(jar):
        raise SystemExit("Le jar construit est lui-meme corrompu : %s" % jar)
    digest = hashlib.sha256(open(jar, "rb").read()).hexdigest()[:12]
    print("jar : %s (%s)" % (os.path.basename(jar), digest))

    lines = running_java()
    for name in DEFAULTS:
        instance = os.path.join(INSTANCES, name)
        mods = os.path.join(instance, "mods")
        if not os.path.isdir(mods):
            print("  %-28s absente" % name)
            continue
        busy = [l for l in lines if name.lower() in l.lower()]
        if busy and not force:
            print("  %-28s REFUS : un jeu tourne sur cette instance" % name)
            print("      Ferme la session, puis relance ce script.")
            continue
        dest = os.path.join(mods, os.path.basename(jar))
        shutil.copy2(jar, dest)
        ok = sound(dest)
        print("  %-28s pose%s" % (name, "" if ok else " -- MAIS LE ZIP EST CASSE, a refaire"))


if __name__ == "__main__":
    main()
