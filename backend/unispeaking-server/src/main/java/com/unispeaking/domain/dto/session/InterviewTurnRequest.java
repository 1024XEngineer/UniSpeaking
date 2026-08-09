package com.unispeaking.domain.dto.session;

/**
 * {@code submitTurn} 的 multipart 请求体：transcript（必填）+ audio（可空）。
 *
 * <p>音频落盘/attach 由第五刀（RecordingStore 泛化）补齐，本刀先接收参数。</p>
 */
public record InterviewTurnRequest(String transcript, byte[] audio) {
}
