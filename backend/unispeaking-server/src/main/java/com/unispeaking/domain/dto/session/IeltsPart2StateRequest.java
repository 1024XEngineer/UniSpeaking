package com.unispeaking.domain.dto.session;

import com.unispeaking.domain.vo.scene.IeltsPart2Event;
import jakarta.validation.constraints.NotNull;

public record IeltsPart2StateRequest(@NotNull IeltsPart2Event event) {
}
