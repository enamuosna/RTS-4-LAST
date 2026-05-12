#Requires -Version 5.1
<#
.SYNOPSIS
    Build d'un installateur MSI Windows pour le client JavaFX RTS Caisse.

.DESCRIPTION
    Pipeline complet :
      1. Maven : génération du fat-JAR (toutes dépendances embarquées)
      2. jlink : création d'un runtime Java minimal avec les modules JavaFX
      3. jpackage : génération du .msi via WiX Toolset

.EXAMPLE
    .\build-msi.ps1
    .\build-msi.ps1 -Version "1.2.0" -Clean
    .\build-msi.ps1 -BackendUrl "http://192.168.1.50:8080/api"
    .\build-msi.ps1 -JdkHome "C:\Program Files\Java\jdk-21.0.11"
#>

[CmdletBinding()]
param(
    [string]$Version = "1.0.0",
    [string]$BackendUrl = "http://localhost:8090/api",
    [switch]$Clean,
    [ValidateSet('per-user', 'per-machine')]
    [string]$InstallType = 'per-machine',
    [string]$JdkHome = ""
)

$ErrorActionPreference = 'Stop'

# Force la console à utiliser UTF-8 (sinon les accents sont cassés sur PS 5.1)
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

# Compatible PS 7.3+ (ignoré sur PS 5.1)
$PSNativeCommandUseErrorActionPreference = $false

# ============================================================
# Configuration projet
# ============================================================
$AppName        = "RTS Caisse Client"
$AppVendor      = "RTS Sénégal"
$AppCopyright   = "© $(Get-Date -Format yyyy) RTS Sénégal"
$AppDescription = "Client de caisse pour les guichets RTS"
$MainJarName    = "rts-caisse-guichet.jar"                       # Nom utilisé dans build/input
$MainClass      = "sn.rts.caisse.guichet.app.Launcher"           # Wrapper qui n'étend PAS Application
$IconPath       = "src\main\resources\icons\rts.ico"
$LicensePath    = "LICENSE.txt"

$BuildDir   = "build"
$RuntimeDir = "$BuildDir\runtime"
$InputDir   = "$BuildDir\input"
$OutputDir  = "$BuildDir\msi"

$ModulesList = @(
    'java.base',
    'java.desktop',
    'java.logging',
    'java.naming',
    'java.net.http',
    'java.prefs',
    'java.sql',
    'java.xml',
    'jdk.crypto.ec',
    'jdk.unsupported',
    'javafx.controls',
    'javafx.fxml',
    'javafx.graphics'
)

# ============================================================
# Fonctions utilitaires
# ============================================================
function Assert-Tool {
    param([string]$Name, [string]$Hint)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Outil manquant : $Name. $Hint"
    }
}

function Get-NativeOutput {
    param([scriptblock]$Block)
    $previousEAP = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & $Block 2>&1 | Out-String
    }
    finally {
        $ErrorActionPreference = $previousEAP
    }
    return $output
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host " RTS Caisse Client - Build MSI v$Version"     -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan

if ($JdkHome) {
    if (-not (Test-Path $JdkHome)) {
        throw "JdkHome introuvable : $JdkHome"
    }
    $env:JAVA_HOME = $JdkHome
    $env:Path = "$JdkHome\bin;$env:Path"
    Write-Host "[INFO] JAVA_HOME forcé sur : $JdkHome" -ForegroundColor DarkGray
}

Assert-Tool 'mvn'    "Installe Maven et ajoute-le au PATH."
Assert-Tool 'candle' "WiX Toolset 3.14 manquant. Télécharge-le sur wixtoolset.org et ajoute son dossier bin\ au PATH."

if (-not $env:JAVA_HOME) {
    throw "JAVA_HOME non défini. Définis-le sur le chemin du JDK 21, ou utilise -JdkHome."
}
$javaExe     = Join-Path $env:JAVA_HOME 'bin\java.exe'
$jlinkExe    = Join-Path $env:JAVA_HOME 'bin\jlink.exe'
$jpackageExe = Join-Path $env:JAVA_HOME 'bin\jpackage.exe'

