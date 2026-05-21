# Plant Guard AI — Plant Leaf Disease Detection (Android)

Android application for **on-device plant leaf disease classification** combined with an **optional AI assistant** for follow-up questions. The project suits undergraduate thesis work in mobile computing, applied machine learning, or precision agriculture.

---

## Abstract (thesis-ready summary)

Smallholder and home growers often lack quick access to plant pathologists. This work presents **Plant Guard AI**, a native Android app that captures or imports leaf images, verifies that the subject is likely a leaf, classifies **15 disease or health states** for pepper, potato, and tomato (labels aligned with the widely used PlantVillage-style taxonomy), and shows **rule-based treatment hints**. Classification runs **locally** using **ONNX Runtime** and two neural models (a leaf gate and a DenseNet-style disease classifier). An integrated **chatbot** uses the **Groq** API (OpenAI-compatible HTTP) so users can ask contextual questions when a network is available; chat and scan history can be persisted per user. The stack is **Kotlin**, **CameraX**, **View Binding**, and **shared preferences–backed** user and history managers.

---

## Problem statement

- Manual inspection of leaf symptoms is subjective and slow.
- Cloud-only diagnosis raises latency, cost, and privacy concerns.
- Farmers need not only a label but **actionable guidance** and a way to **ask follow-up questions**.

---

## Objectives

1. Deliver **real-time, on-device** inference for leaf disease classification on commodity phones.
2. Use a **two-stage pipeline** (leaf vs. non-leaf, then disease class) to reduce false positives on arbitrary images.
3. Map predictions to **short treatment recommendations** for thesis evaluation and user utility.
4. Provide **user accounts**, **scan history**, **chat history**, and an **admin** view for registered users (demo / academic scope).
5. Integrate a **large language model** via Groq for conversational support when online.

---

## Scope and limitations

- **Classes**: 15 labels covering pepper, potato, and tomato (see `app/src/main/assets/labels.txt`).
- **On-device models** must be present under `app/src/main/assets/` as `leaf_detector2.onnx` and `densenet_disease_detector.onnx` (bundled with your project; large files may be omitted from some Git hosts—document this in your thesis if you redistribute).
- **Chatbot** requires **internet** and a valid **Groq API key**; it is not a replacement for professional diagnosis.
- **User management** is local (SharedPreferences), suitable for prototypes—not production multi-tenant security.

---

## System architecture

### High-level flow

1. **Home** → Login or Sign up → **Main** hub.
2. **Camera** (CameraX) captures or gallery picks an image → image path passed to **Detection**.
3. **Detection** loads ONNX sessions, runs **leaf model**, then **disease model**, softmax over 15 classes, shows label, confidence, and mapped treatment text; can open **Chatbot** with the detected label.
4. **Chatbot** sends prompts (and optional vision payload per your implementation) to Groq over HTTPS (OkHttp, coroutines).
5. **Settings**, **Chat history**, **Scan history**, and **Admin** support evaluation and demos.

### Machine learning pipeline (on-device)

| Stage | Asset | Role |
|--------|--------|------|
| 1 | `leaf_detector2.onnx` | Filters whether the crop is likely a leaf before full classification. |
| 2 | `densenet_disease_detector.onnx` | Multi-class disease / healthy prediction. |
| Labels | `labels.txt` | One class name per line; order must match model output indices. |

Input resolution defaults to **224×224** RGB; the app attempts to infer **NCHW vs. NHWC** and tensor shapes from the model where possible (`DetectionActivity.kt`).

### Class labels (15)

Pepper bell (bacterial spot, healthy); potato (early blight, late blight, healthy); tomato (bacterial spot, early blight, late blight, leaf mold, Septoria leaf spot, spider mites, target spot, yellow leaf curl virus, mosaic virus, healthy)—exact strings in `labels.txt`.

### Dataset reference (thesis citation)

The label set follows the **PlantVillage** style taxonomy. Common citation for the dataset:

- Hughes, D., & Salathé, M. (2015). *An open access repository of images on plant health to enable the development of mobile disease diagnostics*. arXiv:1511.08060.

