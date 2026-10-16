# =====================================================================
# Starts the API locally, loading credentials from .env first.
#
#   1. Copy .env.example to .env and fill in your Supabase values
#   2. Right-click this file -> Run with PowerShell
#   3. Or from this folder:  .\run-local.ps1
#
# The app refuses to start without DB_URL, so a missing .env fails
# loudly rather than silently falling back to localhost.
# =====================================================================

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not (Test-Path ".env")) {
    Write-Host ""
    Write-Host "  No .env file found." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  Copy .env.example to .env, then fill in your Supabase values:"
    Write-Host "      Copy-Item .env.example .env"
    Write-Host ""
    Write-Host "  Find them at: Supabase -> Project Settings -> Database -> Connection string"
    Write-Host "                Use the SESSION POOLER URI."
    Write-Host ""
    exit 1
}

# Load KEY=VALUE pairs into the current process environment.
Get-Content ".env" | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#") -and $line -match "^([^=]+)=(.*)$") {
        $key   = $matches[1].Trim()
        $value = $matches[2].Trim()
        [Environment]::SetEnvironmentVariable($key, $value, "Process")
    }
}

$javaHome = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
if (Test-Path $javaHome) {
    $env:JAVA_HOME = $javaHome
    $env:Path = "$javaHome\bin;$env:Path"
}

Write-Host ""
Write-Host "  Starting CampusPilot API on http://localhost:8080" -ForegroundColor Green
Write-Host "  Health check:  http://localhost:8080/actuator/health"
Write-Host "  Stop with Ctrl+C"
Write-Host ""

.\mvnw.cmd spring-boot:run