# Ryuu SmartPickup - build & run (needs JDK 23+; fetches JavaFX jars from Maven Central on first run)
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$fxVer = "26.0.2"
$lib = Join-Path $root "lib"
$out = Join-Path $root "out"

New-Item -ItemType Directory -Force $lib | Out-Null
foreach ($m in "base", "graphics", "controls") {
    $jar = Join-Path $lib "javafx-$m-$fxVer-win.jar"
    if (-not (Test-Path $jar)) {
        Write-Host "Downloading javafx-$m $fxVer ..."
        Invoke-WebRequest "https://repo1.maven.org/maven2/org/openjfx/javafx-$m/$fxVer/javafx-$m-$fxVer-win.jar" -OutFile $jar
    }
}

if (Test-Path $out) { Remove-Item -Recurse -Force $out }
$sources = Get-ChildItem -Recurse (Join-Path $root "src") -Filter *.java | ForEach-Object { $_.FullName }
javac --module-path $lib --add-modules javafx.controls -d $out -encoding UTF-8 $sources
if ($LASTEXITCODE -ne 0) { exit 1 }
Copy-Item (Join-Path $root "src/com/ryuu/pickup/app.css") (Join-Path $out "com/ryuu/pickup/app.css")

java --module-path $lib --add-modules javafx.controls --enable-native-access=javafx.graphics -cp $out com.ryuu.pickup.App
