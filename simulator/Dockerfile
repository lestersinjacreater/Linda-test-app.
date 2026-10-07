# Build from the repo root:  docker build -f simulator/Dockerfile .
FROM python:3.11-slim
WORKDIR /app
COPY simulator/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY simulator/src ./src
COPY simulator/models ./models
COPY shared/verified_senders.json ./shared/verified_senders.json
CMD ["uvicorn", "--factory", "src.main:create_app", "--host", "0.0.0.0", "--port", "8000"]
