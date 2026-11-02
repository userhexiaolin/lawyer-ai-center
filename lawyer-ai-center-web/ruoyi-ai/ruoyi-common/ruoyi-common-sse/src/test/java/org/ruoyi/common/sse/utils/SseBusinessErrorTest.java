package org.ruoyi.common.sse.utils;

import cn.hutool.extra.spring.SpringUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.ruoyi.common.sse.core.SseEmitterManager;
import org.ruoyi.common.sse.dto.SseEventDto;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class SseBusinessErrorTest {
    private SseEmitterManager manager;

    @BeforeEach void initializeSseUtility() {
        manager = mock(SseEmitterManager.class);
        var context = new GenericApplicationContext();
        context.registerBean(SseEmitterManager.class, () -> manager);
        context.refresh();
        var spring = new SpringUtil();
        spring.setApplicationContext(context);
        spring.postProcessBeanFactory(context.getDefaultListableBeanFactory());
        ReflectionTestUtils.setField(SseMessageUtils.class, "MANAGER", manager);
        when(manager.sendLocalEvent(eq("s"), any(SseEventDto.class))).thenReturn(true);
    }

    @Test void arbitraryExceptionTextRemainsRedacted() {
        SseMessageUtils.sendErrorAndComplete("s", "Bearer secret-canary; private-contract-canary");
        assertTerminal("请求处理失败，请稍后重试");
    }

    @Test void fixedBusinessMessageReachesTheClientAsATerminalError() {
        SseMessageUtils.sendBusinessErrorAndComplete("s", SseMessageUtils.BusinessError.DRAFTING_SOURCE);
        assertTerminal("填写字段缺少合同原文依据，请补充资料或重新提取");
    }

    @Test void missingBusinessErrorUsesTheSafeFallback() {
        SseMessageUtils.sendBusinessErrorAndComplete("s", null);
        assertTerminal("请求处理失败，请稍后重试");
    }

    private void assertTerminal(String expected) {
        var event = ArgumentCaptor.forClass(SseEventDto.class);
        verify(manager).sendLocalEvent(eq("s"), event.capture());
        assertEquals("error", event.getValue().getEvent());
        assertEquals(expected, event.getValue().getError());
        assertEquals(Boolean.TRUE, event.getValue().getDone());
        verify(manager, never()).publishMessage(any());
    }
}