if (-not (Test-Path $javaExe))     { throw "java introuvable : $javaExe." }
if (-not (Test-Path $jlinkExe))    { throw "jlink introuvable : $jlinkExe. JAVA_HOME pointe-t-il bien vers un JDK ?" }
if (-not (Test-Path $jpackageExe)) { throw "jpackage introuvable : $jpackageExe. JAVA_HOME pointe-t-il bien vers un JDK ?" }

if (-not $env:JAVAFX_JMODS) {
    throw "Variable d'environnement JAVAFX_JMODS non définie. Pointe-la vers le dossier des jmods JavaFX 21."
}
if (-not (Test-Path $env:JAVAFX_JMODS)) {
    throw "JAVAFX_JMODS pointe vers un dossier inexistant : $env:JAVAFX_JMODS"
}

$javaVerOutput = Get-NativeOutput { & $javaExe -version }
$javaVerLine = ($javaVerOutput -split "`r?`n" | Where-Object { $_.Trim() } | Select-Object -First 1).Trim()

if ($javaVerLine -notmatch '"21\.') {
    Write-Warning "Tu n'utilises pas un JDK 21 ($javaVerLine)."
    Write-Warning "JavaFX JMods 21 est conçu pour le JDK 21."
    Write-Warning "Utilise -JdkHome 'C:\\chemin\\jdk-21' pour forcer la version."
}

$candleOutput = Get-NativeOutput { candle -? }
$candleLine = ($candleOutput -split "`r?`n" |
    Where-Object { $_ -match 'Microsoft|Windows Installer XML' } |
    Select-Object -First 1)
if (-not $candleLine) { $candleLine = "(version non identifiée)" }

Write-Host "[OK] JDK       : $javaVerLine"
Write-Host "[OK] JAVA_HOME : $env:JAVA_HOME"
Write-Host "[OK] jlink     : $jlinkExe"
Write-Host "[OK] jpackage  : $jpackageExe"
Write-Host "[OK] JavaFX    : $env:JAVAFX_JMODS"
Write-Host "[OK] WiX       : $($candleLine.Trim())"
Write-Host "[OK] MainClass : $MainClass"
Write-Host ""

# ============================================================
# 1. Nettoyage
# ============================================================
if ($Clean -and (Test-Path $BuildDir)) {
    Write-Host "[1/4] Nettoyage du dossier $BuildDir..." -ForegroundColor Yellow
    Remove-Item -Recurse -Force $BuildDir
}
New-Item -ItemType Directory -Force -Path $BuildDir, $InputDir, $OutputDir | Out-Null

# ============================================================
# 2. Maven : build du fat-JAR
# ============================================================
Write-Host "[2/4] Maven package..." -ForegroundColor Yellow

$env:RTS_BACKEND_URL = $BackendUrl

mvn clean package -DskipTests "-Dbackend.url=$BackendUrl"
if ($LASTEXITCODE -ne 0) { throw "Echec Maven (code $LASTEXITCODE)" }

# Récupère le JAR shadé produit dans target/
# IMPORTANT : avec maven-shade-plugin, le fat-JAR remplace le JAR classique,
# donc on prend juste le JAR principal de target/.
$shadedJar = Get-ChildItem -Path "target" -Filter "*.jar" |
    Where-Object { $_.Name -notmatch '(original|sources|javadoc)' } |
    Select-Object -First 1

if (-not $shadedJar) { throw "Aucun JAR trouvé dans target/. Vérifie ta config maven-shade-plugin." }

# Validation : un fat-JAR JavaFX devrait peser > 5 MB.
# Si on est en dessous, c'est que shade-plugin n'a pas embarqué les dépendances.
$jarSizeMB = [math]::Round($shadedJar.Length / 1MB, 2)
if ($jarSizeMB -lt 5) {
    Write-Warning "JAR suspect : $jarSizeMB MB (attendu > 5 MB pour un fat-JAR JavaFX)."
    Write-Warning "Vérifie que maven-shade-plugin est bien configuré dans pom.xml."
}

