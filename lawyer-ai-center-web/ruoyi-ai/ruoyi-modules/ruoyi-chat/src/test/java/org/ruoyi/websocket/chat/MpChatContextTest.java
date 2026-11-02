package org.ruoyi.websocket.chat;
import dev.langchain4j.data.message.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class MpChatContextTest {
    @Test void legalPromptIsSystemMessageAndHistoryIsPreservedExactlyOnce() {
        var result = MpChatWebSocketHandler.buildModelMessages("审查配置", List.of(UserMessage.from("合同金额7319元"), AiMessage.from("已记录")), "合同金额是多少");
        assertEquals(4, result.size()); assertInstanceOf(SystemMessage.class, result.get(0));
        assertEquals("合同金额7319元", ((UserMessage)result.get(1)).singleText());
        assertEquals("合同金额是多少", ((UserMessage)result.get(3)).singleText());
    }
    @Test void historyIsBoundedAndCannotOverrideConfiguredSystemPrompt() {
        List<ChatMessage> history = new ArrayList<>(); history.add(SystemMessage.from("旧配置"));
        for (int i=0;i<40;i++) history.add(i%2==0 ? UserMessage.from("问题"+i) : AiMessage.from("回复"+i));
        var result = MpChatWebSocketHandler.buildModelMessages("新配置", history, "本轮问题");
        assertEquals(22,result.size()); assertEquals(1,result.stream().filter(SystemMessage.class::isInstance).count());
    }
}
