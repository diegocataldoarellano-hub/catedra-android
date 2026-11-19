<#
.SYNOPSIS
    Publica Cátedra en GitHub (código + GitHub Pages en /docs + Release con APK)
    y deja todo listo para conectar Render + Google Drive OAuth2 en 2 minutos.
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File E:\Proyectos\catedra-android\scripts\deploy_github_and_render.ps1
#>
param(
    [string]$RepoName = "catedra-android",
    [string]$VersionTag = "v1.0.0",
    [switch]$PrivateRepo
)

$ErrorActionPreference = "Stop"
$ProjectDir = "E:\Proyectos\catedra-android"
$ApkPath = "$ProjectDir\dist\Catedra-v1.0.0.apk"

# 1. Asegurar cachés en Disco E: (Regla obligatoria)
$env:GRADLE_USER_HOME = "E:\Cache_Global_Usuario\gradle"
$env:ANDROID_HOME = "E:\Android\Sdk"
$env:ANDROID_SDK_ROOT = "E:\Android\Sdk"
$env:ANDROID_USER_HOME = "E:\Cache_Global_Usuario\android"

Set-Location $ProjectDir

Write-Host "`n=== 1. Verificando herramientas (git / gh) ===" -ForegroundColor Cyan
git --version | Out-Null
gh --version | Out-Null
$ghUser = (gh api user --jq ".login").Trim()
if (-not $ghUser) { throw "No hay sesión activa en GitHub CLI. Ejecuta: gh auth login" }
Write-Host "Usuario GitHub activo: $ghUser" -ForegroundColor Green

# 2. Compilar APK actualizado si no existe
if (-not (Test-Path $ApkPath)) {
    Write-Host "`n=== 2. Compilando APK de Cátedra ===" -ForegroundColor Cyan
    .\gradlew.bat assembleDebug --no-daemon
    New-Item -ItemType Directory -Force -Path "$ProjectDir\dist" | Out-Null
    Copy-Item "$ProjectDir\app\build\outputs\apk\debug\app-debug.apk" $ApkPath -Force
}

$sha256 = (Get-FileHash $ApkPath -Algorithm SHA256).Hash
$apkMb = [math]::Round((Get-Item $ApkPath).Length / 1MB, 2)
Write-Host "APK verificado: $ApkPath ($apkMb MB)" -ForegroundColor Green
Write-Host "SHA-256: $sha256" -ForegroundColor DarkGray

# 3. Actualizar enlaces del portal con el usuario/repo real
$portalFile = "$ProjectDir\docs\index.html"
$renderYaml = "$ProjectDir\render.yaml"
$bridgeJs = "$ProjectDir\render-bridge\server.js"
foreach ($f in @($portalFile, $renderYaml, $bridgeJs)) {
    if (Test-Path $f) {
        $txt = Get-Content $f -Raw -Encoding UTF8
        $txt = $txt -replace "diegocataldoarellano-hub/catedra-android", "$ghUser/$RepoName"
        Set-Content -Path $f -Value $txt -Encoding UTF8
    }
}

# 4. Inicializar repositorio Git (excluyendo dist/*.apk para mantener el repo liviano; el APK va en Releases!)
Write-Host "`n=== 3. Preparando Git y subiendo a GitHub ===" -ForegroundColor Cyan
$gitignore = Get-Content "$ProjectDir\.gitignore" -Raw
if ($gitignore -notmatch "dist/") {
    Add-Content "$ProjectDir\.gitignore" "`n/dist`n"
}

if (-not (Test-Path "$ProjectDir\.git")) {
    git init -b main
}
git add .
$pending = git status --porcelain
if ($pending) {
    git commit -m "Release $VersionTag - Catedra Android APK + Portal GitHub Pages + Render Google Drive Bridge"
}

$visibility = if ($PrivateRepo) { "--private" } else { "--public" }
$repoExists = $true
try { gh repo view "$ghUser/$RepoName" | Out-Null } catch { $repoExists = $false }

if (-not $repoExists) {
    gh repo create "$ghUser/$RepoName" $visibility --source="." --remote="origin" --push --description "Catedra: lector universal y docente conversacional Android con NVIDIA NIM, Gemini, OpenAlex/arXiv y Google Drive"
} else {
    $remotes = git remote
    if ($remotes -notcontains "origin") {
        git remote add origin "https://github.com/$ghUser/$RepoName.git"
    }
    git push -u origin main
}

