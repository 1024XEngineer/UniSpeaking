#!/usr/bin/env bash

set -euo pipefail

base_url="${OCR_BASE_URL:-http://127.0.0.1:8080}"
image_path="${1:-}"
access_token="${OCR_ACCESS_TOKEN:-}"

if [[ -z "${access_token}" ]]; then
  printf 'Set OCR_ACCESS_TOKEN to a logged-in local JWT.\n' >&2
  exit 2
fi

availability="$(curl --fail-with-body --silent --show-error \
  -H "Authorization: Bearer ${access_token}" \
  "${base_url}/api/interview-scenes/ocr/availability")"
printf 'OCR availability: %s\n' "${availability}"

if [[ -z "${image_path}" ]]; then
  exit 0
fi
if [[ ! -f "${image_path}" ]]; then
  printf 'Image does not exist: %s\n' "${image_path}" >&2
  exit 2
fi

curl --fail-with-body --silent --show-error \
  -H "Authorization: Bearer ${access_token}" \
  -F "jobDescriptionImage=@${image_path}" \
  "${base_url}/api/interview-scenes/prepare-materials"
printf '\n'
