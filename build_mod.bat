@echo off
chcp 65001 >nul
title EmeraldWeapons - Build

echo.
echo  ====================================
echo   EmeraldWeapons - Build du mod
echo  ====================================
echo.

:: Nettoyage + build
echo [1/3] Compilation en cours...
call gradlew.bat clean build --console=plain -x test
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo  [ERREUR] Le build a echoue. Verifie les logs ci-dessus.
    pause
    exit /b 1
)
echo.

:: "clean" supprime build\moddev\, ou ModDevGradle stocke les arguments de
:: lancement du jeu. Sans cette etape, lancer le client depuis l'IDE echoue
:: avec : could not open build\moddev\clientRunVmArgs.txt
echo [2/3] Restauration de l'environnement de dev...
call gradlew.bat prepareClientRun --console=plain -q
if %ERRORLEVEL% NEQ 0 (
    echo  [ATTENTION] La preparation du run client a echoue.
    echo  Le JAR reste valide, mais relance "gradlew prepareClientRun"
    echo  avant de demarrer le jeu depuis l'IDE.
)
echo.

:: Trouver le JAR produit
echo [3/3] Verification du JAR...
set JAR_PATH=build\libs\emeraldweapons-1.1.0.jar

if exist "%JAR_PATH%" (
    echo  [OK] Build reussi !
    echo.
    echo  Fichier genere :
    echo    %~dp0%JAR_PATH%
    echo.
    echo  Copie ce fichier dans le dossier /mods de ton serveur.
    echo.
    :: Ouvrir le dossier build/libs dans l'explorateur
    explorer build\libs
) else (
    echo  [ERREUR] JAR introuvable : %JAR_PATH%
    echo  Verifie le dossier build\libs\ manuellement.
)

pause
