# ═══════════════════════════════════════════════════════
# FILE: ml_service/Dockerfile
# FastAPI Python 3.11 — ML Microservice
# ═══════════════════════════════════════════════════════
FROM python:3.11-slim

WORKDIR /app

# Install system deps
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl build-essential \
    && rm -rf /var/lib/apt/lists/*

# Install Python deps
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy app code
COPY . .

# Create models and data dirs
RUN mkdir -p models data

# Train models with synthetic data at container startup
# (In production: mount pre-trained models via Docker volume)
RUN python train_models.py --synthetic --output models/ 2>/dev/null || true

EXPOSE 5000

HEALTHCHECK --interval=15s --timeout=10s --retries=3 \
  CMD curl -f http://localhost:5000/health || exit 1

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "10000"]
