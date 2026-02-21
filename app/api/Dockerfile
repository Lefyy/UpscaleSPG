# syntax=docker/dockerfile:1

FROM python:3.11-slim
WORKDIR /opt/upscale

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV PIP_NO_CACHE_DIR=1

COPY app/api/requirements.txt /tmp/requirements.txt
RUN pip install --no-cache-dir --extra-index-url https://download.pytorch.org/whl/cpu -r /tmp/requirements.txt

COPY app/api ./app/api
COPY app/scripts/*.py ./app/scripts/

EXPOSE 8000
CMD ["uvicorn", "app.api.upscale_endpoint:app", "--host", "0.0.0.0", "--port", "8000"]
