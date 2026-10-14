@echo off
chcp 65001 >nul
echo 1/3 Compilation du JAR...
call build_jar.bat --no-pause
if %errorlevel% neq 0 (
    echo [ERREUR] Impossible de continuer sans le JAR compilé.
    if "%1" neq "--no-pause" pause
    exit /b %errorlevel%
)

echo.
echo 2/3 Génération de l'exécutable Windows ChatReseau.exe avec jpackage...
if exist dist rmdir /s /q dist
if exist build_input rmdir /s /q build_input
mkdir build_input
copy /y CHAT_RESEAU.jar build_input\CHAT_RESEAU.jar >nul

jpackage --type app-image --input build_input --main-jar CHAT_RESEAU.jar --main-class chatreseau.Main --name "ChatReseau" --dest dist

rmdir /s /q build_input

if %errorlevel% equ 0 (
    echo.
    echo 3/3 Compression de l'exécutable dans ChatReseau_Windows_x64.zip...
    if exist ChatReseau_Windows_x64.zip del /f /q ChatReseau_Windows_x64.zip
    tar -a -c -f ChatReseau_Windows_x64.zip -C dist ChatReseau
    echo.
    echo [SUCCÈS] Exécutable Windows autonome généré :
    echo - Exécutable : dist\ChatReseau\ChatReseau.exe
    echo - Archive ZIP : ChatReseau_Windows_x64.zip
    echo.
) else (
    echo [ERREUR] Échec de la génération de l'exécutable .exe.
    if "%1" neq "--no-pause" pause
    exit /b %errorlevel%
)
if "%1" neq "--no-pause" pause
