@echo off
chcp 65001 >nul
if exist dist\ChatReseau\ChatReseau.exe (
    start "" "dist\ChatReseau\ChatReseau.exe"
) else if exist CHAT_RESEAU.jar (
    start "" javaw -jar CHAT_RESEAU.jar
) else (
    call build_jar.bat --no-pause
    start "" javaw -jar CHAT_RESEAU.jar
)
