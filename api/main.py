from __future__ import annotations

import os
from pathlib import Path
from threading import Lock
from typing import Any

import numpy as np
import onnxruntime as ort
import requests
from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from PIL import Image


BASE_DIR = Path(__file__).resolve().parent
MODEL_DIR = Path(os.getenv("MODEL_DIR", BASE_DIR / "models"))
LEAF_MODEL_FILE = MODEL_DIR / "leaf_detector2.onnx"
DISEASE_MODEL_FILE = MODEL_DIR / "densenet_disease_detector.onnx"

LEAF_MODEL_URL = os.getenv(
    "LEAF_MODEL_URL",
    "https://huggingface.co/Bhagyawanth1729/App/resolve/main/leaf_detector2.onnx",
)
DISEASE_MODEL_URL = os.getenv(
    "DISEASE_MODEL_URL",
    "https://huggingface.co/Bhagyawanth1729/App/resolve/main/densenet_disease_detector.onnx",
)

LABELS = [
    "Pepper_bell_Bacterial_spot",
    "Pepper_bell_healthy",
    "Potato___Early_blight",
    "Potato___Late_blight",
    "Potato___healthy",
    "Tomato_Bacterial_spot",
    "Tomato_Early_blight",
    "Tomato_Late_blight",
    "Tomato_Leaf_Mold",
    "Tomato_Septoria_leaf_spot",
    "Tomato_Spider_mites_Two_spotted_spider_mite",
    "Tomato__Target_Spot",
    "Tomato__Tomato_YellowLeaf_Curl_Virus",
    "Tomato__Tomato_mosaic_virus",
    "Tomato_healthy",
]

DISEASE_TREATMENTS = {
    "Pepper_bell_Bacterial_spot": "Remove infected leaves, avoid overhead watering, and apply a copper-based bactericide.",
    "Pepper_bell_healthy": "Your pepper plant looks healthy. Maintain proper watering, fertilization, and regular pest checks.",
    "Potato___Early_blight": "Remove infected leaves, avoid water on foliage, rotate crops, and use a fungicide such as chlorothalonil or mancozeb if needed.",
    "Potato___Late_blight": "Remove and destroy infected plants, avoid waterlogged conditions, and apply recommended fungicides promptly.",
    "Potato___healthy": "Potato plant appears healthy. Continue regular care and monitor for spots on leaves or stems.",
    "Tomato_Bacterial_spot": "Remove infected leaves, avoid working in wet plants, and apply copper-based bactericides. Use disease-free seeds.",
    "Tomato_Early_blight": "Remove lower infected leaves, stake plants for better airflow, rotate crops, and use fungicide when necessary.",
    "Tomato_Late_blight": "Immediately remove and destroy infected plants, avoid overhead irrigation, and follow local guidance on fungicide sprays.",
    "Tomato_Leaf_Mold": "Improve ventilation, avoid high humidity, remove infected leaves, and use suitable fungicides if the disease spreads.",
    "Tomato_Septoria_leaf_spot": "Remove heavily infected leaves, avoid overhead watering, mulch soil, and apply fungicide where recommended.",
    "Tomato_Spider_mites_Two_spotted_spider_mite": "Spray with water to knock off mites, use neem oil or insecticidal soap, and avoid plant stress.",
    "Tomato__Target_Spot": "Remove infected leaves, maintain good spacing and airflow, and apply appropriate fungicides if disease is severe.",
    "Tomato__Tomato_YellowLeaf_Curl_Virus": "Remove infected plants, control whiteflies, and use resistant varieties where possible.",
    "Tomato__Tomato_mosaic_virus": "Remove infected plants, disinfect tools, and avoid handling plants after smoking or handling tobacco.",
    "Tomato_healthy": "Tomato plant appears healthy. Maintain balanced nutrition, proper watering, and regular monitoring.",
}

NO_LEAF_THRESHOLD = float(os.getenv("NO_LEAF_THRESHOLD", "0.7"))

