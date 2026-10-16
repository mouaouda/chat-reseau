@echo off
chcp 65001 >nul

where gradle >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERREUR] Gradle n'est pas installe ou n'est pas dans le PATH.
    if "%1" neq "--no-pause" pause
    exit /b 1
)

echo [1/2] Build JavaFX avec Gradle (Java 21)...
gradle clean jar
if %ERRORLEVEL% NEQ 0 (
    echo [ERREUR] Echec du build Gradle.
    if "%1" neq "--no-pause" pause
    exit /b 1
)

echo.
echo [2/2] Copie du jar final vers CHAT_RESEAU.jar...
if exist build\libs\CHAT_RESEAU.jar (
    copy /y build\libs\CHAT_RESEAU.jar CHAT_RESEAU.jar >nul
    echo [SUCCES] CHAT_RESEAU.jar cree avec succes.
) else (
    echo [ERREUR] build\libs\CHAT_RESEAU.jar introuvable.
    if "%1" neq "--no-pause" pause
    exit /b 1
)

if "%1" neq "--no-pause" pause
