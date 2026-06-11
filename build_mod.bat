@echo off
chcp 65001 >nul
title EmeraldWeapons - Build

echo.
echo  ====================================
echo   EmeraldWeapons - Build du mod
echo  ====================================
echo.

:: Nettoyage + build
echo [1/2] Compilation en cours...
call gradlew.bat clean build --console=plain -x test
echo.

if %ERRORLEVEL% NEQ 0 (
    echo  [ERREUR] Le build a echoue. Verifie les logs ci-dessus.
    pause
    exit /b 1
)

:: Trouver le JAR produit
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