app = FastAPI(title="Plant Guard AI Model API", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=os.getenv("CORS_ORIGINS", "*").split(","),
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

_session_lock = Lock()
_leaf_session: ort.InferenceSession | None = None
_disease_session: ort.InferenceSession | None = None


def download_model(url: str, destination: Path) -> None:
    if destination.exists() and destination.stat().st_size > 0:
        return

    destination.parent.mkdir(parents=True, exist_ok=True)
    tmp_path = destination.with_suffix(destination.suffix + ".tmp")

    with requests.get(url, stream=True, timeout=(30, 900)) as response:
        response.raise_for_status()
        with tmp_path.open("wb") as output:
            for chunk in response.iter_content(chunk_size=1024 * 1024):
                if chunk:
                    output.write(chunk)

    if tmp_path.stat().st_size == 0:
        tmp_path.unlink(missing_ok=True)
        raise RuntimeError(f"Downloaded model is empty: {destination.name}")

    tmp_path.replace(destination)


def get_sessions() -> tuple[ort.InferenceSession, ort.InferenceSession]:
    global _leaf_session, _disease_session

    with _session_lock:
        if _leaf_session is None:
            download_model(LEAF_MODEL_URL, LEAF_MODEL_FILE)
            _leaf_session = ort.InferenceSession(
                str(LEAF_MODEL_FILE), providers=["CPUExecutionProvider"]
            )

        if _disease_session is None:
            download_model(DISEASE_MODEL_URL, DISEASE_MODEL_FILE)
            _disease_session = ort.InferenceSession(
                str(DISEASE_MODEL_FILE), providers=["CPUExecutionProvider"]
            )

    return _leaf_session, _disease_session


def input_spec(session: ort.InferenceSession) -> tuple[str, int, bool]:
    model_input = session.get_inputs()[0]
    shape = model_input.shape
    input_size = 224
    is_nchw = False

    if len(shape) == 4:
        d1, d2, d3 = shape[1], shape[2], shape[3]
        if d3 == 3 and isinstance(d1, int) and isinstance(d2, int) and d1 == d2:
            input_size = d1
            is_nchw = False
        elif d1 == 3 and isinstance(d2, int) and isinstance(d3, int) and d2 == d3:
            input_size = d2
            is_nchw = True

    return model_input.name, input_size, is_nchw


def preprocess(image: Image.Image, input_size: int, is_nchw: bool) -> np.ndarray:
    resized = image.convert("RGB").resize((input_size, input_size))
    array = np.asarray(resized, dtype=np.float32) / 255.0
    if is_nchw:
        array = np.transpose(array, (2, 0, 1))
    return np.expand_dims(array, axis=0).astype(np.float32)


def run_model(session: ort.InferenceSession, image: Image.Image) -> np.ndarray:
    input_name, input_size, is_nchw = input_spec(session)
    tensor = preprocess(image, input_size, is_nchw)
    output = session.run(None, {input_name: tensor})[0]
    return np.asarray(output, dtype=np.float32).reshape(-1)


def confidence_percent(scores: np.ndarray, index: int) -> float:
    if scores.size == 0 or index < 0 or index >= scores.size:
        return 0.0

    total = float(scores.sum())
    looks_like_probabilities = (
        float(scores.min()) >= 0.0
        and float(scores.max()) <= 1.0
        and abs(total - 1.0) <= 0.05
    )
    if looks_like_probabilities:
        confidence = float(scores[index])
    else:
        shifted = scores - np.max(scores)
        exp_scores = np.exp(shifted)
        confidence = float(exp_scores[index] / np.sum(exp_scores))

    return round(max(0.0, min(confidence * 100.0, 100.0)), 2)


def display_label(label: str) -> str:
    return label.replace("_", " ")


@app.get("/")
def root() -> dict[str, str]:
    return {
        "name": "Plant Guard AI Model API",
        "status": "ok",
        "predict": "POST /predict with form-data field 'file'",
    }


@app.get("/health")
def health() -> dict[str, Any]:
    return {"status": "ok", "models_loaded": _leaf_session is not None and _disease_session is not None}


@app.post("/predict")
async def predict(file: UploadFile = File(...)) -> dict[str, Any]:
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="Upload an image file.")

    try:
        image = Image.open(file.file)
        leaf_session, disease_session = get_sessions()

        leaf_scores = run_model(leaf_session, image)
        non_leaf_probability = float(leaf_scores[0]) if leaf_scores.size else 0.0
        if non_leaf_probability > NO_LEAF_THRESHOLD:
            return {
                "leaf_detected": False,
                "result": "No leaf detected",
                "confidence": 0.0,
                "non_leaf_probability": round(non_leaf_probability, 4),
                "treatment": "Please upload a clear leaf image.",
            }

        scores = run_model(disease_session, image)
        if scores.size == 0:
            raise HTTPException(status_code=500, detail="Disease model returned empty output.")

        index = int(np.argmax(scores))
        label = LABELS[index] if index < len(LABELS) else "Unknown"
        confidence = confidence_percent(scores, index)

        return {
            "leaf_detected": True,
            "label": label,
            "disease": display_label(label),
            "confidence": confidence,
            "treatment": DISEASE_TREATMENTS.get(label, "No treatment information available for this disease."),
            "class_index": index,
        }
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Prediction failed: {exc}") from exc
