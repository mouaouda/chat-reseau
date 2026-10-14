@echo off
chcp 65001 >nul
echo Compilation du projet Java...
if not exist out mkdir out
javac -encoding UTF-8 -d out src\chatreseau\model\*.java src\chatreseau\network\*.java src\chatreseau\util\*.java src\chatreseau\ui\*.java src\chatreseau\Main.java
if %errorlevel% neq 0 (
    echo Erreur lors de la compilation.
    pause
    exit /b %errorlevel%
)

echo Création du fichier exécutable CHAT_RESEAU.jar...
jar cfe CHAT_RESEAU.jar chatreseau.Main -C out .
if %errorlevel% equ 0 (
    echo.
    echo [SUCCÈS] Fichier CHAT_RESEAU.jar créé avec succès !
    echo Vous pouvez le lancer en double-cliquant dessus ou via : java -jar CHAT_RESEAU.jar
    echo.
) else (
    echo [ERREUR] Échec de la création du fichier .jar.
)
pause
