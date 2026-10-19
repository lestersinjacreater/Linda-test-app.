# Build from the repo root:  docker build -f fraudintel/Dockerfile .
FROM python:3.11-slim
WORKDIR /app
COPY fraudintel/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY fraudintel/src ./src
COPY fraudintel/data ./data
COPY shared/verified_callers.json ./shared/verified_callers.json
ENV FRAUDINTEL_SEED_PATH=/app/data/seed.json
ENV VERIFIED_CALLERS_PATH=/app/shared/verified_callers.json
CMD ["uvicorn", "--factory", "src.main:create_app", "--host", "0.0.0.0", "--port", "8002"]