Public mirrors include [Kaggle PlantVillage](https://www.kaggle.com/datasets/emmarex/plantdisease).

---

## Tech stack

| Layer | Technology |
|--------|------------|
| Language | Kotlin |
| UI | Android SDK 34, Material Components, View Binding |
| Camera | CameraX |
| On-device ML | ONNX Runtime Android (`onnxruntime-android`) |
| Optional TFLite libs | Present in Gradle for compatibility / future use |
| Networking | OkHttp, Gson |
| Async | Kotlin Coroutines |
| LLM API | Groq (`BuildConfig.AI_*` from `local.properties`) |

---

## Project structure (key paths)

```
app/src/main/
├── AndroidManifest.xml      # Activities, permissions
├── assets/
│   ├── labels.txt           # Class names (order = output index)
│   ├── leaf_detector2.onnx  # Leaf gate model
│   └── densenet_disease_detector.onnx
├── java/com/example/plant_disease_detection/
│   ├── HomeActivity.kt      # Entry / auth entry
│   ├── LoginActivity.kt, SignupActivity.kt
│   ├── MainActivity.kt      # Post-login hub
│   ├── CameraActivity.kt
│   ├── DetectionActivity.kt # ONNX inference + treatments
│   ├── ChatbotActivity.kt   # Groq chat
│   ├── ChatHistoryActivity.kt, SettingsActivity.kt
│   ├── AdminActivity.kt     # User list admin UI
│   ├── PlantGuardApplication.kt
│   └── utils/               # UserManager, ScanHistoryManager, ChatHistoryManager, etc.
└── res/                     # Layouts, themes, drawables
```

Root Gradle project name: **Plant Guard AI** (`settings.gradle`).

---

## Build and run

### Prerequisites

- **Android Studio** (compatible with AGP 8.2.x) or command-line Gradle.
- **JDK 11**.
- **Android SDK** with API **34**; device or emulator with **API 24+**.

### Configure secrets (do not commit)

Create or edit **`local.properties`** in the project root (this file is normally gitignored). Android Gradle Plugin also stores `sdk.dir` here.

Example (replace placeholders):

```properties
sdk.dir=/path/to/Android/Sdk
GROQ_API_KEY=your_groq_api_key_here
GROQ_MODEL=llama-3.1-8b-instant
GROQ_VISION_MODEL=llama-3.2-11b-vision-preview
MODEL_API_BASE_URL=https://your-render-service.onrender.com
```

Keys are injected as `BuildConfig` fields in `app/build.gradle`. **Never commit real API keys** or publish them in your thesis repository—use placeholders and describe configuration in prose.

`MODEL_API_BASE_URL` is the deployed FastAPI model service URL. The Android detection screen sends the selected leaf image to `${MODEL_API_BASE_URL}/predict` and displays the returned disease, confidence, and treatment.

### Commands

- **Windows**: `gradlew.bat assembleDebug`
- **macOS/Linux**: `./gradlew assembleDebug`

Install the debug APK from `app/build/outputs/apk/debug/` or use **Run** in Android Studio.

### Permissions

The app requests **camera**, **internet**, and **read image** permissions as declared in `AndroidManifest.xml`.

---

## Suggested thesis document outline

You can map chapters to this project as follows:

1. **Introduction** — Agriculture context, motivation, contributions of the mobile app.
2. **Literature review** — CNNs for plant disease, mobile ML (TFLite / ONNX), PlantVillage and related datasets.
3. **Requirements** — Functional (capture, classify, history, chat) and non-functional (latency, offline classification).
4. **Methodology** — Data preprocessing, model training / export to ONNX (describe your training work if you retrained), two-stage inference design.
5. **System design** — Activity diagram, module diagram, sequence diagram (camera → detect → optional chat).
6. **Implementation** — Kotlin, CameraX, ONNX Runtime integration, Groq API.
7. **Testing and results** — Accuracy on a hold-out set, confusion matrix, on-device latency, qualitative screenshots.
8. **Conclusion and future work** — More crops/classes, federated learning, on-device LLM, backend auth.

---

## Supplementary materials

- Prior thesis PDF linked in the original project README: [VI_sem_thesis.pdf](https://github.com/user-attachments/files/20128194/VI_sem_thesis.pdf) (verify link if you mirror the repo).
- Flow diagram (from original README): ![Flow Chart](https://github.com/user-attachments/assets/1a274edb-3b48-4758-bb25-f140e9707982)

---

## License and academic use

Clarify the license of any third-party models and datasets in your thesis acknowledgments. If this repository is a fork, retain upstream license files and citations.

---

## Original short description

**Plant Guard AI** is an Android app that uses **deep learning (ONNX)** on leaf images to detect diseases on pepper, potato, and tomato, suggests brief treatments, and offers an **online AI assistant** for follow-up questions—supporting thesis topics in **mobile AI** and **smart agriculture**.
