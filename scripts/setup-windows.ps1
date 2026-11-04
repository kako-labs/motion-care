param(
    [string]$SdkPath = "$env:LOCALAPPDATA\Android\Sdk",
    [switch]$IncludeEmulatorImage
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot

Write-Host "[1/5] Checking JDK 17"
$Java = Get-Command java -ErrorAction SilentlyContinue
if (-not $Java) {
    throw "Java was not found. Install Microsoft OpenJDK 17 and reopen PowerShell."
}
$JavaVersion = (& java -version 2>&1 | Out-String)
if ($JavaVersion -notmatch 'version "17[.\"]') {
    throw "JDK 17 is required. Current output: $JavaVersion"
}

Write-Host "[2/5] Checking Python"
$PythonLauncher = Get-Command py -ErrorAction SilentlyContinue
if (-not $PythonLauncher) {
    throw "Python 3.9 or later is required for scripts/dev.py."
}

Write-Host "[3/5] Checking Android SDK"
if (-not (Test-Path -LiteralPath $SdkPath -PathType Container)) {
    throw "Android SDK was not found at $SdkPath. Install it with Android Studio or pass -SdkPath."
}
$SdkManager = Join-Path $SdkPath "cmdline-tools\latest\bin\sdkmanager.bat"
if (-not (Test-Path -LiteralPath $SdkManager -PathType Leaf)) {
    throw "Android SDK Command-line Tools (latest) is missing: $SdkManager"
}

Write-Host "[4/5] Installing project SDK packages"
$Packages = @(
    "platforms;android-35",
    "build-tools;35.0.0",
    "platform-tools",
    "emulator"
)
if ($IncludeEmulatorImage) {
    $Packages += "system-images;android-35;default;x86_64"
}
& $SdkManager $Packages
if ($LASTEXITCODE -ne 0) {
    throw "sdkmanager failed. Review licenses and the network connection."
}

Write-Host "[5/5] Writing local.properties and running doctor"
$EscapedSdk = $SdkPath.Replace("\", "\\").Replace(":", "\:")
$LocalProperties = Join-Path $ProjectRoot "local.properties"
[System.IO.File]::WriteAllText($LocalProperties, "sdk.dir=$EscapedSdk`r`n", [System.Text.UTF8Encoding]::new($false))

Push-Location $ProjectRoot
try {
    & py -3 scripts/dev.py doctor
    if ($LASTEXITCODE -ne 0) { throw "Project doctor failed." }
} finally {
    Pop-Location
}

Write-Host "Ready. Open the project in Android Studio and set Gradle JDK to 17."
