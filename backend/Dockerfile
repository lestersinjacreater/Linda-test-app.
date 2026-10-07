# Build from the repo root so shared/verified_senders.json is included:
#   docker build -f backend/Dockerfile .
FROM python:3.11-slim
WORKDIR /app
COPY backend/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY backend/src ./src
COPY shared ./shared
ENV VERIFIED_SENDERS_PATH=/app/shared/verified_senders.json
CMD ["uvicorn", "--factory", "src.main:create_app", "--host", "0.0.0.0", "--port", "8001"]