# 5. Crear o actualizar Release con el APK adjunto
Write-Host "`n=== 4. Publicando Release $VersionTag con APK ===" -ForegroundColor Cyan
$releaseNotes = @"
## Cátedra $VersionTag (Android 8.0+)

- Lector universal (PDF visual/texto, Word, Excel/CSV, PowerPoint, EPUB, TXT e imágenes/cámara)
- Docente conversacional con citas clickeables, diagnóstico de errores de comprensión y lectura en voz alta
- Motor híbrido: **NVIDIA NIM (Nemotron + NeMo Rerank + VLM)** + **Google Gemini (1M tokens)** + **OpenAI/OpenRouter**
- Notas ultraligeras `.md`/`.txt` + buscador de papers (OpenAlex / arXiv)
- Google Drive híbrido (Android SAF instantáneo + OAuth2 1-toque vía Render)

**Tamaño APK:** $apkMb MB
**SHA-256:** ``$sha256``
"@

$releaseExists = $true
try { gh release view $VersionTag --repo "$ghUser/$RepoName" | Out-Null } catch { $releaseExists = $false }

if ($releaseExists) {
    gh release upload $VersionTag $ApkPath --repo "$ghUser/$RepoName" --clobber
} else {
    gh release create $VersionTag $ApkPath --repo "$ghUser/$RepoName" --title "Cátedra $VersionTag" --notes $releaseNotes
}

# 6. Activar GitHub Pages en /docs (solo disponible gratis en repos públicos)
Write-Host "`n=== 5. Activando GitHub Pages (/docs) ===" -ForegroundColor Cyan
try {
    gh api -X POST "repos/$ghUser/$RepoName/pages" -f "source[branch]=main" -f "source[path]=/docs" | Out-Null
    Write-Host "GitHub Pages activado en /docs." -ForegroundColor Green
} catch {
    Write-Host "Aviso GitHub Pages: ya estaba activo o el repo es privado. Puedes revisarlo en Settings -> Pages." -ForegroundColor Yellow
}

$repoUrl = "https://github.com/$ghUser/$RepoName"
$pagesUrl = "https://$ghUser.github.io/$RepoName/"
$apkUrl = "$repoUrl/releases/latest/download/Catedra-v1.0.0.apk"
$renderDeployUrl = "https://render.com/deploy?repo=$repoUrl"

Write-Host "`n============================================================" -ForegroundColor Green
Write-Host "  CÁTEDRA PUBLICADO CON ÉXITO" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host "1) Portal GitHub Pages : $pagesUrl"
Write-Host "2) Descarga APK directa: $apkUrl"
Write-Host "3) Desplegar en Render : $renderDeployUrl"
Write-Host ""
Write-Host "=== PASOS PARA DEJAR GOOGLE DRIVE OAUTH2 OK EN RENDER (2 min) ===" -ForegroundColor Cyan
Write-Host "A) Abre Render Blueprint: $renderDeployUrl (usa render.yaml automáticamente, plan Free)."
Write-Host "B) En Google Cloud Console -> APIs y servicios -> Credenciales -> Crear ID de cliente OAuth (Aplicación web):"
Write-Host "   - Activa primero 'Google Drive API' en Biblioteca."
Write-Host "   - URI de redirección autorizada:"
Write-Host "       https://<TU-SERVICIO>.onrender.com/auth/google/callback" -ForegroundColor Yellow
Write-Host "   - Agrega tu correo en 'Usuarios de prueba' de la Pantalla de consentimiento OAuth."
Write-Host "C) En Render -> Environment, pega estas 3 variables:"
Write-Host "   - GOOGLE_CLIENT_ID      = <tu Client ID de Google Cloud>"
Write-Host "   - GOOGLE_CLIENT_SECRET  = <tu Client Secret de Google Cloud>"
Write-Host "   - PUBLIC_BASE_URL       = https://<TU-SERVICIO>.onrender.com"
Write-Host "D) En Cátedra (Android) -> Ajustes -> Google Drive -> pega:"
Write-Host "   https://<TU-SERVICIO>.onrender.com" -ForegroundColor Yellow
Write-Host "   Luego ve a Google Drive en el menú y toca 'Conectar Google Drive' (1 toque)."
Write-Host "============================================================`n" -ForegroundColor Green
