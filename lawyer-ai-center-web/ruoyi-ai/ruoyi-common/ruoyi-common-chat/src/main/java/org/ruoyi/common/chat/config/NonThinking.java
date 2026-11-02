package org.ruoyi.common.chat.config;

import java.util.Map;

/** Application policy: disable model reasoning at the upstream request, not just its display. */
public final class NonThinking {
    private NonThinking() {}
    public static Map<String, Object> parameters() {
        return Map.of("chat_template_kwargs", Map.of("enable_thinking", false));
    }
}
