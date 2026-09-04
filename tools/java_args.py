"""Regle la memoire et le ramasse-miettes des instances CurseForge.

CE QU'ON CORRIGE, ET POURQUOI.

Mesure du 4 septembre, ligne de commande du client du joueur :

    -Xmx16384m -Xms256m

Un tas qui part de 256 Mo pour monter a 16 Go passe son temps a se
redimensionner, et chaque agrandissement est une pause. Pire, au-dela de douze
gigaoctets les pauses de ramasse-miettes s'ALLONGENT : le collecteur a plus de
memoire a parcourir, pas moins de travail. La machine a 128 Go -- la RAM n'a
jamais ete la contrainte, c'est le REGLAGE qui l'etait.

On pose donc un tas FIXE de dix gigaoctets et le jeu d'options G1 eprouve sur
Minecraft moddé (dit « Aikar ») : pauses courtes et regulieres plutot que rares
et longues. C'est exactement ce qu'il faut a un jeu qui dessine soixante images
par seconde.

    python tools/java_args.py            # les instances connues
    python tools/java_args.py --show     # ne change rien, montre l'etat

Le script REFUSE de toucher a une instance dont le jeu tourne : CurseForge
reecrit `minecraftinstance.json` a la fermeture et effacerait le reglage.
"""

import json
import io
import os
import subprocess
import sys

INSTANCES = os.path.join(os.path.expanduser("~"), "curseforge", "minecraft", "Instances")
DEFAULTS = ["All the Mods 10 - CUSTOM", "Mode Arcencium"]

MEMORY_MB = 10240

ARGS = " ".join([
    "-Xms%dm" % MEMORY_MB,
    "-XX:+UseG1GC",
    "-XX:+ParallelRefProcEnabled",
    "-XX:MaxGCPauseMillis=50",
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+DisableExplicitGC",
    "-XX:+AlwaysPreTouch",
    "-XX:G1NewSizePercent=30",
    "-XX:G1MaxNewSizePercent=40",
    "-XX:G1HeapRegionSize=8M",
    "-XX:G1ReservePercent=20",
    "-XX:G1HeapWastePercent=5",
    "-XX:G1MixedGCCountTarget=4",
    "-XX:InitiatingHeapOccupancyPercent=15",
    "-XX:G1MixedGCLiveThresholdPercent=90",
    "-XX:G1RSetUpdatingPauseTimePercent=5",
    "-XX:SurvivorRatio=32",
    "-XX:+PerfDisableSharedMem",
    "-XX:MaxTenuringThreshold=1",
])


def running(name):
    try:
        out = subprocess.run(
            ["powershell", "-NoProfile", "-Command",
             "Get-CimInstance Win32_Process -Filter \"name='javaw.exe' or name='java.exe'\""
             " | ForEach-Object { $_.CommandLine }"],
            capture_output=True, text=True, timeout=90)
    except Exception:
        return True                       # dans le doute, on ne touche a rien
    return any(name.lower() in line.lower() and "-Xmx" in line
               for line in out.stdout.splitlines())


def main():
    show = "--show" in sys.argv
    for name in DEFAULTS:
        path = os.path.join(INSTANCES, name, "minecraftinstance.json")
        if not os.path.isfile(path):
            print("  %-28s absente" % name)
            continue
        data = json.load(io.open(path, encoding="utf-8"))
        print("  %-28s memoire %s Mo, override %s, args %s"
              % (name, data.get("allocatedMemory"), data.get("isMemoryOverride"),
                 "poses" if data.get("javaArgsOverride") else "aucun"))
        if show:
            continue
        if running(name):
            print("      REFUS : le jeu tourne sur cette instance, CurseForge reecrirait le fichier")
            continue
        data["allocatedMemory"] = MEMORY_MB
        data["isMemoryOverride"] = True
        data["javaArgsOverride"] = ARGS
        with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(data, fh, ensure_ascii=False, indent=2)
        print("      pose : %d Mo fixes + G1 regle" % MEMORY_MB)


if __name__ == "__main__":
    main()
