package org.ruoyi.service.chat.impl.provider;

import java.util.Map;
import org.ruoyi.common.chat.domain.dto.request.ChatRequest;

/** Ark's explicit upstream thinking switch is independent of SDK returnThinking. */
final class SeedThinkingParameters {
    private SeedThinkingParameters() {}
    static Map<String, Object> forRequest(String model, ChatRequest request) {
        boolean seed21 = model != null && (model.endsWith("doubao-seed-2.1-pro-260628") || model.endsWith("doubao-seed-2-1-pro-260628"));
        return seed21
            ? Map.of("chat_template_kwargs", Map.of("enable_thinking", false), "thinking", Map.of("type", "disabled"))
            : org.ruoyi.common.chat.config.NonThinking.parameters();
    }
}
