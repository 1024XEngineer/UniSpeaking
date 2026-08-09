package com.unispeaking.domain.dto.ocr;

/**
 * OCR 能力可用性探测响应（前端据此禁用/启用 JD 图片上传）。
 */
public record OcrAvailabilityResponse(boolean available) {
}