Copy-Item $shadedJar.FullName "$InputDir\$MainJarName" -Force
Write-Host "      JAR copié : $InputDir\$MainJarName ($jarSizeMB MB)"

# ============================================================
# 3. jlink : runtime Java minimal
# ============================================================
Write-Host "[3/4] jlink : création du runtime Java custom..." -ForegroundColor Yellow

if (Test-Path $RuntimeDir) { Remove-Item -Recurse -Force $RuntimeDir }

$modulePath = "$env:JAVA_HOME\jmods;$env:JAVAFX_JMODS"
$modules    = $ModulesList -join ','

& $jlinkExe `
    --module-path $modulePath `
    --add-modules $modules `
    --output $RuntimeDir `
    --strip-debug `
    --no-man-pages `
    --no-header-files `
    --compress=zip-6

if ($LASTEXITCODE -ne 0) { throw "Echec jlink (code $LASTEXITCODE)" }

$runtimeSize = (Get-ChildItem $RuntimeDir -Recurse | Measure-Object -Property Length -Sum).Sum
Write-Host "      Runtime généré : $RuntimeDir ($([math]::Round($runtimeSize / 1MB, 1)) MB)"

# ============================================================
# 4. jpackage : génération du MSI
# ============================================================
Write-Host "[4/4] jpackage : génération du MSI..." -ForegroundColor Yellow

$jpackageArgs = @(
    '--type',          'msi',
    '--name',          $AppName,
    '--app-version',   $Version,
    '--vendor',        $AppVendor,
    '--copyright',     $AppCopyright,
    '--description',   $AppDescription,
    '--input',         $InputDir,
    '--main-jar',      $MainJarName,
    '--main-class',    $MainClass,
    '--runtime-image', $RuntimeDir,
    '--dest',          $OutputDir,
    '--win-menu',
    '--win-menu-group', 'RTS',
    '--win-shortcut',
    '--win-dir-chooser',
    '--win-upgrade-uuid', '7e3c4f2a-5b91-4d8e-a6f3-1c2d8e4b9a01'
)

if ($InstallType -eq 'per-user') {
    $jpackageArgs += '--win-per-user-install'
}

if (Test-Path $IconPath) {
    $jpackageArgs += '--icon', $IconPath
} else {
    Write-Warning "Pas d'icône trouvée à $IconPath. L'installateur utilisera l'icône Java par défaut."
}

if (Test-Path $LicensePath) {
    $jpackageArgs += '--license-file', $LicensePath
}

# DECOMMENTER pour debug : ouvre une console qui affiche les erreurs Java
# $jpackageArgs += '--win-console'

& $jpackageExe @jpackageArgs
if ($LASTEXITCODE -ne 0) { throw "Echec jpackage (code $LASTEXITCODE)" }

# ============================================================
# Récap
# ============================================================
$msi = Get-ChildItem -Path $OutputDir -Filter "*.msi" | Select-Object -First 1
if (-not $msi) { throw "Aucun MSI généré dans $OutputDir." }

$msiSizeMB = [math]::Round($msi.Length / 1MB, 1)

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host " BUILD TERMINE AVEC SUCCES" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host " Fichier   : $($msi.FullName)"
Write-Host " Taille    : $msiSizeMB MB"
Write-Host " Version   : $Version"
Write-Host " Backend   : $BackendUrl"
Write-Host " Install   : $InstallType"
Write-Host "============================================" -ForegroundColor Green
Write-Host ""
Write-Host "AVANT D'INSTALLER : désinstalle l'ancienne version :"
Write-Host "  Get-WmiObject -Class Win32_Product -Filter `"Name LIKE '%RTS Caisse%'`" | ForEach-Object { `$_.Uninstall() }" -ForegroundColor Gray
Write-Host ""
Write-Host "Pour installer :"
Write-Host "  msiexec /i `"$($msi.FullName)`" /qn   (silencieux)" -ForegroundColor Gray
Write-Host "  msiexec /i `"$($msi.FullName)`"        (interactif)" -ForegroundColor Gray
Write-Host ""
