"""HTTP endpoint for image upscaling.

Run:
    uvicorn app.api.upscale_endpoint:app --host 0.0.0.0 --port 8000
"""

from enum import Enum
import os
from pathlib import Path
import tempfile

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import Response

from app.scripts.upscale_image import upscale_image

app = FastAPI(title="Upscale API", version="1.1.0")


class ModelName(str, Enum):
    bilinear = "bilinear"
    bicubic = "bicubic"
    espcn = "espcn"
    edsr = "edsr"
    srgan = "srgan"


_WEIGHTS_DIR = Path(os.getenv("UPSCALE_WEIGHTS_DIR", "app/scripts/weights"))
_MODEL_WEIGHTS = {
    "espcn": {2: "ESPCN_2x.pth.tar", 3: "ESPCN_3x.pth.tar", 4: "ESPCN_4x.pth.tar"},
    "edsr": {2: "EDSR_2x.pt", 3: "EDSR_3x.pt", 4: "EDSR_4x.pt"},
    "srgan": {2: "SRGAN_2x.pth.tar", 4: "SRGAN_4x.pth"},
}


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


def _resolve_model_path(model_name: str, scale: int) -> str:
    if model_name in ("bilinear", "bicubic"):
        return "none"

    model_paths = _MODEL_WEIGHTS.get(model_name)
    if model_paths is None or scale not in model_paths:
        raise HTTPException(status_code=400, detail=f"Unsupported model/scale combination: {model_name} x{scale}")

    model_path = _WEIGHTS_DIR / model_paths[scale]
    if not model_path.exists():
        raise HTTPException(status_code=500, detail=f"Model weights not found: {model_path}")

    return str(model_path)


@app.post("/upscale")
async def upscale(
    file: UploadFile = File(...),
    model_name: ModelName = Form(...),
    scale: int = Form(...),
) -> Response:
    if scale < 2 or scale > 4:
        raise HTTPException(status_code=400, detail="Scale must be between 2 and 4")

    suffix = Path(file.filename or "image.png").suffix or ".png"

    input_path = None
    output_path = None
    try:
        model_path = _resolve_model_path(model_name.value, scale)

        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as input_tmp:
            input_path = input_tmp.name
            input_tmp.write(await file.read())

        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as output_tmp:
            output_path = output_tmp.name

        exit_code = upscale_image(
            input_path=input_path,
            output_path=output_path,
            model_path=model_path,
            model_name=model_name.value,
            scale=scale,
        )

        if exit_code != 0:
            raise HTTPException(status_code=500, detail="Image upscaling failed")

        output_bytes = Path(output_path).read_bytes()
        media_type = file.content_type or "image/png"
        return Response(content=output_bytes, media_type=media_type)
    finally:
        if input_path and Path(input_path).exists():
            Path(input_path).unlink(missing_ok=True)
        if output_path and Path(output_path).exists():
            Path(output_path).unlink(missing_ok=True)
