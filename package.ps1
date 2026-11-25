# Ryuu SmartPickup - build dist\RyuuSmartPickup\RyuuSmartPickup.exe (needs JDK 23+ with jpackage)
# Run run.ps1 once first so lib\ has the JavaFX jars.
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$lib = Join-Path $root "lib"
$build = Join-Path $root "build"
$dist = Join-Path $root "dist"
$version = "1.0"

foreach ($d in $build, (Join-Path $dist "RyuuSmartPickup")) { if (Test-Path $d) { Remove-Item -Recurse -Force $d } }
New-Item -ItemType Directory -Force (Join-Path $build "classes"), (Join-Path $build "input") | Out-Null

$sources = Get-ChildItem -Recurse (Join-Path $root "src") -Filter *.java | ForEach-Object { $_.FullName }
javac --module-path $lib --add-modules javafx.controls -d (Join-Path $build "classes") -encoding UTF-8 $sources
if ($LASTEXITCODE -ne 0) { exit 1 }
Copy-Item (Join-Path $root "src/com/ryuu/pickup/app.css") (Join-Path $build "classes/com/ryuu/pickup/app.css")
jar --create --file (Join-Path $build "input/ryuu-smartpickup.jar") -C (Join-Path $build "classes") .

jpackage --type app-image --name RyuuSmartPickup --app-version $version --dest $dist `
    --icon (Join-Path $root "assets/icon.ico") --input (Join-Path $build "input") --main-jar ryuu-smartpickup.jar --main-class com.ryuu.pickup.App `
    --module-path $lib --add-modules javafx.controls,java.desktop,java.net.http `
    --java-options "--enable-native-access=javafx.graphics" --java-options '-Dryuu.home=$APPDIR\..'
if ($LASTEXITCODE -ne 0) { exit 1 }

# photos, restaurants and the offline map sit next to the exe
$assets = Join-Path $dist "RyuuSmartPickup/assets"
New-Item -ItemType Directory -Force $assets | Out-Null
Copy-Item -Recurse (Join-Path $root "assets/photos") $assets
Copy-Item (Join-Path $root "assets/restaurants.tsv"), (Join-Path $root "assets/coimbatore.map"), (Join-Path $root "assets/icon.png") $assets
Write-Host "Built $dist\RyuuSmartPickup\RyuuSmartPickup.exe"
