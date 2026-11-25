@echo off
rem Ryuu SmartPickup - double-click to launch (builds on first run)
cd /d "%~dp0"

if not exist "lib\javafx-controls-26.0.2-win.jar" (
    echo First run: downloading JavaFX and building...
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run.ps1"
    exit /b
)

if not exist "out\com\ryuu\pickup\App.class" (
    echo Building...
    dir /s /b src\*.java > "%TEMP%\ryuu_sources.txt"
    javac --module-path lib --add-modules javafx.controls -d out -encoding UTF-8 @"%TEMP%\ryuu_sources.txt"
    if errorlevel 1 ( pause & exit /b 1 )
    copy /y "src\com\ryuu\pickup\app.css" "out\com\ryuu\pickup\app.css" >nul
)

start "" javaw --module-path lib --add-modules javafx.controls --enable-native-access=javafx.graphics -cp out com.ryuu.pickup.App
