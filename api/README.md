# Plant Guard AI Model API

FastAPI backend for deploying the plant leaf ONNX models on Render.

## Endpoints

- `GET /health` - checks whether the service is alive.
- `POST /predict` - accepts an image in form-data field `file`.

Example:

```bash
curl -X POST "https://your-render-service.onrender.com/predict" \
  -F "file=@leaf.jpg"
```

Response:

```json
{
  "leaf_detected": true,
  "label": "Tomato_Early_blight",
  "disease": "Tomato Early blight",
  "confidence": 92.4,
  "treatment": "Remove lower infected leaves, stake plants for better airflow, rotate crops, and use fungicide when necessary.",
  "class_index": 6
}
```

## Render Deployment

1. Push this repository to GitHub.
2. In Render, create a new Web Service.
3. Use the root `render.yaml` blueprint, or create a Web Service manually.
4. For manual setup, set the Root Directory to `api` and use:
   - Build command: `pip install -r requirements.txt`
   - Start command: `uvicorn main:app --host 0.0.0.0 --port $PORT`

The service downloads the two ONNX files from Hugging Face on first prediction and caches them in `api/models`.

Optional environment variables:

- `LEAF_MODEL_URL`
- `DISEASE_MODEL_URL`
- `NO_LEAF_THRESHOLD`
- `CORS_ORIGINS`
