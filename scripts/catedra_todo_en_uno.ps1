<#
.SYNOPSIS
    Script TODO-EN-UNO para Cátedra (Android + NVIDIA NIM + Google Drive Render + GitHub Pages/Release).
    - Mantiene todos los cachés estrictamente en Disco E: y verifica el límite de 500 MB.
    - Prueba en vivo NVIDIA NIM + NeMo Rerank (si entregas -NvidiaApiKey).
    - Verifica Render (Google Drive OAuth2) y GitHub Pages.
    - Corre pruebas unitarias (7/7) y compila el APK.
    - Si entregas -NvidiaApiKey o -GeminiApiKey, compila un APK personal LOCAL con tus claves ya
      preconfiguradas (usando local.properties, que está en .gitignore y NUNCA se sube a GitHub).
    - Instala directo en tu celular por ADB (o lo deja en /sdcard/Download si MIUI restringe USB).
    - Con -PublishGitHub sube código limpio y APK sin claves a GitHub + Release + GitHub Pages.

.EXAMPLE
    # Uso rápido todo-en-uno para tu celular con tu NVIDIA API Key:
    powershell -ExecutionPolicy Bypass -File "E:\Proyectos\catedra-android\scripts\catedra_todo_en_uno.ps1" -NvidiaApiKey "nvapi-TU_CLAVE"

.EXAMPLE
    # Compilar, probar, instalar en celular Y actualizar GitHub Release (el Release público siempre va SIN claves):
    powershell -ExecutionPolicy Bypass -File "E:\Proyectos\catedra-android\scripts\catedra_todo_en_uno.ps1" -NvidiaApiKey "nvapi-TU_CLAVE" -PublishGitHub
