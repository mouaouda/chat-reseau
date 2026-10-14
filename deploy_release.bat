@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul

echo =====================================================================
echo           DÉPLOIEMENT AUTOMATIQUE D'UNE NOUVELLE RELEASE
echo                    CHAT RESEAU (GitHub CLI gh)
echo =====================================================================
echo.

set "TAG=%~1"
set "TITLE=%~2"
set "NOTES=%~3"
set "NO_PAUSE=%~4"

if "%TAG%"=="" (
    set /p "TAG=Entrez le tag de version (ex: v1.0.1) : "
)

if "%TAG%"=="" (
    echo [ERREUR] Le tag de version ne peut pas être vide.
    if "%NO_PAUSE%" neq "--no-pause" pause
    exit /b 1
)

if "%TITLE%"=="" (
    set /p "TITLE=Entrez le titre de la release (ex: v1.0.1 - Correctifs et Nouveautés) : "
)
if "%TITLE%"=="" (
    set "TITLE=Release %TAG%"
)

if "%NOTES%"=="" (
    set /p "NOTES=Entrez les notes de mise à jour : "
)
if "%NOTES%"=="" (
    set "NOTES=Mise à jour automatique de Chat Réseau (%TAG%)."
)

echo.
echo [1/5] Mise à jour du numéro de version (%TAG%) dans UpdateManager.java...
powershell -ExecutionPolicy Bypass -File set_version.ps1 "!TAG!"
if %errorlevel% neq 0 (
    echo [ERREUR] Échec de la mise à jour de la version dans le code.
    if "%NO_PAUSE%" neq "--no-pause" pause
    exit /b 1
)

echo.
echo [2/5] Compilation du code et création du fichier CHAT_RESEAU.jar...
call build_jar.bat --no-pause
if %errorlevel% neq 0 (
    echo [ERREUR] Échec de la compilation du JAR.
    if "%NO_PAUSE%" neq "--no-pause" pause
    exit /b 1
)

echo.
echo [3/5] Génération de l'exécutable Windows .exe et du fichier ChatReseau_Windows_x64.zip...
call build_exe.bat --no-pause
if %errorlevel% neq 0 (
    echo [ERREUR] Échec de la génération de l'exécutable .exe.
    if "%NO_PAUSE%" neq "--no-pause" pause
    exit /b 1
)

echo.
echo [4/5] Synchronisation Git (commit et push)...
git add .
git commit -m "release: !TAG! - !TITLE!"
git push origin main

echo.
echo [5/5] Publication de la release sur GitHub avec `gh`...
echo Téléversement de :
echo  - CHAT_RESEAU.jar
echo  - ChatReseau_Windows_x64.zip
echo.
gh release create !TAG! CHAT_RESEAU.jar ChatReseau_Windows_x64.zip --title "!TITLE!" --notes "!NOTES!" --clobber
if %errorlevel% equ 0 (
    echo.
    echo =====================================================================
    echo [SUCCÈS] La release !TAG! a été publiée sur GitHub avec succès !
    echo Fichiers attachés :
    echo  - CHAT_RESEAU.jar (pour mise à jour auto instantanée)
    echo  - ChatReseau_Windows_x64.zip (exécutable .exe autonome avec JRE)
    echo.
    echo Dès que les utilisateurs ouvrent leur application, elle détectera
    echo la version !TAG!, proposera l'installation en 1 clic et redémarrera
    echo automatiquement avec la nouvelle version !
    echo =====================================================================
) else (
    echo [ERREUR] Échec de la publication de la release sur GitHub via `gh`.
)

echo.
if "%NO_PAUSE%" neq "--no-pause" pause
