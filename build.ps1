# Portable build. Requires JAVA_HOME (JDK 21), Node.js 22 and Python 3.11+.
# Default: build and verify only. -Install: copy into the original Prism layout.
param([switch]$Install)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    & python scripts/build.py
    if ($LASTEXITCODE -ne 0) { throw "Build failed with exit code $LASTEXITCODE" }
    if ($Install) {
        $modsPath = Join-Path (Split-Path $PSScriptRoot -Parent) 'minecraft/mods'
        if (-not (Test-Path -LiteralPath $modsPath -PathType Container)) {
            throw "Instance mods directory not found: $modsPath"
        }
        & ./gradlew.bat installToInstance --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Addon installation failed' }
    }
    Write-Host 'Verified artifacts are in dist/.' -ForegroundColor Green
} finally { Pop-Location }
