#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ocr_root="${repo_root}/.local/ocr"
venv_dir="${ocr_root}/venv"
model_dir="${ocr_root}/models"
python_executable="${OCR_PYTHON_EXECUTABLE:-${venv_dir}/bin/python}"
python_bin="${OCR_BOOTSTRAP_PYTHON:-python3.11}"

if ! command -v "${python_bin}" >/dev/null 2>&1; then
  printf 'Missing %s. PaddleOCR local mode requires Python 3.11.\n' "${python_bin}" >&2
  printf 'Install Python 3.11, or set OCR_BOOTSTRAP_PYTHON to its executable.\n' >&2
  exit 1
fi

mkdir -p "${ocr_root}" "${model_dir}"
if [[ ! -x "${venv_dir}/bin/python" ]]; then
  "${python_bin}" -m venv "${venv_dir}"
fi

"${venv_dir}/bin/python" -m pip install \
  --disable-pip-version-check \
  --upgrade pip \
  --index-url "${PYPI_INDEX_URL:-https://mirrors.aliyun.com/pypi/simple/}"
if [[ "$(uname -s)" == "Darwin" ]]; then
  # The repository requirements are for the Linux Docker image. macOS needs
  # Paddle's official CPU wheel index; the rest of PaddleOCR can use PyPI.
  "${venv_dir}/bin/python" -m pip install \
    --disable-pip-version-check \
    paddlepaddle==3.0.0 \
    --index-url "${PADDLE_PIP_INDEX_URL:-https://www.paddlepaddle.org.cn/packages/stable/cpu/}"
  "${venv_dir}/bin/python" -m pip install \
    --disable-pip-version-check \
    paddleocr==3.2.0 \
    --index-url "${PYPI_INDEX_URL:-https://mirrors.aliyun.com/pypi/simple/}"
else
  "${venv_dir}/bin/python" -m pip install \
    --disable-pip-version-check \
    --index-url "${PYPI_INDEX_URL:-https://mirrors.aliyun.com/pypi/simple/}" \
    -r "${repo_root}/backend/unispeaking-server/docker/ocr/requirements.txt"
fi

export PADDLE_PDX_CACHE_HOME="${model_dir}"
export PADDLE_PDX_MODEL_SOURCE="${PADDLE_PDX_MODEL_SOURCE:-bos}"
"${venv_dir}/bin/python" \
  "${repo_root}/backend/unispeaking-server/src/main/resources/ocr/paddle_ocr_runner.py" \
  --download-models \
  --device cpu \
  --text-detection-model-name PP-OCRv5_mobile_det \
  --text-recognition-model-name PP-OCRv5_mobile_rec \
  --disable-doc-orientation \
  --disable-doc-unwarping \
  --disable-textline-orientation

cat <<EOF

Local PaddleOCR is ready.
Start the backend with:
  OCR_ENABLED=true \\
  OCR_PYTHON_EXECUTABLE=${python_executable} \\
  OCR_RUNNER_PATH=${repo_root}/backend/unispeaking-server/src/main/resources/ocr/paddle_ocr_runner.py \\
  OCR_MODEL_DIRECTORY=${model_dir} \\
  ./mvnw --settings docker/maven/settings.xml spring-boot:run
EOF
