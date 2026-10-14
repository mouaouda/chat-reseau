@echo off
chcp 65001 >nul
echo 1/2 Compilation du JAR...
call build_jar.bat

echo.
echo 2/2 Génération de l'exécutable Windows ChatReseau.exe avec jpackage...
if exist dist rmdir /s /q dist
jpackage --type app-image --input . --main-jar CHAT_RESEAU.jar --main-class chatreseau.Main --name "ChatReseau" --dest dist

if %errorlevel% equ 0 (
    echo.
    echo [SUCCÈS] Exécutable Windows autonome généré dans :
    echo dist\ChatReseau\ChatReseau.exe
    echo.
    echo Cette application contient son propre runtime Java et fonctionne sur n'importe quel PC Windows sans avoir besoin d'installer Java !
    echo.
) else (
    echo [ERREUR] Échec de la génération de l'exécutable .exe.
)
pause
