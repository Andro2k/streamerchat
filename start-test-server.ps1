# Script de PowerShell para iniciar un servidor local de prueba (Paper/Purpur 1.21.11)
$ErrorActionPreference = "Stop"

$workspace = Get-Location
$testDir = Join-Path $workspace "test-server"
$pluginsDir = Join-Path $testDir "plugins"
$serverJar = Join-Path $testDir "server-1.21.11.jar"

Write-Host "==========================================" -ForegroundColor Yellow
Write-Host " Configurando servidor local 1.21.11... " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Yellow

if (-not (Test-Path $testDir)) {
    New-Item -ItemType Directory -Path $testDir | Out-Null
}
if (-not (Test-Path $pluginsDir)) {
    New-Item -ItemType Directory -Path $pluginsDir | Out-Null
}

# 1. Compilar el plugin
Write-Host "[1/3] Compilando el plugin con Maven..." -ForegroundColor Yellow
mvn clean package -DskipTests=false
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error en la compilacion de Maven." -ForegroundColor Red
    exit 1
}

# 2. Copiar JAR a la carpeta de plugins
$compiledJar = Join-Path $workspace "target\streamerchat-0.1.jar"
if (Test-Path $compiledJar) {
    Copy-Item -Path $compiledJar -Destination (Join-Path $pluginsDir "streamerchat-0.1.jar") -Force
    Write-Host "[2/3] Plugin copiado a test-server/plugins/streamerchat-0.1.jar" -ForegroundColor Green
} else {
    Write-Host "No se encontro el archivo JAR compilado." -ForegroundColor Red
    exit 1
}

# Aceptar EULA automaticamente para pruebas
Set-Content -Path (Join-Path $testDir "eula.txt") -Value "eula=true"

# 3. Descargar Servidor 1.21.11 (Paper/Purpur) si no existe
if (-not (Test-Path $serverJar)) {
    Write-Host "[3/3] Descargando Servidor Paper/Purpur 1.21.11..." -ForegroundColor Yellow
    $serverUrl = "https://api.purpurmc.org/v2/purpur/1.21.11/latest/download"
    Invoke-WebRequest -Uri $serverUrl -OutFile $serverJar
    Write-Host "Servidor 1.21.11 descargado exitosamente." -ForegroundColor Green
} else {
    Write-Host "[3/3] Servidor 1.21.11 ya esta listo." -ForegroundColor Green
}

Write-Host ""
Write-Host "Iniciando servidor de pruebas Minecraft 1.21.11 en localhost:25565..." -ForegroundColor Cyan
Write-Host "Presiona Ctrl+C en cualquier momento para detener el servidor." -ForegroundColor Gray
Write-Host ""

Set-Location $testDir
java -Xms1G -Xmx2G -jar server-1.21.11.jar nogui
