@echo off
chcp 65001 >nul
echo Compilation du Chat Reseau Local...
if not exist out mkdir out
javac -encoding UTF-8 -d out src\chatreseau\model\*.java src\chatreseau\network\*.java src\chatreseau\util\*.java src\chatreseau\ui\*.java src\chatreseau\Main.java
if %errorlevel% neq 0 (
    echo Erreur lors de la compilation.
    pause
    exit /b %errorlevel%
)
echo Lancement du Chat Reseau Local...
start "" javaw -cp out chatreseau.Main