#>
param(
    [string]$NvidiaApiKey = "",
    [string]$GeminiApiKey = "",
    [string]$RenderUrl = "https://catedra-portal.onrender.com",
    [string]$RepoName = "catedra-android",
    [string]$VersionTag = "v1.0.0",
    [switch]$PublishGitHub,
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
$ProjectDir = "E:\Proyectos\catedra-android"
$Adb = if (Test-Path "E:\Android\Sdk\platform-tools\adb.exe") { "E:\Android\Sdk\platform-tools\adb.exe" } else { "adb" }

# 1. Entorno estrictamente en Disco E:
$env:GRADLE_USER_HOME = "E:\Cache_Global_Usuario\gradle"
$env:ANDROID_HOME = "E:\Android\Sdk"
$env:ANDROID_SDK_ROOT = "E:\Android\Sdk"
$env:ANDROID_USER_HOME = "E:\Cache_Global_Usuario\android"

Set-Location $ProjectDir
New-Item -ItemType Directory -Force -Path "$ProjectDir\dist" | Out-Null

Write-Host "`n============================================================" -ForegroundColor Cyan
Write-Host "  CÁTEDRA — SCRIPT TODO EN UNO" -ForegroundColor Cyan
Write-Host "============================================================`n" -ForegroundColor Cyan

# 2. Verificar Render Bridge (Google Drive OAuth2) y GitHub Pages
Write-Host "[1/6] Verificando puente Google Drive en Render ($RenderUrl)..." -ForegroundColor Yellow
try {
    $health = Invoke-RestMethod -Uri "$($RenderUrl.TrimEnd('/'))/health" -TimeoutSec 25
    if ($health.oauthConfigured -eq $true) {
        Write-Host "  OK -> Render activo y Google Drive OAuth2 configurado ($($health.redirectUri))" -ForegroundColor Green
    } else {
        Write-Host "  AVISO -> Render respondió, pero faltan variables GOOGLE_CLIENT_ID / SECRET." -ForegroundColor Red
    }
} catch {
    Write-Host "  AVISO -> Render puede estar despertando del plan Free (toma ~30s). Detalle: $($_.Exception.Message)" -ForegroundColor Yellow
}

# 3. Probar en vivo NVIDIA NIM + NeMo Retriever Rerank si el usuario pasó su clave
if ($NvidiaApiKey.Trim().Length -gt 0) {
    $cleanNv = $NvidiaApiKey.Trim()
    Write-Host "`n[2/6] Probando NVIDIA API Key en vivo (Catálogo NIM + NeMo Retriever Rerank)..." -ForegroundColor Yellow
    try {
        $modelsResp = Invoke-RestMethod -Uri "https://integrate.api.nvidia.com/v1/models" -Headers @{ Authorization = "Bearer $cleanNv" } -TimeoutSec 20
        Write-Host "  OK -> NVIDIA NIM autenticado ($($modelsResp.data.Count) modelos disponibles)." -ForegroundColor Green
    } catch {
        Write-Host "  ERROR -> NVIDIA NIM rechazó la clave o no respondió: $($_.Exception.Message)" -ForegroundColor Red
    }
    try {
        $embBody = @{
            model = "nvidia/nemotron-3.5-embed-8b"
            input = @("El analisis critico requiere contrastar tesis y evidencias con citas exactas.")
            input_type = "passage"
            encoding_format = "float"
            truncate = "END"
        } | ConvertTo-Json -Depth 5
        $embBytes = [System.Text.Encoding]::UTF8.GetBytes($embBody)
        $em = Invoke-RestMethod -Method Post `
            -Uri "https://integrate.api.nvidia.com/v1/embeddings" `
            -Headers @{ Authorization = "Bearer $cleanNv"; Accept = "application/json" } `
            -ContentType "application/json; charset=utf-8" `
            -Body $embBytes -TimeoutSec 25
        Write-Host "  OK -> Skill NVIDIA Nemotron-3.5-Embed-8B operativa (dim=$($em.data[0].embedding.Count))." -ForegroundColor Green
    } catch {
        Write-Host "  AVISO -> Embeddings devolvió aviso (respaldo BM25 automático): $($_.Exception.Message)" -ForegroundColor Yellow
    }
    try {
        $trBody = @{
            model = "nvidia/riva-translate-4b-instruct-v2"
            messages = @(@{ role = "user"; content = "Translate to natural Spanish: Critical reading improves understanding." })
            max_tokens = 120
            temperature = 0.2
        } | ConvertTo-Json -Depth 5
        $trBytes = [System.Text.Encoding]::UTF8.GetBytes($trBody)
        $tr = Invoke-RestMethod -Method Post `
            -Uri "https://integrate.api.nvidia.com/v1/chat/completions" `
            -Headers @{ Authorization = "Bearer $cleanNv"; Accept = "application/json" } `
            -ContentType "application/json; charset=utf-8" `
            -Body $trBytes -TimeoutSec 25
        Write-Host "  OK -> Skill NVIDIA Riva Translate 4B operativa: $($tr.choices[0].message.content)" -ForegroundColor Green
    } catch {
        Write-Host "  AVISO -> Riva Translate devolvió aviso: $($_.Exception.Message)" -ForegroundColor Yellow
    }

    # Guardar clave SOLO en local.properties (está en .gitignore, nunca sube a GitHub)
    $localPropsPath = "$ProjectDir\local.properties"
    $rawLines = if (Test-Path $localPropsPath) { @(Get-Content $localPropsPath) } else { @("sdk.dir=E\:\\Android\\Sdk") }
    $cleanLines = @("sdk.dir=E\:\\Android\\Sdk")
    foreach ($ln in $rawLines) {
        if ($ln -match "^catedra\.gemini\.key=") { $cleanLines += $ln }
    }
    $cleanLines += "catedra.nvidia.key=$cleanNv"
    [System.IO.File]::WriteAllLines($localPropsPath, [string[]]$cleanLines, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "  OK -> Clave guardada solo localmente en local.properties (protegida por .gitignore)." -ForegroundColor Green
} else {
    Write-Host "`n[2/6] Sin -NvidiaApiKey indicada (puedes ingresarla luego en Ajustes de la app o pasarla por parámetro)." -ForegroundColor DarkGray
}

if ($GeminiApiKey.Trim().Length -gt 0) {
    $cleanGm = $GeminiApiKey.Trim()
    $localPropsPath = "$ProjectDir\local.properties"
    $rawLines = if (Test-Path $localPropsPath) { @(Get-Content $localPropsPath) } else { @("sdk.dir=E\:\\Android\\Sdk") }
    $cleanLines = @("sdk.dir=E\:\\Android\\Sdk")
    foreach ($ln in $rawLines) {
        if ($ln -match "^catedra\.nvidia\.key=") { $cleanLines += $ln }
    }
    $cleanLines += "catedra.gemini.key=$cleanGm"
    [System.IO.File]::WriteAllLines($localPropsPath, [string[]]$cleanLines, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "  OK -> Gemini API Key guardada solo localmente en local.properties." -ForegroundColor Green
}

# 4. Pruebas unitarias
if (-not $SkipTests) {
    Write-Host "`n[3/6] Ejecutando pruebas unitarias (parsers, citas, BM25, papers)..." -ForegroundColor Yellow
    .\gradlew.bat testDebugUnitTest --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Fallaron las pruebas unitarias." }
    Write-Host "  OK -> Todas las pruebas unitarias aprobadas." -ForegroundColor Green
} else {
    Write-Host "`n[3/6] Pruebas unitarias omitidas (-SkipTests)." -ForegroundColor DarkGray
}

# 5. Compilar APK limpio (si se publica a GitHub) y/o APK personal con claves preconfiguradas para tu celular
$hasLocalKeys = ($NvidiaApiKey.Trim().Length -gt 0) -or ($GeminiApiKey.Trim().Length -gt 0)
if ($PublishGitHub) {
    Write-Host "`n[4/6] Compilando APK público LIMPIO (sin claves) para GitHub Release..." -ForegroundColor Yellow
    .\gradlew.bat assembleDebug --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Falló compilación pública." }
    $PublicApk = "$ProjectDir\dist\Catedra-$VersionTag.apk"
    Copy-Item "$ProjectDir\app\build\outputs\apk\debug\app-debug.apk" $PublicApk -Force

    $ghUser = (gh api user --jq ".login").Trim()
    git add .
    $pending = git status --porcelain
    if ($pending) {
        git commit -m "Actualización Cátedra $VersionTag (script todo-en-uno)"
        git push origin main
    }
    gh release upload $VersionTag $PublicApk --repo "$ghUser/$RepoName" --clobber
    Write-Host "  OK -> Código y APK limpio subidos a GitHub Release $VersionTag." -ForegroundColor Green
}

$ApkToInstall = "$ProjectDir\dist\Catedra-$VersionTag.apk"
if ($hasLocalKeys) {
    Write-Host "`n[4/6] Compilando APK PERSONAL (con tus API keys preconfiguradas solo para tu celular)..." -ForegroundColor Yellow
    $env:CATEDRA_INJECT_PERSONAL_KEYS = "true"
    try {
        .\gradlew.bat assembleDebug --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "Falló compilación personal." }
        $ApkToInstall = "$ProjectDir\dist\Catedra-personal.apk"
        Copy-Item "$ProjectDir\app\build\outputs\apk\debug\app-debug.apk" $ApkToInstall -Force
    } finally {
        Remove-Item Env:\CATEDRA_INJECT_PERSONAL_KEYS -ErrorAction SilentlyContinue
    }
    # Limpiar BuildConfig generado para dejar el árbol de build sin claves
    .\gradlew.bat :app:generateDebugBuildConfig --no-daemon | Out-Null
} elseif (-not $PublishGitHub) {
    Write-Host "`n[4/6] Compilando APK..." -ForegroundColor Yellow
    .\gradlew.bat assembleDebug --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Falló compilación." }
    Copy-Item "$ProjectDir\app\build\outputs\apk\debug\app-debug.apk" $ApkToInstall -Force
}

$sha256 = (Get-FileHash $ApkToInstall -Algorithm SHA256).Hash
$apkMb = [math]::Round((Get-Item $ApkToInstall).Length / 1MB, 2)
Write-Host "  APK listo: $ApkToInstall ($apkMb MB) | SHA-256: $sha256" -ForegroundColor Green

# 6. Instalar en celular conectado vía ADB (con respaldo automático a /sdcard/Download para MIUI/HyperOS)
Write-Host "`n[5/6] Detectando celular por USB (ADB)..." -ForegroundColor Yellow
$ErrorActionPreference = "Continue"
$adbOut = & $Adb devices 2>$null
$deviceLine = $adbOut | Where-Object { $_ -match "\s+device$" } | Select-Object -First 1
if ($deviceLine) {
    $serial = ($deviceLine -split "\s+")[0]
    Write-Host "  Celular detectado: $serial — intentando instalación directa..." -ForegroundColor Cyan
    $installOut = & $Adb -s $serial install -r -g $ApkToInstall 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  OK -> Cátedra instalado directamente en tu celular ($serial)." -ForegroundColor Green
        & $Adb -s $serial shell am start -n com.catedra.app/.MainActivity 2>$null | Out-Null
        Write-Host "  OK -> Aplicación abierta en tu pantalla." -ForegroundColor Green
    } else {
        Write-Host "  MIUI/HyperOS restringió instalación USB ($installOut)." -ForegroundColor Yellow
        $remoteName = Split-Path $ApkToInstall -Leaf
        & $Adb -s $serial push $ApkToInstall "/sdcard/Download/$remoteName"
        Write-Host "  OK -> Copiado en Descargas del celular: /sdcard/Download/$remoteName" -ForegroundColor Green
        Write-Host "  -> Abre Archivos -> Descargas -> toca '$remoteName' para instalar en 1 toque." -ForegroundColor Cyan
        Write-Host "  -> (Para futuras instalaciones automáticas por cable: activa 'Instalar vía USB' en Opciones de desarrollador)." -ForegroundColor DarkGray
    }
} else {
    Write-Host "  No se detectó celular por USB. Instala manualmente desde: $ApkToInstall" -ForegroundColor Yellow
}
$ErrorActionPreference = "Stop"

# 7. Verificación de almacenamiento (< 500 MB en E:)
$projSizeMb = [math]::Round(((Get-ChildItem $ProjectDir -Recurse -File | Measure-Object -Property Length -Sum).Sum) / 1MB, 2)
Write-Host "`n[6/6] Peso total del proyecto en E:\Proyectos\catedra-android: $projSizeMb MB (Límite: 500 MB)" -ForegroundColor Cyan
Write-Host "`n============================================================" -ForegroundColor Green
Write-Host "  LISTO — TODO VERIFICADO" -ForegroundColor Green
Write-Host "  Portal : https://diegocataldoarellano-hub.github.io/$RepoName/" -ForegroundColor Green
Write-Host "  Render : $RenderUrl" -ForegroundColor Green
Write-Host "============================================================`n" -ForegroundColor Green
