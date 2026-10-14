@echo off
chcp 65001 >nul
echo Compilation du projet Java...
if not exist out mkdir out
javac -encoding UTF-8 -d out src\chatreseau\model\*.java src\chatreseau\network\*.java src\chatreseau\util\*.java src\chatreseau\ui\*.java src\chatreseau\Main.java
if %errorlevel% neq 0 (
    echo [ERREUR] Erreur lors de la compilation.
    if "%1" neq "--no-pause" pause
    exit /b %errorlevel%
)

echo Création du fichier exécutable CHAT_RESEAU.jar...
jar cfe CHAT_RESEAU.jar chatreseau.Main -C out .
if %errorlevel% equ 0 (
    echo [SUCCÈS] Fichier CHAT_RESEAU.jar créé avec succès !
) else (
    echo [ERREUR] Échec de la création du fichier .jar.
    if "%1" neq "--no-pause" pause
    exit /b %errorlevel%
)
if "%1" neq "--no-pause" pause
