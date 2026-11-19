# Cátedra — Lector y Docente Conversacional para Android

Aplicación Android nativa (**Kotlin + Jetpack Compose**) con diseño minimalista inspirado en **Telegram** para estudiar cualquier documento (visual o de texto) conversando de forma fluida con un **Docente IA** que cita páginas exactas, detecta errores de comprensión, lee textos completos en voz alta, busca papers académicos y guarda todo lo comprendido en archivos ultraligeros `.md` / `.txt`.

---

## Características Principales

### 1. Apertura Universal de Documentos (Visuales y de Texto)
- **PDF (`.pdf`)**: extracción exacta página por página (`[Pág. N]`), lector visual nativo (`PdfRenderer` con pellizco/zoom) y detección de páginas escaneadas/gráficos para análisis visual multimodal.
- **Word y texto enriquecido (`.docx`, `.doc`, `.odt`, `.rtf`)**: títulos, párrafos, tablas y notas al pie.
- **Excel y hojas de cálculo (`.xlsx`, `.xls`, `.ods`, `.csv`, `.tsv`)**: extracción de cada hoja y fila en formato tabular citable (`[Hoja: Nombre]`).
- **Presentaciones (`.pptx`, `.odp`)**: texto de cada diapositiva + notas del expositor (`[Diap. N]`).
- **Libros electrónicos y texto (`.epub`, `.txt`, `.md`, `.html`, `.tex`, `.json`, `.xml`)**.
- **Documentos visuales / fotos (`.jpg`, `.png`, `.webp` y cámara)**: fotografías de apuntes manuscritos, pizarras, diagramas, fórmulas y tablas.
- Integración nativa con **"Abrir con Cátedra"** y **"Compartir en Cátedra"** desde WhatsApp, Telegram, Google Drive o el gestor de archivos del celular.

### 2. Docente Conversacional con Citas y Detección de Errores
- **Diálogo natural y pedagógico** (modos: *Docente Socrático*, *Cátedra Directa* y *Tutor de Examen*).
- **Citas exactas clickeables**: cada `[Pág. X]`, `[Diap. X]`, `[Hoja: X]` o `[Cap. X]` abre el lector justo en ese pasaje.
- **Diagnóstico de comprensión**: cuando explicas un tema con tus palabras, el docente valida lo correcto, detecta confusiones específicas contrastándolas contra el documento y te guía con contraejemplos.
- **Acciones rápidas estilo Telegram**: `/resumen`, `/preguntas`, `/evaluar`, `/critica`, `/conceptos` y síntesis automática de aprendizajes.

### 3. Motor Híbrido IA: NVIDIA NIM + Skills NeMo + Google Gemini + Multi-proveedor
- **NVIDIA NIM (`build.nvidia.com`, API Key `nvapi-...`)**:
  - Modelos docentes y de razonamiento: `nvidia/llama-3.3-nemotron-super-49b-v1.5`, `nvidia/llama-3.1-nemotron-70b-instruct`, `nvidia/nemotron-3.5-super-vl-120b-a12b`, `meta/llama-3.3-70b-instruct`, `deepseek-ai/deepseek-r1` + **descubrimiento en vivo del catálogo NVIDIA**.
  - **Skill NVIDIA NeMo Retriever Rerank** (`nvidia/llama-3.2-nv-rerankqa-1b-v2`): reordena semánticamente las páginas más relevantes de documentos extensos para lograr citas quirúrgicas (funciona tanto con NVIDIA como potenciando a Gemini/OpenAI).
  - **Skill Visión Multimodal NVIDIA VLM**: interpreta gráficos, ecuaciones, tablas y apuntes escaneados.
  - Visualización colapsable del razonamiento interno (`<think>`).
- **Google Gemini API**: contexto de hasta 1 millón de tokens para libros completos + visión nativa (`gemini-2.5-flash`, `gemini-2.5-pro`).
- **OpenAI / OpenRouter / Servidor propio compatible**.

### 4. Lectura Completa en Voz Alta, Notas de Voz y Modo Diálogo Oral
- **Lector en voz alta (TTS)** con mini-reproductor fijo estilo Telegram (pausa, saltar sección, velocidad `0.85x`–`1.85x`).
- **Notas de voz sin gastar almacenamiento**: el dictado se transcribe al instante en texto limpio (0 KB en audios almacenados).
- **Modo Diálogo Oral (manos libres)**: conversas hablando y el docente responde en voz alta.

### 5. Almacenamiento Ultraligero (`.md` / `.txt` + Índices `.gz`)
- Todo lo que comprendiste, los errores corregidos, tu análisis crítico y citas se guardan en un archivo plano `.md` / `.txt` (~2 KB a 15 KB por libro completo).
- Botón **"Sintetizar chat"** para destilar únicamente el aprendizaje real y poder compactar historiales antiguos sin perder tus notas.
- Exportación instantánea a `.txt` / `.md`, guardado en carpeta local (SAF) o subida a **Google Drive (`Catedra_Notas`)**.

### 6. Google Drive Híbrido + Buscador de Papers Académicos
- **Google Drive sin fricción**: acceso inmediato a tus archivos locales y de Google Drive mediante el selector nativo Android SAF, más explorador opcional por Google Drive REST API (exporta Google Docs/Sheets/Slides automáticamente).
- **Buscador de Papers gratuito**: consulta **OpenAlex** (250M+ trabajos) y **arXiv**, sugiere búsquedas relacionadas con tu lectura actual, descarga PDFs de acceso abierto directo a un nuevo chat docente y permite comparar otro paper con tu documento actual.

---

## Compilación Local (Disco `E:`)

```powershell
$env:GRADLE_USER_HOME = "E:\Cache_Global_Usuario\gradle"
$env:ANDROID_HOME = "E:\Android\Sdk"
$env:ANDROID_USER_HOME = "E:\Cache_Global_Usuario\android"
cd E:\Proyectos\catedra-android
.\gradlew.bat assembleDebug
```

El APK generado queda en `app/build/outputs/apk/debug/app-debug.apk`.
